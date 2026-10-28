package de.knito.billboard.api.impl.net;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import de.knito.billboard.api.impl.BillboardApiMod;

/**
 * One piece of a file dropped onto the Lua Box screen. A packet from client to server may hold only about
 * 32 KB, so a file comes in pieces of {@link #CHUNK} bytes, in order; offset says where this piece belongs.
 */
public record BoxUploadPayload(String name, int total, int offset, byte[] data) implements CustomPacketPayload {
	public static final int CHUNK = 30000;

	public static final Type<BoxUploadPayload> TYPE = new Type<>(BillboardApiMod.id("box_upload"));

	public static final StreamCodec<ByteBuf, BoxUploadPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(256), BoxUploadPayload::name,
			ByteBufCodecs.VAR_INT, BoxUploadPayload::total,
			ByteBufCodecs.VAR_INT, BoxUploadPayload::offset,
			ByteBufCodecs.byteArray(CHUNK), BoxUploadPayload::data,
			BoxUploadPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
