package de.knito.billboard.api.impl.box;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;

import de.knito.billboard.api.LuaArgs;
import de.knito.billboard.api.LuaException;
import de.knito.billboard.api.LuaMethod;
import de.knito.billboard.api.LuaMethods;
import de.knito.billboard.api.impl.BillboardApiMod;
import de.knito.billboard.api.impl.network.Network;
import de.knito.billboard.api.lua.cobalt.Constants;
import de.knito.billboard.api.lua.cobalt.LuaError;
import de.knito.billboard.api.lua.cobalt.LuaTable;
import de.knito.billboard.api.lua.cobalt.LuaValue;
import de.knito.billboard.api.lua.cobalt.ValueFactory;
import de.knito.billboard.api.lua.cobalt.Varargs;
import de.knito.billboard.api.lua.cobalt.function.RegisteredFunction;

/**
 * The Java half of the {@code network} table and of client handles; the Lua half is in the prelude. A handle is a
 * number here: it stands for a position and a type, not for a Java object, so a handle to a machine that was broken
 * or cut off raises an error on every call and works again when the machine is back.
 */
final class NetworkLibrary {
	/** The type Billboard Engine registers; the global {@code engine} is the one engine in the network. */
	static final String ENGINE_TYPE = "billboard:engine";
	static final Set<String> RESERVED = Set.of("getName", "setName", "getType");
	/** Client types already reported for using a reserved name. */
	private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

	private record Handle(BlockPos pos, String type, String[] name) {
	}

	private final LuaBoxBlockEntity box;
	private final Map<BlockPos, Integer> ids = new HashMap<>();
	private final List<Handle> handles = new ArrayList<>();
	private final Map<Integer, CompletableFuture<?>> jobs = new HashMap<>();
	private int nextJob = 1;

	NetworkLibrary(LuaBoxBlockEntity box) {
		this.box = box;
	}

	LuaTable functions() {
		return RegisteredFunction.bind(new RegisteredFunction[]{
				RegisteredFunction.of("list", s -> list()),
				RegisteredFunction.of("version", s -> ValueFactory.valueOf(box.network().version())),
				RegisteredFunction.of("byName", (s, name) -> byName(name.checkString())),
				RegisteredFunction.of("byType", (s, type) -> byType(type.checkString())),
				RegisteredFunction.of("engine", s -> engine()),
				RegisteredFunction.ofV("call", (s, args) -> call(args)),
				RegisteredFunction.ofV("poll", (s, args) -> poll(args.arg(1).checkInteger())),
		});
	}

	/** Called when the program ends: waiting calls are dropped. */
	void close() {
		jobs.values().forEach(job -> job.cancel(false));
		jobs.clear();
	}

	// ---- finding clients ----

	private Network network() throws LuaError {
		Network network = box.network().get();
		if (network.problem() != null) {
			throw new LuaError(network.problem(), 0);
		}
		return network;
	}

	private List<Network.Member> usable() throws LuaError {
		return network().members().stream().filter(Network.Member::usable).toList();
	}

	private LuaValue list() throws LuaError {
		LuaTable table = new LuaTable();
		int i = 1;
		for (Network.Member member : usable().stream().sorted(Comparator.comparing(m -> Network.lower(m.name()))).toList()) {
			table.rawset(i++, ValueFactory.valueOf(member.name()));
		}
		return table;
	}

	private LuaValue byName(String name) throws LuaError {
		for (Network.Member member : usable()) {
			if (Network.lower(member.name()).equals(Network.lower(name))) {
				return info(member);
			}
		}
		return Constants.NIL;
	}

	/** "typestones:typewriter", or just "typewriter" while only one mod has a client of that name. */
	private LuaValue byType(String type) throws LuaError {
		List<Network.Member> members = usable();
		Set<String> fullTypes = new TreeSet<>();
		List<Network.Member> matches = new ArrayList<>();
		for (Network.Member member : members) {
			boolean match = type.indexOf(':') >= 0 ? member.type().equals(type) : member.type().endsWith(":" + type) || member.type().equals(type);
			if (match) {
				fullTypes.add(member.type());
				matches.add(member);
			}
		}
		if (fullTypes.size() > 1) {
			throw new LuaError("\"" + type + "\" could mean " + String.join(" or ", fullTypes) + "; give the full type", 0);
		}
		LuaTable table = new LuaTable();
		int i = 1;
		for (Network.Member member : matches) {
			table.rawset(i++, info(member));
		}
		return table;
	}

	private LuaValue engine() throws LuaError {
		List<Network.Member> engines = usable().stream().filter(m -> m.type().equals(ENGINE_TYPE)).toList();
		if (engines.size() != 1) {
			throw new LuaError(engines.isEmpty() ? "no Billboard Engine in this network" : engines.size() + " Billboard Engines in this network, use network.find(\"engine\")", 0);
		}
		return info(engines.getFirst());
	}

