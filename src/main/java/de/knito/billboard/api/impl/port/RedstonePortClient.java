package de.knito.billboard.api.impl.port;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.core.registries.BuiltInRegistries;

import de.knito.billboard.api.BillboardApi;
import de.knito.billboard.api.LuaArgs;
import de.knito.billboard.api.LuaClient;
import de.knito.billboard.api.LuaException;
import de.knito.billboard.api.LuaMethods;
import de.knito.billboard.api.impl.ModBlockEntities;

/**
 * The Lua side of a redstone port. A program claims the port with {@code claim()} before it sets a signal, and
 * lets go with {@code release()} or by ending; reading needs no claim. Sides are named {@code "north"}, {@code "south"}, {@code "east"},
 * {@code "west"}, {@code "up"} and {@code "down"}, as the world sees them.
 */
public final class RedstonePortClient implements LuaClient {
	private final RedstonePortBlockEntity port;

	private RedstonePortClient(RedstonePortBlockEntity port) {
		this.port = port;
	}

	public static void initialize() {
		BillboardApi.CLIENTS.registerForBlockEntity((port, side) -> new RedstonePortClient(port), ModBlockEntities.REDSTONE_PORT);
	}

	@Override
	public String type() {
		return "billboard_api:redstone_port";
	}

	@Override
	public void methods(LuaMethods methods) {
		methods.add("claim", args -> {
					port.claim(args.caller());
					return null;
				})
				.add("release", args -> {
					port.release(args.caller());
					return null;
				})
				.add("setOutput", args -> {
					Direction side = side(args);
					port.setOutput(side, power(args), args.caller());
					return null;
				})
				.add("getOutput", args -> port.output(side(args)))
				.add("getInput", args -> input(side(args)))
				.add("getBlock", args -> block(side(args)));
	}

	/** 0 to 15, or a boolean: true is 15. */
	private static int power(LuaArgs args) throws LuaException {
		if (args.get(2) instanceof Boolean on) {
			return on ? RedstonePortBlockEntity.MAX_POWER : 0;
		}
		int level = args.integer(2);
		if (level < 0 || level > RedstonePortBlockEntity.MAX_POWER) {
			throw new LuaException("bad argument #2 (signal strength must be 0 to " + RedstonePortBlockEntity.MAX_POWER + ", got " + level + ")");
		}
		return level;
	}

	private static Direction side(LuaArgs args) throws LuaException {
		String name = args.string(1);
		for (Direction direction : Direction.values()) {
			if (direction.getSerializedName().equals(name)) {
				return direction;
			}
		}
		throw new LuaException("bad argument #1 (side expected: north, south, east, west, up or down, got \"" + name + "\")");
	}

	/**
	 * What arrives from that side. A side the program drives reads 0: whatever comes back on it, through redstone
	 * dust for one, is the port's own signal.
	 */
	private int input(Direction side) {
		Level level = port.getLevel();
		if (level == null || port.output(side) > 0) {
			return 0;
		}
		return level.getSignal(port.getBlockPos().relative(side), side);
	}

	private Object block(Direction side) {
		Level level = port.getLevel();
		BlockState state = level.getBlockState(port.getBlockPos().relative(side));
		Map<String, Object> properties = new LinkedHashMap<>();
		for (Property<?> property : state.getProperties()) {
			properties.put(property.getName(), value(state, property));
		}
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("name", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
		result.put("properties", properties);
		return result;
	}

	private static <T extends Comparable<T>> Object value(BlockState state, Property<T> property) {
		T value = state.getValue(property);
		return switch (value) {
			case Boolean b -> b;
			case Integer i -> i.longValue();
			default -> property.getName(value).toLowerCase(Locale.ROOT);
		};
	}
}
