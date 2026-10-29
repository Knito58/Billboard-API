package de.knito.billboard.api.impl.port;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import de.knito.billboard.api.impl.ModBlockEntities;

/**
 * The bridge between Lua and vanilla redstone: a full block that gives each of its six sides the signal a
 * program asks for, and tells a program what arrives on each side. It looks like a pipe and passes the
 * network on like one, so it can sit inside a run of pipes; see {@code Network.scan}.
 */
public class RedstonePortBlock extends BaseEntityBlock {
	public RedstonePortBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new RedstonePortBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.REDSTONE_PORT, RedstonePortBlockEntity::serverTick);
	}

	@Override
	protected boolean isSignalSource(BlockState state) {
		return true;
	}

	/**
	 * Weak power, like a block of redstone: it drives what touches it, but not what sits behind a solid block.
	 * The direction runs from the one asking to the port, so the side of the port is the opposite one.
	 */
	@Override
	protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return level.getBlockEntity(pos) instanceof RedstonePortBlockEntity port ? port.output(direction.getOpposite()) : 0;
	}
}
