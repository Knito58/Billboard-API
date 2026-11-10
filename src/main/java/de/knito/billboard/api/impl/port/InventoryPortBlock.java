package de.knito.billboard.api.impl.port;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import de.knito.billboard.api.impl.ModBlockEntities;

/**
 * The bridge between Lua and the inventories next to it: chests, barrels, furnaces, and whatever a mod offers
 * through Fabric's Transfer API. A full block that looks like a pipe and passes the network on like one, see
 * {@code Network.scan}. It holds no items itself.
 */
public class InventoryPortBlock extends BaseEntityBlock {
	public InventoryPortBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new InventoryPortBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.INVENTORY_PORT, InventoryPortBlockEntity::serverTick);
	}
}
