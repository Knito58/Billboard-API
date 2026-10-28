package de.knito.billboard.api.impl.network;

import java.util.UUID;
import java.util.regex.Pattern;

import com.mojang.serialization.Codec;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import de.knito.billboard.api.BillboardApi;
import de.knito.billboard.api.impl.BillboardApiMod;

/**
 * What the library stores on the block entity of every Lua client, so client mods write no NBT for it: the
 * client's name and the player who placed it. Both are saved with the chunk and lost when the machine is broken.
 */
public final class ClientData {
	public static final AttachmentType<String> NAME =
			AttachmentRegistry.create(BillboardApiMod.id("client_name"), builder -> builder.persistent(Codec.STRING));
	public static final AttachmentType<UUID> OWNER =
			AttachmentRegistry.create(BillboardApiMod.id("client_owner"), builder -> builder.persistent(UUIDUtil.CODEC));

	private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,32}");
	public static final String NAME_RULE = "1 to 32 letters, digits, _ or -";

	private ClientData() {
	}

	public static void initialize() {
		// Loading the class registers the attachments.
	}

	/** After a player placed a block: if it is a Lua client, they own it. */
	public static void placed(Level level, BlockPos pos, @Nullable Player player) {
		if (player == null || !(level instanceof ServerLevel)) {
			return;
		}
		BlockEntity entity = level.getBlockEntity(pos);
		if (entity != null && BillboardApi.CLIENTS.find(level, pos, null, entity, null) != null) {
			entity.setAttached(OWNER, player.getUUID());
			entity.setChanged();
		}
	}

	public static boolean isValidName(String name) {
		return NAME_PATTERN.matcher(name).matches();
	}

	/** The start of the default names: "typestones:typewriter" gives typewriter_1, typewriter_2 ... */
	public static String shortType(String type) {
		int colon = type.indexOf(':');
		String path = colon < 0 ? type : type.substring(colon + 1);
		String cleaned = path.replaceAll("[^A-Za-z0-9_-]", "_");
		return cleaned.isEmpty() ? "client" : cleaned.length() > 24 ? cleaned.substring(0, 24) : cleaned;
	}
}
