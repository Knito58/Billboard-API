package de.knito.billboard.api.impl.box;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.imageio.ImageIO;

import net.minecraft.server.level.ServerPlayer;

import de.knito.billboard.api.impl.ApiConfig;
import de.knito.billboard.api.impl.net.LuaBoxActionPayload;
import de.knito.billboard.api.impl.net.BoxUploadPayload;

/**
 * Files dropped onto the Lua Box screen arrive here in pieces. The server checks everything again, whatever
 * the client checked: who may upload, names, types, sizes, the number of files, and that an image really is one.
 */
public final class BoxUploads {
	/** The file a player is sending right now, one per player. */
	private record Upload(LuaBoxBlockEntity box, String name, int total, ByteArrayOutputStream data) {
	}

	private static final Map<UUID, Upload> UPLOADS = new HashMap<>();

	private BoxUploads() {
	}

	/** The box whose screen this player has open, or null. */
	private static LuaBoxBlockEntity openBox(ServerPlayer player) {
		if (!(player.containerMenu instanceof LuaBoxMenu menu) || menu.box() == null || !menu.stillValid(player)) {
			return null;
		}
		return menu.box();
	}

	/** The box whose screen this player has open, if they may change its files; otherwise tells them why not. */
	private static LuaBoxBlockEntity editableBox(ServerPlayer player) {
		LuaBoxBlockEntity box = openBox(player);
		if (box != null && !box.canEdit(player)) {
			box.print(player.getName().getString() + " may not change the files of this Lua Box; only its owner"
					+ (ApiConfig.boxesOpsOnly() ? " (server: operators only)" : " and operators") + " may.", true);
			return null;
		}
		return box;
	}

	/** Restart and Stop are for everybody (unless boxes_ops_only); removing a file only for those who may edit. */
	public static void action(ServerPlayer player, LuaBoxActionPayload payload) {
		LuaBoxBlockEntity box = payload.action() == LuaBoxActionPayload.Action.DELETE ? editableBox(player) : openBox(player);
		if (box == null) {
			return;
		}
		switch (payload.action()) {
			case RESTART, STOP -> {
				if (!box.canControl(player)) {
					box.print(player.getName().getString() + " may not restart or stop this Lua Box (server: operators only).", true);
				} else if (payload.action() == LuaBoxActionPayload.Action.RESTART) {
					box.restart();
				} else {
					box.stop();
				}
			}
			case DELETE -> box.deleteFile(payload.name());
		}
	}

	public static void receive(ServerPlayer player, BoxUploadPayload piece) {
		Upload upload = UPLOADS.get(player.getUUID());
		if (piece.offset() == 0) {
			LuaBoxBlockEntity box = editableBox(player);
			if (box == null) {
				UPLOADS.remove(player.getUUID());
				return;
			}
			String problem = check(box, piece.name(), piece.total());
			if (problem != null) {
				box.print(problem, true);
				UPLOADS.remove(player.getUUID());
				return;
			}
			upload = new Upload(box, piece.name(), piece.total(), new ByteArrayOutputStream(piece.total()));
			UPLOADS.put(player.getUUID(), upload);
		}
		// A piece out of order, for another file, or for a box that is gone ends the upload.
		if (upload == null || !upload.name().equals(piece.name()) || upload.data().size() != piece.offset()
				|| upload.box().isRemoved() || !(player.containerMenu instanceof LuaBoxMenu menu) || menu.box() != upload.box()) {
			UPLOADS.remove(player.getUUID());
			return;
		}
		upload.data().write(piece.data(), 0, piece.data().length);
		if (upload.data().size() > upload.total()) {
			upload.box().print(upload.name() + ": more data than announced, dropped.", true);
			UPLOADS.remove(player.getUUID());
			return;
		}
		if (upload.data().size() == upload.total()) {
			UPLOADS.remove(player.getUUID());
			finish(upload);
		}
	}

	/** Forgets a half-sent file when the player leaves. */
	public static void forget(UUID player) {
		UPLOADS.remove(player);
	}

	/** Why this file may not go into the box, or null if it may. */
	static String check(LuaBoxBlockEntity box, String name, int size) {
		if (!BoxStorage.isValidName(name)) {
			return name + ": please use only letters, digits, _ - and . in file names.";
		}
		if (!BoxStorage.isAllowedType(name)) {
			return name + ": a Lua Box takes main.lua and files of these types: " + String.join(", ", ApiConfig.boxFileTypes()) + ".";
		}
		if (size <= 0) {
			return name + " is empty.";
		}
		String tooLarge = BoxStorage.sizeProblem(name, size);
		if (tooLarge != null) {
			return tooLarge;
		}
		if (!BoxStorage.isMain(name) && box.folder() != null) {
			long others = BoxStorage.extraFiles(box.folder()).stream()
					.filter(p -> !p.getFileName().toString().equals(name))
					.count();
			if (others >= ApiConfig.boxMaxFiles()) {
				return "This box already holds " + others + " files besides main.lua, the most allowed. Remove one first.";
			}
		}
		return null;
	}

	private static void finish(Upload upload) {
		byte[] data = upload.data().toByteArray();
		LuaBoxBlockEntity box = upload.box();
		if (!BoxStorage.isImage(upload.name())) {
			try {
				StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(data));
			} catch (CharacterCodingException e) {
				box.print(upload.name() + " is not a text file in UTF-8. Only images may hold other bytes.", true);
				return;
			}
			box.storeFile(upload.name(), data);
			return;
		}
		// Reading a big GIF takes a moment; do it beside the game, then store the file in the tick.
		box.print("Checking " + upload.name() + "...", false);
		CompletableFuture.runAsync(() -> {
			String problem = null;
			try {
				if (ImageIO.read(new ByteArrayInputStream(data)) == null) {
					problem = upload.name() + " is not an image Java can read.";
				}
			} catch (IOException | RuntimeException e) {
				problem = upload.name() + " is not an image Java can read.";
			}
			String result = problem;
			box.onServerThread(() -> {
				if (box.isRemoved()) {
					return;
				}
				if (result != null) {
					box.print(result, true);
				} else if (check(box, upload.name(), data.length) == null) {
					box.storeFile(upload.name(), data);
				}
			});
		}, BoxThreads.WORKER);
	}
}
