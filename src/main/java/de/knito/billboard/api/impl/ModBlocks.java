package de.knito.billboard.api.impl;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import de.knito.billboard.api.impl.box.LuaBoxBlock;
import de.knito.billboard.api.impl.pipe.DataPipeBlock;
import de.knito.billboard.api.impl.port.RedstonePortBlock;

public class ModBlocks {
	public static final Block LUA_BOX = register(
			"lua_box",
			LuaBoxBlock::new,
			BlockBehaviour.Properties.of().strength(1.0f).sound(SoundType.WOOD)
	);

	public static final Block DATA_PIPE = register(
			"data_pipe",
			DataPipeBlock::new,
			BlockBehaviour.Properties.of().strength(0.5f).sound(SoundType.WOOD).noOcclusion()
	);

	public static final Block REDSTONE_PORT = register(
			"redstone_port",
			RedstonePortBlock::new,
			BlockBehaviour.Properties.of().strength(0.5f).sound(SoundType.WOOD)
	);

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> blockFactory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, BillboardApiMod.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, blockFactory.apply(properties.setId(blockKey)));

		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, BillboardApiMod.id(name));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(itemKey)));

		return block;
	}

	public static void initialize() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register((creativeTab) -> {
			creativeTab.accept(LUA_BOX.asItem());
			creativeTab.accept(DATA_PIPE.asItem());
			creativeTab.accept(REDSTONE_PORT.asItem());
		});
	}
}
