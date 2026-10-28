package de.knito.billboard.api.impl.pipe;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import de.knito.billboard.api.BillboardApi;
import de.knito.billboard.api.impl.box.LuaBoxBlock;

/**
 * A thin pipe that joins Lua Boxes and Lua clients into one network. Nothing travels through it; the network walk
 * just follows it. It joins every pipe, box and client next to it by itself. Sneak-clicking an arm, or the side you
 * want, with an empty hand switches that side off, so two networks can run side by side; again switches it on.
 */
public class DataPipeBlock extends Block {
	public static final Map<Direction, EnumProperty<PipeSide>> SIDES = new EnumMap<>(Direction.class);

	static {
		for (Direction direction : Direction.values()) {
			SIDES.put(direction, EnumProperty.create(direction.getSerializedName(), PipeSide.class));
		}
	}

	private static final VoxelShape CORE = Block.box(6, 6, 6, 10, 10, 10);
	private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Direction.class);

	static {
		ARMS.put(Direction.DOWN, Block.box(6, 0, 6, 10, 6, 10));
		ARMS.put(Direction.UP, Block.box(6, 10, 6, 10, 16, 10));
		ARMS.put(Direction.NORTH, Block.box(6, 6, 0, 10, 10, 6));
		ARMS.put(Direction.SOUTH, Block.box(6, 6, 10, 10, 10, 16));
		ARMS.put(Direction.WEST, Block.box(0, 6, 6, 6, 10, 10));
		ARMS.put(Direction.EAST, Block.box(10, 6, 6, 16, 10, 10));
	}

	/** Worked out once for all 729 states; getShape is called from several threads. */
	private final Map<BlockState, VoxelShape> shapes;

	public DataPipeBlock(Properties properties) {
		super(properties);
		BlockState state = stateDefinition.any();
		for (EnumProperty<PipeSide> side : SIDES.values()) {
			state = state.setValue(side, PipeSide.NONE);
		}
		registerDefaultState(state);
		Map<BlockState, VoxelShape> map = new HashMap<>();
		for (BlockState possible : stateDefinition.getPossibleStates()) {
			VoxelShape shape = CORE;
			for (Direction direction : Direction.values()) {
				if (possible.getValue(SIDES.get(direction)) == PipeSide.CONNECTED) {
					shape = Shapes.or(shape, ARMS.get(direction));
				}
			}
			map.put(possible, shape);
		}
		shapes = Map.copyOf(map);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		SIDES.values().forEach(builder::add);
	}

	/** Whether this pipe joins the block on that side: not switched off, and the pipe on the other side neither. */
	public static boolean joins(BlockState pipe, Direction side) {
		return pipe.getValue(SIDES.get(side)) != PipeSide.OFF;
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return connect(defaultBlockState(), context.getLevel(), context.getClickedPos());
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
		BlockState updated = connect(state, level, pos);
		if (updated != state) {
			level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
		}
	}

	/** Shows an arm on every side that is not off and has a pipe, box or client next to it. */
	private static BlockState connect(BlockState state, Level level, BlockPos pos) {
		for (Direction direction : Direction.values()) {
			EnumProperty<PipeSide> side = SIDES.get(direction);
			if (state.getValue(side) == PipeSide.OFF) {
				continue;
			}
			state = state.setValue(side, canJoin(level, pos.relative(direction), direction.getOpposite()) ? PipeSide.CONNECTED : PipeSide.NONE);
		}
		return state;
	}

	private static boolean canJoin(Level level, BlockPos pos, Direction facingUs) {
		if (!level.isLoaded(pos)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof DataPipeBlock) {
			return joins(state, facingUs);
		}
		if (state.getBlock() instanceof LuaBoxBlock) {
			return true;
		}
		BlockEntity entity = level.getBlockEntity(pos);
		return entity != null && BillboardApi.CLIENTS.find(level, pos, state, entity, facingUs) != null;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!player.isSecondaryUseActive()) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			Direction direction = clickedSide(hitResult, pos);
			EnumProperty<PipeSide> side = SIDES.get(direction);
			BlockState toggled = state.setValue(side, state.getValue(side) == PipeSide.OFF ? PipeSide.NONE : PipeSide.OFF);
			level.setBlock(pos, connect(toggled, level, pos), Block.UPDATE_ALL);
		}
		return InteractionResult.SUCCESS;
	}

	/** The arm under the cursor; on the centre, the face that was clicked. */
	private static Direction clickedSide(BlockHitResult hit, BlockPos pos) {
		Vec3 local = hit.getLocation().subtract(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
		double ax = Math.abs(local.x), ay = Math.abs(local.y), az = Math.abs(local.z);
		double max = Math.max(ax, Math.max(ay, az));
		if (max <= 2.5 / 16) {
			return hit.getDirection();
		}
		if (max == ax) {
			return local.x > 0 ? Direction.EAST : Direction.WEST;
		}
		if (max == ay) {
			return local.y > 0 ? Direction.UP : Direction.DOWN;
		}
		return local.z > 0 ? Direction.SOUTH : Direction.NORTH;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shapes.getOrDefault(state, CORE);
	}
}
