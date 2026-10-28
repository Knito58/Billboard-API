package de.knito.billboard.api.impl;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.SemanticVersion;

/**
 * Turns the Script Boxes of Billboard Engine 1.x into Lua Boxes when a world loads: the old block, item and block
 * entity ids point to the Lua Box. The box keeps its id, and its folder moves over when it is first used
 * ({@link de.knito.billboard.api.impl.box.BoxStorage#folder}). Skipped while Billboard Engine 1.x itself is installed,
 * since that still registers its own Script Box under the old id.
 */
final class ScriptBoxAlias {
	private static final Identifier OLD = Identifier.fromNamespaceAndPath("billboard", "script_box");

	private ScriptBoxAlias() {
	}

	static void initialize() {
		if (oldEngineInstalled()) {
			BillboardApiMod.LOGGER.info("Billboard Engine 1.x is installed and keeps its Script Box; no alias to the Lua Box");
			return;
		}
		Identifier box = BillboardApiMod.id("lua_box");
		BuiltInRegistries.BLOCK.addAlias(OLD, box);
		BuiltInRegistries.ITEM.addAlias(OLD, box);
		BuiltInRegistries.BLOCK_ENTITY_TYPE.addAlias(OLD, box);
	}

	/** Billboard Engine before 2.0, which still has its own Script Box. */
	private static boolean oldEngineInstalled() {
		return FabricLoader.getInstance().getModContainer("billboard")
				.map(mod -> mod.getMetadata().getVersion())
				.map(version -> version instanceof SemanticVersion semantic && semantic.getVersionComponent(0) < 2)
				.orElse(false);
	}
}
