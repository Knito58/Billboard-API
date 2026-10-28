package de.knito.billboard.api.impl.box;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.knito.billboard.api.LuaArgs;
import de.knito.billboard.api.LuaCaller;
import de.knito.billboard.api.lua.cobalt.Constants;
import de.knito.billboard.api.lua.cobalt.LuaError;
import de.knito.billboard.api.lua.cobalt.LuaString;
import de.knito.billboard.api.lua.cobalt.LuaTable;
import de.knito.billboard.api.lua.cobalt.LuaValue;
import de.knito.billboard.api.lua.cobalt.ValueFactory;
import de.knito.billboard.api.lua.cobalt.Varargs;

/**
 * Turns Lua values into the plain Java values of {@link LuaArgs} and back, for calls to Lua clients. Clients never
 * see Cobalt types. Errors carry no position (level 0); the Lua side adds the caller's.
 */
final class LuaValues {
	/** Deeper tables than this are almost always a table that contains itself in a roundabout way. */
	private static final int MAX_DEPTH = 32;

	private LuaValues() {
	}

	/** Arguments from {@code first} on. Top-level strings stay bytes, so binary data comes through unchanged. */
	static LuaArgs args(Varargs args, int first, LuaCaller caller) throws LuaError {
		List<Object> values = new ArrayList<>();
		for (int i = first; i <= args.count(); i++) {
			LuaValue value = args.arg(i);
			values.add(value.typeName().equals("string") ? bytes(value.checkLuaString()) : toJava(value, 0, Collections.newSetFromMap(new IdentityHashMap<>())));
		}
		return new LuaArgs(values, caller);
	}

	private static Object toJava(LuaValue value, int depth, Set<LuaValue> open) throws LuaError {
		switch (value.typeName()) {
			case "nil":
				return null;
			case "boolean":
				return value.toBoolean();
			case "number": {
				double d = value.toDouble();
				if (d == Math.rint(d) && Math.abs(d) <= 0x1p53) {
					return (long) d;
				}
				return d;
			}
			case "string":
				return new String(bytes(value.checkLuaString()), StandardCharsets.UTF_8);
			case "table":
				return table(value.checkTable(), depth, open);
			default:
				throw new LuaError("can't pass a " + value.typeName() + " to a client", 0);
		}
	}

	private static Object table(LuaTable table, int depth, Set<LuaValue> open) throws LuaError {
		if (depth >= MAX_DEPTH || !open.add(table)) {
			throw new LuaError("can't pass a table that contains itself to a client", 0);
		}
		try {
			List<LuaValue> keys = new ArrayList<>();
			List<LuaValue> values = new ArrayList<>();
			Varargs next = table.next(Constants.NIL);
			while (!next.first().isNil()) {
				keys.add(next.first());
				values.add(next.arg(2));
				next = table.next(next.first());
			}
			int length = table.length();
			if (length == keys.size()) {
				List<Object> list = new ArrayList<>(length);
				for (int i = 1; i <= length; i++) {
					list.add(toJava(table.rawget(i), depth + 1, open));
				}
				return list;
			}
			Map<String, Object> map = new LinkedHashMap<>();
			for (int i = 0; i < keys.size(); i++) {
				LuaValue key = keys.get(i);
				String name = switch (key.typeName()) {
					case "string" -> new String(bytes(key.checkLuaString()), StandardCharsets.UTF_8);
					case "number" -> {
						Object number = toJava(key, depth, open);
						yield String.valueOf(number);
					}
					default -> throw new LuaError("can't pass a table with " + key.typeName() + " keys to a client", 0);
				};
				map.put(name, toJava(values.get(i), depth + 1, open));
			}
			return map;
		} finally {
			open.remove(table);
		}
	}

	private static byte[] bytes(LuaString string) {
		byte[] bytes = new byte[string.length()];
		string.copyTo(bytes, 0);
		return bytes;
	}

	/** A client's result: null is no value, an Object[] several values. */
	static Varargs toLua(Object value) throws LuaError {
		if (value instanceof Object[] values) {
			LuaValue[] list = new LuaValue[values.length];
			for (int i = 0; i < values.length; i++) {
				list[i] = single(values[i], 0);
			}
			return ValueFactory.varargsOf(list);
		}
		return value == null ? Constants.NONE : single(value, 0);
	}

	private static LuaValue single(Object value, int depth) throws LuaError {
		if (depth >= MAX_DEPTH) {
			throw new LuaError("client returned a table nested too deep", 0);
		}
		switch (value) {
			case null:
				return Constants.NIL;
			case Boolean b:
				return ValueFactory.valueOf(b);
			case Byte b:
				return ValueFactory.valueOf((int) b);
			case Short s:
				return ValueFactory.valueOf((int) s);
			case Integer i:
				return ValueFactory.valueOf(i);
			case Long l:
				return l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE ? ValueFactory.valueOf(l.intValue()) : ValueFactory.valueOf(l.doubleValue());
			case Number n:
				return ValueFactory.valueOf(n.doubleValue());
			case CharSequence text:
				return LuaString.valueOf(text.toString().getBytes(StandardCharsets.UTF_8));
			case byte[] bytes:
				return LuaString.valueOf(bytes);
			case List<?> list: {
				LuaTable table = new LuaTable();
				int i = 1;
				for (Object item : list) {
					table.rawset(i++, single(item, depth + 1));
				}
				return table;
			}
			case Map<?, ?> map: {
				LuaTable table = new LuaTable();
				for (Map.Entry<?, ?> entry : map.entrySet()) {
					if (!(entry.getKey() instanceof String) && !(entry.getKey() instanceof Number)) {
						throw new LuaError("client returned a table with a " + (entry.getKey() == null ? "null" : entry.getKey().getClass().getSimpleName()) + " key", 0);
					}
					if (entry.getValue() != null) {
						table.rawset(single(entry.getKey(), depth + 1), single(entry.getValue(), depth + 1));
					}
				}
				return table;
			}
			default:
				throw new LuaError("client returned a " + value.getClass().getSimpleName() + ", which Lua can't use", 0);
		}
	}
}
