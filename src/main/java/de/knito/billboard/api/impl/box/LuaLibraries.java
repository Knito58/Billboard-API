package de.knito.billboard.api.impl.box;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.Locale;

import net.minecraft.server.level.ServerLevel;

import de.knito.billboard.api.lua.cobalt.Constants;
import de.knito.billboard.api.lua.cobalt.LuaError;
import de.knito.billboard.api.lua.cobalt.LuaState;
import de.knito.billboard.api.lua.cobalt.LuaString;
import de.knito.billboard.api.lua.cobalt.LuaTable;
import de.knito.billboard.api.lua.cobalt.LuaThread;
import de.knito.billboard.api.lua.cobalt.LuaValue;
import de.knito.billboard.api.lua.cobalt.ValueFactory;
import de.knito.billboard.api.lua.cobalt.Varargs;
import de.knito.billboard.api.lua.cobalt.compiler.CompileException;
import de.knito.billboard.api.lua.cobalt.compiler.LoadState;
import de.knito.billboard.api.lua.cobalt.function.RegisteredFunction;

/**
 * What a Lua Box program sees besides standard Lua: sleep, print, require, {@code network}, the {@code files} table for the box's
 * other files, and the parts of CC: Tweaked's {@code os} and {@code textutils} that clock scripts need.
 * No writing of files. The {@code network} table and client handles come from {@link NetworkLibrary}.
 */
final class LuaLibraries {
	/** Written in Lua, because sleep() must be able to pause the program. */
	private static final String PRELUDE = """
			local java, SLEEP = ...
			local yield, select, tostring, type, concat, error = coroutine.yield, select, tostring, type, table.concat, error

			function sleep(seconds)
				seconds = seconds or 0
				if type(seconds) ~= "number" then
					error("bad argument #1 to 'sleep' (number expected, got " .. type(seconds) .. ")", 2)
				end
				yield(SLEEP, seconds)
			end

			function print(...)
				local parts = {}
				for i = 1, select("#", ...) do parts[i] = tostring((select(i, ...))) end
				java.print(concat(parts, " "))
			end

			-- require("name") runs name.lua from the box once and returns what it returned.
			local loaded, LOADING, loadModule, pcall = {}, {}, java.loadModule, pcall
			package = { loaded = loaded }
			function require(name)
				if type(name) ~= "string" then
					error("bad argument #1 to 'require' (string expected, got " .. type(name) .. ")", 2)
				end
				local value = loaded[name]
				if value == LOADING then error("module '" .. name .. "' requires itself", 2) end
				if value ~= nil then return value end
				local ok, chunk = pcall(loadModule, name)
				if not ok then error(chunk, 2) end
				loaded[name] = LOADING
				local result = chunk(name)
				if result == nil then result = true end
				loaded[name] = result
				return result
			end

			files = { list = java.listFiles, read = java.readFile }
			function files.lines(name)
				local text, pos = java.readFile(name), 1
				return function()
					if pos > #text then return nil end
					local nl = text:find("\\n", pos, true)
					local line = text:sub(pos, (nl or #text + 1) - 1)
					pos = (nl or #text) + 1
					return (line:gsub("\\r$", ""))
				end
			end

			-- network: the Lua clients this box reaches. A handle's functions are called with a dot, tw.type("HI");
			-- a colon works too. A client may answer later: then the program waits here, tick by tick.
			local net, pack, unpack, ipairs, setmetatable = java.network, table.pack, table.unpack, ipairs, setmetatable
			local call, poll = net.call, net.poll
			local handles = {}
			local function handleFor(info)
				local id = info.id
				local h = handles[id]
				if not h then h = {}; handles[id] = h end
				for _, name in ipairs(info.methods) do
					if h[name] == nil then
						h[name] = function(...)
							local r
							if select("#", ...) > 0 and (...) == h then
								r = pack(pcall(call, id, name, select(2, ...)))
							else
								r = pack(pcall(call, id, name, ...))
							end
							if not r[1] then error(r[2], 2) end
							if r[2] then return unpack(r, 3, r.n) end
							local job = r[3]
							while true do
								yield(SLEEP, 0)
								r = pack(pcall(poll, job))
								if not r[1] then error(r[2], 2) end
								if r[2] then return unpack(r, 3, r.n) end
							end
						end
					end
				end
				return h
			end

			network = {}
			function network.list()
				local ok, names = pcall(net.list)
				if not ok then error(names, 2) end
				return names
			end
			function network.version()
				return net.version()
			end
			function network.findByName(name)
				if type(name) ~= "string" then
					error("bad argument #1 to 'findByName' (string expected, got " .. type(name) .. ")", 2)
				end
				local ok, info = pcall(net.byName, name)
				if not ok then error(info, 2) end
				return info and handleFor(info) or nil
			end
			function network.find(kind)
				if type(kind) ~= "string" then
					error("bad argument #1 to 'find' (string expected, got " .. type(kind) .. ")", 2)
				end
				local ok, infos = pcall(net.byType, kind)
				if not ok then error(infos, 2) end
				local list = {}
				for i, info in ipairs(infos) do list[i] = handleFor(info) end
				return list
			end

			-- engine: kept for Script Box programs, the one Billboard Engine in the network.
			local engineProxy
			engineProxy = setmetatable({}, { __index = function(_, key)
				return function(...)
					local ok, info = pcall(net.engine)
					if not ok then error(info, 2) end
					local f = handleFor(info)[key]
					if f == nil then error("the engine has no function " .. tostring(key), 2) end
					if select("#", ...) > 0 and (...) == engineProxy then return f(select(2, ...)) end
					return f(...)
				end
			end })
			engine = engineProxy

			textutils = {}
			function textutils.formatTime(time, twentyFour)
				if type(time) ~= "number" then
					error("bad argument #1 to 'formatTime' (number expected, got " .. type(time) .. ")", 2)
				end
				local suffix
				if not twentyFour then
					suffix = time >= 12 and "PM" or "AM"
					if time >= 13 then time = time - 12 end
				end
				local hour = math.floor(time)
				local minute = math.floor((time - hour) * 60)
				if suffix then
					return string.format("%d:%02d %s", hour == 0 and 12 or hour, minute, suffix)
				end
				return string.format("%d:%02d", hour, minute)
			end
			""";

