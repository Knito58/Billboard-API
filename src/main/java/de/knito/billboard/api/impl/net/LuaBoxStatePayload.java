package de.knito.billboard.api.impl.net;

import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import de.knito.billboard.api.impl.BillboardApiMod;

/**
 * What the Lua Box screen shows: state, files, output lines, whether this player may change the files (canEdit)
 * or press Restart and Stop (canControl), and the server's upload limits. Sent when the screen opens and whenever
 * something changes. folder is the box's folder on disk, only in single player (empty otherwise), so the screen can
 * open it and its files. network: the clients for the Network view.
 */
public record LuaBoxStatePayload(String status, boolean canEdit, boolean canControl, List<FileEntry> files, List<Line> lines,
		Limits limits, String folder, Network network) implements CustomPacketPayload {
	/** The Lua clients the box reaches, for the Network view. usable: the box may drive it (owner check). */
	public record Client(long pos, String type, String name, boolean usable) {
		static final StreamCodec<ByteBuf, Client> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_LONG, Client::pos,
				ByteBufCodecs.stringUtf8(256), Client::type,
				ByteBufCodecs.stringUtf8(64), Client::name,
				ByteBufCodecs.BOOL, Client::usable,
				Client::new
		);
	}

	/** problem: why the network can't be used, or empty. */
	public record Network(List<Client> clients, String problem) {
		static final StreamCodec<ByteBuf, Network> STREAM_CODEC = StreamCodec.composite(
				Client.STREAM_CODEC.apply(ByteBufCodecs.list(1024)), Network::clients,
				ByteBufCodecs.stringUtf8(256), Network::problem,
				Network::new
		);
	}

	/** The server's limits, so the screen can refuse a file before sending it. types: allowed extensions, comma separated. */
	public record Limits(int maxLuaKb, int maxFiles, int maxFileKb, String types) {
		static final StreamCodec<ByteBuf, Limits> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Limits::maxLuaKb,
				ByteBufCodecs.VAR_INT, Limits::maxFiles,
				ByteBufCodecs.VAR_INT, Limits::maxFileKb,
				ByteBufCodecs.stringUtf8(1024), Limits::types,
				Limits::new
		);
	}

	public record FileEntry(String name, int size) {
		static final StreamCodec<ByteBuf, FileEntry> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.stringUtf8(256), FileEntry::name,
				ByteBufCodecs.VAR_INT, FileEntry::size,
				FileEntry::new
		);
	}

	public record Line(String text, boolean error) {
		static final StreamCodec<ByteBuf, Line> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.stringUtf8(1024), Line::text,
				ByteBufCodecs.BOOL, Line::error,
				Line::new
		);
	}

	public static final Type<LuaBoxStatePayload> TYPE = new Type<>(BillboardApiMod.id("box_state"));

	public static final StreamCodec<ByteBuf, LuaBoxStatePayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(64), LuaBoxStatePayload::status,
			ByteBufCodecs.BOOL, LuaBoxStatePayload::canEdit,
			ByteBufCodecs.BOOL, LuaBoxStatePayload::canControl,
			FileEntry.STREAM_CODEC.apply(ByteBufCodecs.list(256)), LuaBoxStatePayload::files,
			Line.STREAM_CODEC.apply(ByteBufCodecs.list(256)), LuaBoxStatePayload::lines,
			Limits.STREAM_CODEC, LuaBoxStatePayload::limits,
			ByteBufCodecs.stringUtf8(4096), LuaBoxStatePayload::folder,
			Network.STREAM_CODEC, LuaBoxStatePayload::network,
			LuaBoxStatePayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
