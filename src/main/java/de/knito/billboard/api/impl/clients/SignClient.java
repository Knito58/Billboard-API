package de.knito.billboard.api.impl.clients;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;

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
	private static final int LINES = 4;
	private static final int MAX_LENGTH = 80;

	private final SignBlockEntity sign;

	private SignClient(SignBlockEntity sign) {
		this.sign = sign;
	}

	public static void initialize() {
		BillboardApi.CLIENTS.registerForBlockEntities((entity, side) -> entity instanceof SignBlockEntity sign ? new SignClient(sign) : null,
				BlockEntityType.SIGN, BlockEntityType.HANGING_SIGN);
	}

	@Override
	public String type() {
		return "minecraft:sign";
	}

	@Override
	public void methods(LuaMethods methods) {
		methods.add("getLines", args -> getLines(!args.optBool(1, false)))
				.add("getLine", args -> text(!args.optBool(2, false)).getMessage(line(args), false).getString())
				.add("setLine", args -> {
					boolean front = !args.optBool(3, false);
					sign.setText(text(front).setMessage(line(args), Component.literal(checked(args.string(2)))), front);
					return null;
				})
				.add("setLines", args -> {
					List<?> lines = args.list(1);
					if (lines.size() > LINES) {
						throw new LuaException("a sign has " + LINES + " lines, got " + lines.size());
					}
					boolean front = !args.optBool(2, false);
					SignText text = text(front);
					for (int i = 0; i < LINES; i++) {
						Object line = i < lines.size() ? lines.get(i) : "";
						text = text.setMessage(i, Component.literal(checked(line == null ? "" : line.toString())));
					}
					sign.setText(text, front);
					return null;
				})
				.add("clear", args -> {
					boolean front = !args.optBool(1, false);
					SignText text = text(front);
					for (int i = 0; i < LINES; i++) {
						text = text.setMessage(i, Component.empty());
					}
					sign.setText(text, front);
					return null;
				});
	}

	private SignText text(boolean front) {
		return sign.getText(front);
	}

	private List<String> getLines(boolean front) {
		List<String> lines = new ArrayList<>(LINES);
		for (int i = 0; i < LINES; i++) {
			lines.add(text(front).getMessage(i, false).getString());
		}
		return lines;
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
