package de.knito.billboard.api.client;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;

import de.knito.billboard.api.impl.ApiConfig;
import de.knito.billboard.api.impl.BillboardApiMod;
import de.knito.billboard.api.impl.network.ClientData;

/**
 * Without any other mod: looking at a machine of a network shows its network name just below the crosshair, so two
 * ports side by side can be told apart. Left out when Jade is installed, whose box shows the name instead (see
 * {@link JadeNames}), or when {@code name_hint=false}.
 */
public final class NameHint {
	private NameHint() {
	}

	static void register() {
		if (!ApiConfig.nameHint() || FabricLoader.getInstance().isModLoaded("jade")) {
			return;
		}
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, BillboardApiMod.id("name_hint"), NameHint::extract);
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker tick) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gui.screen() != null || !(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
			return;
		}
		String name = name(minecraft.level, hit.getBlockPos());
		if (name == null) {
			return;
		}
		Component text = Component.literal(name);
		int width = minecraft.font.width(text);
		int x = (graphics.guiWidth() - width) / 2;
		int y = graphics.guiHeight() / 2 + 10;
		// A dark plate, so the name can be read in front of pale blocks and the sky.
		graphics.fill(x - 2, y - 2, x + width + 2, y + minecraft.font.lineHeight, 0x80000000);
		graphics.text(minecraft.font, text, x, y, 0xFFFFFFFF, true);
	}

	/** The network name of the machine at {@code pos} as the server sent it, or null if it has none. */
	static @Nullable String name(@Nullable Level level, BlockPos pos) {
		BlockEntity entity = level == null ? null : level.getBlockEntity(pos);
		return entity == null ? null : entity.getAttached(ClientData.NAME);
	}
}