	/** What the prelude needs to make a handle: its number and the names of its functions. */
	private LuaValue info(Network.Member member) throws LuaError {
		Integer id = ids.get(member.pos());
		if (id == null || !handles.get(id - 1).type().equals(member.type())) {
			handles.add(new Handle(member.pos(), member.type(), new String[]{member.name()}));
			id = handles.size();
			ids.put(member.pos(), id);
		}
		LuaTable methods = new LuaTable();
		int i = 1;
		for (String name : RESERVED) {
			methods.rawset(i++, ValueFactory.valueOf(name));
		}
		for (String name : methods(member).keySet()) {
			methods.rawset(i++, ValueFactory.valueOf(name));
		}
		LuaTable info = new LuaTable();
		info.rawset("id", ValueFactory.valueOf(id));
		info.rawset("methods", methods);
		return info;
	}

	private static Map<String, LuaMethod> methods(Network.Member member) {
		Map<String, LuaMethod> methods = new LinkedHashMap<>();
		member.client().methods(new LuaMethods() {
			@Override
			public LuaMethods add(String name, LuaMethod method) {
				if (RESERVED.contains(name)) {
					if (WARNED.add(member.type())) {
						BillboardApiMod.LOGGER.error("Lua client {} defines {}, which the library provides; its own is ignored", member.type(), name);
					}
				} else if (name != null && name.matches("[A-Za-z_][A-Za-z0-9_]*") && method != null) {
					methods.put(name, method);
				}
				return this;
			}
		});
		return methods;
	}

	// ---- calling clients ----

	private Network.Member resolve(Handle handle) throws LuaError {
		Network.Member member = network().at(handle.pos());
		if (member == null || !member.usable() || !member.type().equals(handle.type())) {
			throw new LuaError("client \"" + handle.name()[0] + "\" is not reachable", 0);
		}
		handle.name()[0] = member.name();
		return member;
	}

	private Varargs call(Varargs args) throws LuaError {
		int id = args.arg(1).checkInteger();
		String name = args.arg(2).checkString();
		if (id < 1 || id > handles.size()) {
			throw new LuaError("not a client handle", 0);
		}
		Network.Member member = resolve(handles.get(id - 1));
		LuaArgs luaArgs = LuaValues.args(args, 3);
		switch (name) {
			case "getName":
				return done(member.name());
			case "getType":
				return done(member.type());
			case "setName":
				return setName(member, luaArgs);
			default:
				break;
		}
		LuaMethod method = methods(member).get(name);
		if (method == null) {
			throw new LuaError(member.name() + " has no function " + name, 0);
		}
		Object result;
		try {
			result = method.call(luaArgs);
		} catch (LuaException e) {
			throw new LuaError(e.getMessage(), 0);
		} catch (RuntimeException e) {
			BillboardApiMod.LOGGER.warn("Lua client {} failed in {}", member.type(), name, e);
			throw new LuaError(member.name() + "." + name + " failed: " + e, 0);
		}
		if (result instanceof CompletableFuture<?> future) {
			int job = nextJob++;
			jobs.put(job, future);
			return ValueFactory.varargsOf(Constants.FALSE, ValueFactory.valueOf(job));
		}
		return done(result);
	}

	/** false while a client's future is not done; then true and its result. */
	private Varargs poll(int id) throws LuaError {
		CompletableFuture<?> job = jobs.get(id);
		if (job == null) {
			throw new LuaError("unknown call", 0);
		}
		if (!job.isDone()) {
			return Constants.FALSE;
		}
		jobs.remove(id);
		Object result;
		try {
			result = job.join();
		} catch (CompletionException | java.util.concurrent.CancellationException e) {
			Throwable cause = e.getCause() != null ? e.getCause() : e;
			if (cause instanceof LuaException) {
				throw new LuaError(cause.getMessage(), 0);
			}
			BillboardApiMod.LOGGER.warn("A Lua client's call failed", cause);
			throw new LuaError("call failed: " + cause, 0);
		}
		if (result instanceof CompletableFuture<?>) {
			throw new LuaError("client returned a future of a future", 0);
		}
		return done(result);
	}

	private Varargs setName(Network.Member member, LuaArgs args) throws LuaError {
		String name;
		try {
			name = args.string(1);
		} catch (LuaException e) {
			throw new LuaError(e.getMessage(), 0);
		}
		String problem = box.network().rename(member, name);
		if (problem != null) {
			return ValueFactory.varargsOf(Constants.NIL, ValueFactory.valueOf(problem));
		}
		return Constants.TRUE;
	}

	private static Varargs done(Object result) throws LuaError {
		return ValueFactory.varargsOf(Constants.TRUE, LuaValues.toLua(result));
	}
}
