package de.knito.billboard.api.impl.network;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import de.knito.billboard.api.BillboardApi;
import de.knito.billboard.api.LuaClient;
import de.knito.billboard.api.impl.ApiConfig;
import de.knito.billboard.api.impl.BillboardApiMod;
import de.knito.billboard.api.impl.box.LuaBoxBlock;

/**
 * What one Lua Box reaches right now: a walk from the box through every box and pipe that touches it, and every
 * Lua client that touches one of them. Nothing travels through a network; it only decides who may call whom.
 *
 * <p>The walk never loads a chunk: clients in unloaded chunks are simply not there. Names are made unique here,
 * per network, case-insensitive: a client without a name gets the lowest free {@code <type>_<n>}, and when two
 * clients share a name, the one this box did not know before is renamed and the rename is logged for every box.
 */
public final class Network {
	public record Member(BlockPos pos, Direction side, LuaClient client, BlockEntity entity, String type, String name, boolean usable) {
	}

	/** Client types already reported in the log for a bad type, so the log is not flooded. */
	private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

	private final List<Member> members;
	private final Map<BlockPos, Member> byPos;
	private final @Nullable String problem;

	private Network(List<Member> members, @Nullable String problem) {
		this.members = Collections.unmodifiableList(members);
		Map<BlockPos, Member> map = new HashMap<>();
		for (Member member : members) {
			map.put(member.pos(), member);
		}
		this.byPos = map;
		this.problem = problem;
	}

	/** All clients, the ones this box may not drive included, in walk order. */
	public List<Member> members() {
		return members;
	}

	public @Nullable Member at(BlockPos pos) {
		return byPos.get(pos);
	}

	public Set<BlockPos> positions() {
		return byPos.keySet();
	}

	/** Why the network can't be used, such as being too large; null if it can. */
	public @Nullable String problem() {
		return problem;
	}

	/** Changes whenever a client joins, leaves, or gets another name. */
	public List<String> signature() {
		List<String> list = new ArrayList<>(members.size() + 1);
		list.add(String.valueOf(problem));
		for (Member member : members) {
			list.add(member.pos().asLong() + " " + member.type() + " " + member.name() + " " + member.usable());
		}
		return list;
	}

	/**
	 * Walks the network of the box at {@code start}.
	 *
	 * @param boxOwner the box's owner; machines of other owners are found but not usable, unless the owner check
	 *                 is off. A machine with no recorded owner is claimed by the first owned box that finds it.
	 * @param known    clients this box saw in its last walk: on a name clash they keep their name
	 */
	public static Network scan(ServerLevel level, BlockPos start, @Nullable UUID boxOwner, Set<BlockPos> known) {
		Deque<BlockPos> queue = new ArrayDeque<>();
		Set<BlockPos> visited = new HashSet<>();
		Map<BlockPos, Member> found = new LinkedHashMap<>();
		queue.add(start);
		visited.add(start);
		int boxes = 1;
		String problem = null;
		walk:
		while (!queue.isEmpty()) {
			BlockPos pos = queue.removeFirst();
			for (Direction direction : Direction.values()) {
				BlockPos next = pos.relative(direction);
				if (visited.contains(next) || found.containsKey(next) || !level.isLoaded(next)) {
					continue;
				}
				BlockState state = level.getBlockState(next);
				if (state.getBlock() instanceof LuaBoxBlock) {
					visited.add(next);
					if (++boxes > ApiConfig.networkMaxBoxes()) {
						problem = "network too large (more than " + ApiConfig.networkMaxBoxes() + " Lua Boxes)";
						break walk;
					}
					queue.addLast(next);
					continue;
				}
				Member member = client(level, next, state, direction.getOpposite());
				if (member != null) {
					found.put(next, member);
					if (found.size() > ApiConfig.networkMaxClients()) {
						problem = "network too large (more than " + ApiConfig.networkMaxClients() + " clients)";
						break walk;
					}
				}
			}
		}
		List<Member> members = name(level, new ArrayList<>(found.values()), known);
		return new Network(own(members, boxOwner), problem);
	}

	private static @Nullable Member client(ServerLevel level, BlockPos pos, BlockState state, Direction side) {
		BlockEntity entity = level.getBlockEntity(pos);
		if (entity == null) {
			return null;
		}
		LuaClient client;
		try {
			client = BillboardApi.CLIENTS.find(level, pos, state, entity, side);
		} catch (RuntimeException e) {
			warnOnce(entity.getType().toString(), "Lua client lookup failed for {} at {}", entity.getType(), pos, e);
			return null;
		}
		if (client == null) {
			return null;
		}
		String type = client.type();
		if (type == null || type.isBlank()) {
			warnOnce(client.getClass().getName(), "Lua client {} has no type and is left out", client.getClass().getName());
			return null;
		}
		return new Member(pos.immutable(), side, client, entity, type, entity.getAttached(ClientData.NAME), true);
	}

	/** Gives nameless clients their default name and renames clashes. Known clients go first, so they keep theirs. */
	private static List<Member> name(ServerLevel level, List<Member> members, Set<BlockPos> known) {
		List<Member> ordered = new ArrayList<>(members.size());
		members.stream().filter(m -> known.contains(m.pos())).forEach(ordered::add);
		members.stream().filter(m -> !known.contains(m.pos())).forEach(ordered::add);
		Set<String> taken = new HashSet<>();
		List<Member> later = new ArrayList<>();
		for (Member member : ordered) {
			if (member.name() != null && ClientData.isValidName(member.name()) && taken.add(lower(member.name()))) {
				continue;
			}
			later.add(member);
		}
		Map<BlockPos, Member> renamed = new HashMap<>();
		for (Member member : later) {
			String name = free(ClientData.shortType(member.type()), taken);
			taken.add(lower(name));
			member.entity().setAttached(ClientData.NAME, name);
			member.entity().setChanged();
			if (member.name() != null) {
				RenameLog.add(level.dimension(), member.pos(), member.name(), name);
			}
			renamed.put(member.pos(), new Member(member.pos(), member.side(), member.client(), member.entity(), member.type(), name, true));
		}
		if (renamed.isEmpty()) {
			return members;
		}
		return members.stream().map(m -> renamed.getOrDefault(m.pos(), m)).toList();
	}

	private static List<Member> own(List<Member> members, @Nullable UUID boxOwner) {
		if (!ApiConfig.networkOwnerCheck()) {
			return members;
		}
		List<Member> result = new ArrayList<>(members.size());
		for (Member member : members) {
			UUID owner = member.entity().getAttached(ClientData.OWNER);
			if (owner == null && boxOwner != null) {
				member.entity().setAttached(ClientData.OWNER, boxOwner);
				member.entity().setChanged();
				owner = boxOwner;
			}
			boolean usable = owner == null || owner.equals(boxOwner);
			result.add(usable ? member : new Member(member.pos(), member.side(), member.client(), member.entity(), member.type(), member.name(), false));
		}
		return result;
	}

	/** The lowest free {@code base_n}, n from 1. */
	static String free(String base, Set<String> taken) {
		for (int n = 1; ; n++) {
			String name = base + "_" + n;
			if (!taken.contains(lower(name))) {
				return name;
			}
		}
	}

	public static String lower(String name) {
		return name.toLowerCase(Locale.ROOT);
	}

	private static void warnOnce(String key, String message, Object... args) {
		if (WARNED.add(key)) {
			BillboardApiMod.LOGGER.error(message, args);
		}
	}
}