	private final LuaBoxBlockEntity box;
	private final LuaState state;
	private final long startTick;
	private final NetworkLibrary network;

	LuaLibraries(LuaBoxBlockEntity box, LuaState state) {
		this.box = box;
		this.state = state;
		this.startTick = box.getLevel() == null ? 0 : box.getLevel().getGameTime();
		this.network = new NetworkLibrary(box);
	}

	void install(LuaTable sleepToken) throws LuaError, CompileException {
		LuaTable globals = state.globals();
		globals.rawset("os", osTable());

		LuaTable java = RegisteredFunction.bind(new RegisteredFunction[]{
				RegisteredFunction.of("print", (s, line) -> {
					box.print(line.checkString(), false);
					return Constants.NIL;
				}),
				RegisteredFunction.of("loadModule", (s, name) -> loadModule(name.checkString())),
				RegisteredFunction.of("listFiles", s -> listFiles()),
				RegisteredFunction.of("readFile", (s, name) -> readFile(name.checkString())),
		});
		java.rawset("network", network.functions());
		LuaValue prelude = LoadState.load(state, new ByteArrayInputStream(PRELUDE.getBytes(StandardCharsets.UTF_8)), "=prelude", globals);
		LuaThread.runMain(state, prelude.checkFunction(), ValueFactory.varargsOf(java, sleepToken));
	}

	/** Called when the program ends: calls still waiting for a client are dropped. */
	void close() {
		network.close();
	}

	// ---- files and modules ----

	/** Compiles name.lua from the box for require(). */
	private LuaValue loadModule(String name) throws LuaError {
		String fileName = name + ".lua";
		if (BoxStorage.isMain(fileName)) {
			throw new LuaError("main.lua is the program, not a module", 0);
		}
		Path file = box.file(fileName);
		if (file == null) {
			throw new LuaError("module '" + name + "' not found: no " + fileName + " in this Lua Box", 0);
		}
		try {
			return LoadState.load(state, new ByteArrayInputStream(Files.readAllBytes(file)), "@" + fileName, state.globals());
		} catch (IOException e) {
			throw new LuaError("could not read " + fileName, 0);
		} catch (CompileException e) {
			throw new LuaError(e.getMessage(), 0);
		}
	}

	/** The names of all files of the box, main.lua included, sorted. */
	private LuaValue listFiles() {
		LuaTable list = new LuaTable();
		int i = 1;
		for (Path file : BoxStorage.files(box.folder())) {
			list.rawset(i++, ValueFactory.valueOf(file.getFileName().toString()));
		}
		return list;
	}

