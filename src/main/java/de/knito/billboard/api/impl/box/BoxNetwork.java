package de.knito.billboard.api.impl.box;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import de.knito.billboard.api.impl.network.ClientData;
import de.knito.billboard.api.impl.network.Network;
import de.knito.billboard.api.impl.network.RenameLog;

/**
 * A Lua Box's view of its network, walked again at most once per tick and only when the program asks. That costs
 * a little time when nothing changed, but no event can be missed: chunks loading, blocks set by commands, all count.
 */
final class BoxNetwork {
	private final LuaBoxBlockEntity box;
	private Network network;
	private long scannedTick = Long.MIN_VALUE;
	private List<String> signature;
	private int version;
	private Set<BlockPos> known = Set.of();
	private long renamesSeen = RenameLog.last();

	BoxNetwork(LuaBoxBlockEntity box) {
		this.box = box;
	}

	Network get() {
		if (!(box.getLevel() instanceof ServerLevel level)) {
			throw new IllegalStateException("Lua Box without a server level");
		}
		long tick = level.getGameTime();
		if (network == null || tick != scannedTick) {
			scannedTick = tick;
			network = Network.scan(level, box.getBlockPos(), box.owner(), known);
			known = Set.copyOf(network.positions());
			List<String> newSignature = network.signature();
			if (!newSignature.equals(signature)) {
				signature = newSignature;
				version++;
			}
			for (RenameLog.Entry entry : RenameLog.since(renamesSeen, level.dimension(), known)) {
				box.print(entry.message(), true);
			}
			renamesSeen = RenameLog.last();
		}
		return network;
	}

	/** Gives a client a new name: null if done, else why not. */
	@Nullable String rename(Network.Member member, String name) {
		if (!ClientData.isValidName(name)) {
			return "invalid name (" + ClientData.NAME_RULE + ")";
		}
		for (Network.Member other : get().members()) {
			if (!other.pos().equals(member.pos()) && Network.lower(other.name()).equals(Network.lower(name))) {
				return "the name " + other.name() + " is taken in this network";
			}
		}
		member.entity().setAttached(ClientData.NAME, name);
		member.entity().setChanged();
		invalidate();
		return null;
	}

	/** A name changed: walk again on the next call, even in the same tick. */
	void invalidate() {
		network = null;
	}

	/** Grows on every change of the network. */
	int version() {
		get();
		return version;
	}
}
