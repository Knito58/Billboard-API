package de.knito.billboard.api.impl.port;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import de.knito.billboard.api.impl.ModBlockEntities;

/**
 * The bridge between Lua and vanilla redstone: a full block that gives each of its six sides the signal a
 * program asks for, and tells a program what arrives on each side. It looks like a pipe and passes the
 * network on like one, so it can sit inside a run of pipes; see {@code Network.scan}.
 *
 * <p>Right-clicking it with a block makes it look like that block (the item is not used up); sneak-clicking with
 * an empty hand takes the look off. Sneak-clicking with a block still places the block.
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
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		if (!(stack.getItem() instanceof BlockItem item) || item.getBlock() instanceof RedstonePortBlock) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		BlockState look = item.getBlock().defaultBlockState();
		if (look.isAir() || look.getRenderShape() != RenderShape.MODEL) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof RedstonePortBlockEntity port) {
			port.setCamouflage(look);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!player.isSecondaryUseActive() || !(level.getBlockEntity(pos) instanceof RedstonePortBlockEntity port) || port.camouflage() == null) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			port.setCamouflage(null);
		}
		return InteractionResult.SUCCESS;
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