	/** A whole file of the box as a string of bytes, as Lua's io.read("a") would give it. */
	private LuaValue readFile(String name) throws LuaError {
		Path file = box.file(name);
		if (file == null) {
			throw new LuaError("no file called " + name + " in this Lua Box");
		}
		try {
			return LuaString.valueOf(Files.readAllBytes(file));
		} catch (IOException e) {
			throw new LuaError("could not read " + name);
		}
	}

	// ---- os, as in CC: Tweaked ----

	private LuaTable osTable() {
		return RegisteredFunction.bind(new RegisteredFunction[]{
				RegisteredFunction.ofV("time", (s, args) -> time(args.arg(1))),
				RegisteredFunction.ofV("date", (s, args) -> date(args.arg(1).optString("%c"), args.arg(2))),
				RegisteredFunction.of("clock", s -> ValueFactory.valueOf((gameTime() - startTick) / 20.0)),
				RegisteredFunction.of("day", (s, kind) -> ValueFactory.valueOf(day(kind.optString("ingame")))),
				RegisteredFunction.of("epoch", (s, kind) -> ValueFactory.valueOf((double) epoch(kind.optString("ingame")))),
		});
	}

	private long gameTime() {
		return box.getLevel() == null ? startTick : box.getLevel().getGameTime();
	}

	/** Time of day in game ticks, 0 = 6:00 in the morning. */
	private long dayTime() {
		return box.getLevel() instanceof ServerLevel level ? LuaBoxBlockEntity.dayTime(level) : 0;
	}

	private Varargs time(LuaValue arg) throws LuaError {
		if (arg instanceof LuaTable) {
			LuaTable table = arg.checkTable();
			LocalDateTime local = LocalDateTime.of(
					field(table, "year", null), field(table, "month", null), 1,
					field(table, "hour", 12), field(table, "min", 0), field(table, "sec", 0));
			local = local.plusDays(field(table, "day", null) - 1L);
			return ValueFactory.valueOf(local.atZone(ZoneId.systemDefault()).toEpochSecond());
		}
		String kind = arg.optString("ingame").toLowerCase(Locale.ROOT);
		return switch (kind) {
			case "ingame" -> ValueFactory.valueOf(((dayTime() + 6000) % 24000) / 1000.0);
			case "utc" -> ValueFactory.valueOf(Instant.now().getEpochSecond());
			case "local" -> ValueFactory.valueOf(Instant.now().getEpochSecond() + ZoneId.systemDefault().getRules().getOffset(Instant.now()).getTotalSeconds());
			default -> throw new LuaError("Unsupported operation");
		};
	}

	private static int field(LuaTable table, String key, Integer fallback) throws LuaError {
		LuaValue value = table.rawget(key);
		if (value.isNil()) {
			if (fallback == null) {
				throw new LuaError("field '" + key + "' missing in date table");
			}
			return fallback;
		}
		return value.checkInteger();
	}

	private int day(String kind) throws LuaError {
		return switch (kind.toLowerCase(Locale.ROOT)) {
			case "ingame" -> (int) ((dayTime() + 6000) / 24000) + 1;
			case "utc" -> (int) (Instant.now().getEpochSecond() / 86400);
			case "local" -> (int) ((Instant.now().getEpochSecond() + ZoneId.systemDefault().getRules().getOffset(Instant.now()).getTotalSeconds()) / 86400);
			default -> throw new LuaError("Unsupported operation");
		};
	}

	private long epoch(String kind) throws LuaError {
		return switch (kind.toLowerCase(Locale.ROOT)) {
			// Milliseconds of game time, where one in-game day lasts 86 400 000.
			case "ingame" -> (dayTime() + 6000) * 3600;
			case "utc" -> System.currentTimeMillis();
			case "local" -> System.currentTimeMillis() + ZoneId.systemDefault().getRules().getOffset(Instant.now()).getTotalSeconds() * 1000L;
			default -> throw new LuaError("Unsupported operation");
		};
	}

