package de.knito.billboard.api.impl.net;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import de.knito.billboard.api.impl.BillboardApiMod;

/**
 * A button in the Lua Box screen: Restart, Stop, remove the file with the given name, or give the client at target
 * (a block position as a long) the given name.
 */
public record LuaBoxActionPayload(Action action, String name, long target) implements CustomPacketPayload {
	public enum Action {
		RESTART, STOP, DELETE, RENAME
	}

	public LuaBoxActionPayload(Action action, String name) {
		this(action, name, 0L);
	}

	public static final Type<LuaBoxActionPayload> TYPE = new Type<>(BillboardApiMod.id("box_action"));

	public static final StreamCodec<ByteBuf, LuaBoxActionPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.idMapper(i -> Action.values()[Math.clamp(i, 0, Action.values().length - 1)], Action::ordinal), LuaBoxActionPayload::action,
			ByteBufCodecs.stringUtf8(256), LuaBoxActionPayload::name,
			ByteBufCodecs.VAR_LONG, LuaBoxActionPayload::target,
			LuaBoxActionPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
