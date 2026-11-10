package de.knito.billboard.api.impl.port;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;

import de.knito.billboard.api.BillboardApi;
import de.knito.billboard.api.LuaArgs;
import de.knito.billboard.api.LuaCaller;
import de.knito.billboard.api.LuaClient;
import de.knito.billboard.api.LuaException;
import de.knito.billboard.api.LuaMethods;
import de.knito.billboard.api.impl.ModBlockEntities;

/**
 * The Lua side of an inventory port. Whatever offers item storage through Fabric's Transfer API next to one of the
 * port's six sides can be read and emptied into another: vanilla containers, and modded ones such as Colossal Chests
 * (through an interface) or Extended Drawers (drawers and access points) that register themselves there.
 *
 * <p>Reading needs no claim. Moving needs the claim of every port it goes through. A move is one transaction: it
 * moves what fits, or nothing at all if something goes wrong, and gives the number of items moved.
 */
public final class InventoryPortClient implements LuaClient {
	/** Items a move takes when the program names no limit. */
	public static final int DEFAULT_LIMIT = 64;

	private final InventoryPortBlockEntity port;

	private InventoryPortClient(InventoryPortBlockEntity port) {
		this.port = port;
	}

	public static void initialize() {
		BillboardApi.CLIENTS.registerForBlockEntity((port, side) -> new InventoryPortClient(port), ModBlockEntities.INVENTORY_PORT);
	}

	@Override
	public String type() {
		return "billboard_api:inventory_port";
	}

	@Override
	public void methods(LuaMethods methods) {
		methods.add("claim", args -> {
					LuaCaller caller = args.caller();
					if (caller == null) {
						throw new LuaException("no program to claim the port for");
					}
					LuaCaller other = port.claim(caller);
					return other == null ? (Object) true : new Object[]{false, other.describe()};
				})
				.add("release", args -> port.release(args.caller()))
				.add("isClaimed", args -> {
					LuaCaller holder = port.holder();
					return holder == null ? (Object) false : new Object[]{true, holder.describe()};
				})
				.add("hasInventory", args -> port.storage(side(args, 1)) != null)
				.add("size", args -> size(storage(args)))
				.add("list", args -> list(storage(args)))
				.add("getItem", args -> item(storage(args), args.integer(2)))
				.add("move", args -> move(args));
	}

	// ---- reading ----

	private Storage<ItemVariant> storage(LuaArgs args) throws LuaException {
		Direction side = side(args, 1);
		Storage<ItemVariant> storage = port.storage(side);
		if (storage == null) {
			throw new LuaException("no inventory on the " + side.getSerializedName() + " side");
		}
		return storage;
	}

	private static int size(Storage<ItemVariant> storage) {
		int slots = 0;
		for (StorageView<ItemVariant> ignored : storage) {
			slots++;
		}
		return slots;
	}

	/** The slots that hold something, in the container's own order: {@code { {slot=1, name="minecraft:stone", count=12}, ... }}. */
	private static List<Map<String, Object>> list(Storage<ItemVariant> storage) {
		List<Map<String, Object>> result = new ArrayList<>();
		int slot = 0;
		for (StorageView<ItemVariant> view : storage) {
			slot++;
			Map<String, Object> entry = describe(view, slot);
			if (entry != null) {
				result.add(entry);
			}
		}
		return result;
	}

	private static @Nullable Map<String, Object> item(Storage<ItemVariant> storage, int wanted) throws LuaException {
		if (wanted < 1) {
			throw new LuaException("bad argument #2 (slot must be 1 or more, got " + wanted + ")");
		}
		int slot = 0;
		for (StorageView<ItemVariant> view : storage) {
			if (++slot == wanted) {
				return describe(view, slot);
			}
		}
		throw new LuaException("bad argument #2 (slot " + wanted + " does not exist, the inventory has " + size(storage) + ")");
	}