	/** os.date with the strftime codes of standard Lua; "!" in front means UTC, "*t" gives a table. */
	private static Varargs date(String format, LuaValue timeArg) throws LuaError {
		long seconds = timeArg.isNil() ? Instant.now().getEpochSecond() : timeArg.checkLong();
		ZoneId zone = ZoneId.systemDefault();
		if (format.startsWith("!")) {
			zone = ZoneOffset.UTC;
			format = format.substring(1);
		}
		ZonedDateTime t = Instant.ofEpochSecond(seconds).atZone(zone);
		if (format.startsWith("*t")) {
			LuaTable table = new LuaTable();
			table.rawset("year", ValueFactory.valueOf(t.getYear()));
			table.rawset("month", ValueFactory.valueOf(t.getMonthValue()));
			table.rawset("day", ValueFactory.valueOf(t.getDayOfMonth()));
			table.rawset("hour", ValueFactory.valueOf(t.getHour()));
			table.rawset("min", ValueFactory.valueOf(t.getMinute()));
			table.rawset("sec", ValueFactory.valueOf(t.getSecond()));
			table.rawset("wday", ValueFactory.valueOf(t.getDayOfWeek().getValue() % 7 + 1));
			table.rawset("yday", ValueFactory.valueOf(t.getDayOfYear()));
			table.rawset("isdst", ValueFactory.valueOf(zone.getRules().isDaylightSavings(t.toInstant())));
			return table;
		}
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < format.length(); i++) {
			char c = format.charAt(i);
			if (c != '%' || i + 1 >= format.length()) {
				out.append(c);
				continue;
			}
			char code = format.charAt(++i);
			switch (code) {
				case 'a' -> out.append(t.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
				case 'A' -> out.append(t.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH));
				case 'b', 'h' -> out.append(t.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
				case 'B' -> out.append(t.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH));
				case 'c' -> out.append(String.format(Locale.ROOT, "%s %s %2d %02d:%02d:%02d %d",
						t.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH), t.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
						t.getDayOfMonth(), t.getHour(), t.getMinute(), t.getSecond(), t.getYear()));
				case 'd' -> out.append(String.format(Locale.ROOT, "%02d", t.getDayOfMonth()));
				case 'e' -> out.append(String.format(Locale.ROOT, "%2d", t.getDayOfMonth()));
				case 'D' -> out.append(String.format(Locale.ROOT, "%02d/%02d/%02d", t.getMonthValue(), t.getDayOfMonth(), t.getYear() % 100));
				case 'F' -> out.append(String.format(Locale.ROOT, "%d-%02d-%02d", t.getYear(), t.getMonthValue(), t.getDayOfMonth()));
				case 'H' -> out.append(String.format(Locale.ROOT, "%02d", t.getHour()));
				case 'I' -> out.append(String.format(Locale.ROOT, "%02d", (t.getHour() + 11) % 12 + 1));
				case 'j' -> out.append(String.format(Locale.ROOT, "%03d", t.getDayOfYear()));
				case 'm' -> out.append(String.format(Locale.ROOT, "%02d", t.getMonthValue()));
				case 'M' -> out.append(String.format(Locale.ROOT, "%02d", t.getMinute()));
				case 'n' -> out.append('\n');
				case 'p' -> out.append(t.getHour() < 12 ? "AM" : "PM");
				case 'R' -> out.append(String.format(Locale.ROOT, "%02d:%02d", t.getHour(), t.getMinute()));
				case 'S' -> out.append(String.format(Locale.ROOT, "%02d", t.getSecond()));
				case 't' -> out.append('\t');
				case 'T', 'X' -> out.append(String.format(Locale.ROOT, "%02d:%02d:%02d", t.getHour(), t.getMinute(), t.getSecond()));
				case 'u' -> out.append(t.getDayOfWeek().getValue());
				case 'w' -> out.append(t.getDayOfWeek().getValue() % 7);
				case 'x' -> out.append(String.format(Locale.ROOT, "%02d/%02d/%02d", t.getMonthValue(), t.getDayOfMonth(), t.getYear() % 100));
				case 'y' -> out.append(String.format(Locale.ROOT, "%02d", t.getYear() % 100));
				case 'Y' -> out.append(t.getYear());
				case 'z' -> {
					int offset = t.getOffset().getTotalSeconds() / 60;
					out.append(String.format(Locale.ROOT, "%c%02d%02d", offset < 0 ? '-' : '+', Math.abs(offset) / 60, Math.abs(offset) % 60));
				}
				case 'Z' -> out.append(zone.getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
				case '%' -> out.append('%');
				default -> throw new LuaError("bad argument #1 to 'date' (invalid conversion specifier '%" + code + "')");
			}
		}
		return ValueFactory.valueOf(out.toString());
	}
}
