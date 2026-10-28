package de.knito.billboard.api.impl.clients;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;

import de.knito.billboard.api.BillboardApi;
import de.knito.billboard.api.LuaArgs;
import de.knito.billboard.api.LuaClient;
import de.knito.billboard.api.LuaException;
import de.knito.billboard.api.LuaMethods;

/**
 * Vanilla signs and hanging signs as Lua clients: the simplest machine there is, and a way to try the network
 * without any other mod. {@code sign.setLine(1, "Hello")} writes the front, {@code sign.setLine(1, "Hi", true)}
 * the back.
 */
public final class SignClient implements LuaClient {
	private static final int LINES = SignText.LINES;
	private static final int MAX_LENGTH = 80;

	private final SignBlockEntity sign;

	private SignClient(SignBlockEntity sign) {
		this.sign = sign;
	}

	public static void initialize() {
		// A fallback rather than a block entity type: it covers signs and hanging signs, and any mod's sign.
		BillboardApi.CLIENTS.registerFallback((level, pos, state, entity, side) -> entity instanceof SignBlockEntity sign ? new SignClient(sign) : null);
	}

	@Override
	public String type() {
		return "minecraft:sign";
	}

	@Override
	public void methods(LuaMethods methods) {
		methods.add("getLines", args -> getLines(slot(args, 1)))
				.add("getLine", args -> getLines(slot(args, 2)).get(line(args)))
				.add("setLine", args -> {
					int line = line(args);
					String text = checked(args.string(2));
					List<String> lines = getLines(slot(args, 3));
					lines.set(line, text);
					return write(slot(args, 3), lines::get);
				})
				.add("setLines", args -> {
					List<?> lines = args.list(1);
					if (lines.size() > LINES) {
						throw new LuaException("a sign has " + LINES + " lines, got " + lines.size());
					}
					List<String> texts = new ArrayList<>(LINES);
					for (int i = 0; i < LINES; i++) {
						Object line = i < lines.size() ? lines.get(i) : null;
						texts.add(checked(line == null ? "" : line.toString()));
					}
					return write(slot(args, 2), texts::get);
				})
				.add("clear", args -> write(slot(args, 1), i -> ""));
	}

	/** The optional "back" flag at that position: true means the back of the sign. */
	private static SignTextSlot slot(LuaArgs args, int index) throws LuaException {
		return args.optBool(index, false) ? SignTextSlot.BACK : SignTextSlot.FRONT;
	}

	private List<String> getLines(SignTextSlot slot) {
		List<String> lines = new ArrayList<>(LINES);
		for (Component message : sign.getText(slot).getMessages(false)) {
			lines.add(message.getString());
		}
		while (lines.size() < LINES) {
			lines.add("");
		}
		return lines;
	}

	/** Replaces all lines of one side, keeping its colour and glow. */
	private Object write(SignTextSlot slot, IntFunction<String> line) {
		List<Component> messages = new ArrayList<>(LINES);
		for (int i = 0; i < LINES; i++) {
			messages.add(Component.literal(line.apply(i)));
		}
		sign.updateText(old -> new SignText(messages, messages, old.getColor(), old.hasGlowingText()), slot);
		return null;
	}

	/** Lines are numbered 1 to 4 in Lua. */
	private static int line(LuaArgs args) throws LuaException {
		int line = args.integer(1);
		if (line < 1 || line > LINES) {
			throw new LuaException("bad argument #1 (line must be 1 to " + LINES + ", got " + line + ")");
		}
		return line - 1;
	}

	private static String checked(String text) throws LuaException {
		if (text.length() > MAX_LENGTH) {
			throw new LuaException("a sign line holds at most " + MAX_LENGTH + " characters");
		}
		return text;
	}
}