	private static @Nullable Map<String, Object> describe(StorageView<ItemVariant> view, int slot) {
		if (view.isResourceBlank() || view.getAmount() <= 0) {
			return null;
		}
		Map<String, Object> entry = new LinkedHashMap<>();
		entry.put("slot", (long) slot);
		entry.put("name", BuiltInRegistries.ITEM.getKey(view.getResource().getItem()).toString());
		entry.put("count", view.getAmount());
		return entry;
	}

	// ---- moving ----

	/**
	 * {@code move(from, to, filter, limit)} moves within this port; {@code move(from, port, to, filter, limit)}
	 * moves to another inventory port of the network, given by its name. See the wiki for the filter.
	 */
	private Object move(LuaArgs args) throws LuaException {
		LuaCaller caller = args.caller();
		port.requireClaim(caller);
		Direction from = side(args, 1);
		// A second string that is not a side name is the name of another port.
		InventoryPortBlockEntity targetPort = port;
		int next = 2;
		if (args.get(2) instanceof String name && !isSide(name)) {
			targetPort = otherPort(caller, name);
			next = 3;
		}
		Direction to = side(args, next);
		Predicate<ItemVariant> filter = filter(args, next + 1);
		long limit = args.isNil(next + 2) ? DEFAULT_LIMIT : args.integer(next + 2);
		if (limit < 1) {
			throw new LuaException("bad argument #" + (next + 2) + " (limit must be 1 or more, got " + limit + ")");
		}
		if (targetPort != port) {
			targetPort.requireClaim(caller);
		}
		if (targetPort.neighbour(to).equals(port.neighbour(from))) {
			throw new LuaException("that is the same inventory: it cannot be moved into itself");
		}
		Storage<ItemVariant> source = port.storage(from);
		if (source == null) {
			throw new LuaException("no inventory on the " + from.getSerializedName() + " side to take from");
		}
		Storage<ItemVariant> target = targetPort.storage(to);
		if (target == null) {
			throw new LuaException("no inventory on the " + to.getSerializedName() + " side to put into");
		}
		try (Transaction transaction = Transaction.openOuter()) {
			long moved = StorageUtil.move(source, target, filter, limit, transaction);
			transaction.commit();
			return moved;
		}
	}

	private InventoryPortBlockEntity otherPort(@Nullable LuaCaller caller, String name) throws LuaException {
		LuaClient client = caller == null ? null : caller.client(name);
		if (client == null) {
			throw new LuaException("bad argument #2 (no machine called \"" + name + "\" in this network)");
		}
		if (client instanceof InventoryPortClient other) {
			return other.port;
		}
		throw new LuaException("bad argument #2 (\"" + name + "\" is not an inventory port)");
	}

	/**
	 * nil: everything. {@code "minecraft:cobblestone"}: that item, whatever its data; a name without a colon is
	 * taken as a vanilla one. {@code "#minecraft:logs"}: any item of that tag.
	 */
	private static Predicate<ItemVariant> filter(LuaArgs args, int index) throws LuaException {
		if (args.isNil(index)) {
			return variant -> true;
		}
		String text = args.string(index);
		boolean tag = text.startsWith("#");
		Identifier id = Identifier.tryParse(tag ? text.substring(1) : text);
		if (id == null) {
			throw new LuaException("bad argument #" + index + " (\"" + text + "\" is not an item name)");
		}
		if (tag) {
			TagKey<Item> key = TagKey.create(net.minecraft.core.registries.Registries.ITEM, id);
			return variant -> variant.toStack().is(key);
		}
		Item item = BuiltInRegistries.ITEM.getOptional(id).orElseThrow(() -> new LuaException("bad argument #" + index + " (no item called \"" + text + "\")"));
		return variant -> variant.getItem() == item;
	}

	private static boolean isSide(String name) {
		for (Direction direction : Direction.values()) {
			if (direction.getSerializedName().equals(name)) {
				return true;
			}
		}
		return false;
	}

	private static Direction side(LuaArgs args, int index) throws LuaException {
		String name = args.string(index);
		for (Direction direction : Direction.values()) {
			if (direction.getSerializedName().equals(name)) {
				return direction;
			}
		}
		throw new LuaException("bad argument #" + index + " (side expected: north, south, east, west, up or down, got \"" + name + "\")");
	}
}
