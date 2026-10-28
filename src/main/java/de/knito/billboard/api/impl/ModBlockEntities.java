package de.knito.billboard.api.impl;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;

import de.knito.billboard.api.impl.box.LuaBoxBlockEntity;

public class ModBlockEntities {
	public static final BlockEntityType<LuaBoxBlockEntity> LUA_BOX = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			BillboardApiMod.id("lua_box"),
			FabricBlockEntityTypeBuilder.create(LuaBoxBlockEntity::new, ModBlocks.LUA_BOX).build()
	);

	public static void initialize() {
	}
}
