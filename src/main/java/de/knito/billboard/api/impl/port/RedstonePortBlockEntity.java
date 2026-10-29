package de.knito.billboard.api.impl.port;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.fabricmc.fabric.api.blockgetter.v2.RenderDataBlockEntity;

import de.knito.billboard.api.LuaCaller;
import de.knito.billboard.api.LuaException;
import de.knito.billboard.api.impl.ModBlockEntities;

/**
 * The signal of each side, and the program that set it. Signals are not saved: a port gives nothing until a
 * program says so, and it lets go of a side when that program ends, is stopped, or its box is gone, so a piston
 * never stays out after the program that pushed it. What is saved is the block it is camouflaged as, if any.
 */
public class RedstonePortBlockEntity extends BlockEntity implements RenderDataBlockEntity {
	public static final int MAX_POWER = 15;

	private final int[] power = new int[Direction.values().length];
	private final @Nullable LuaCaller[] driver = new LuaCaller[Direction.values().length];

	/** The block whose look the port wears, or null for its own. Only the look: nothing else is copied. */
	private @Nullable BlockState camouflage;

	public RedstonePortBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.REDSTONE_PORT, pos, state);
	}

	public @Nullable BlockState camouflage() {
		return camouflage;
	}

	/** Server side: sets or, with null, clears the camouflage, and sends it to the players who see the port. */
	public void setCamouflage(@Nullable BlockState look) {
		if (look == camouflage) {
			return;
		}
		camouflage = look;
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
		}
	}

	/** What the client's model reads to draw the port. */
	@Override
	public @Nullable Object getRenderData() {
		return camouflage;
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (camouflage != null) {
			output.store("camouflage", BlockState.CODEC, camouflage);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		BlockState look = input.read("camouflage", BlockState.CODEC).orElse(null);
		boolean changed = look != camouflage;
		camouflage = look;
		if (changed && level != null && level.isClientSide()) {
			// The packet that carries the camouflage arrives after the block was first drawn.
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
		}
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveCustomOnly(registries);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	public int output(Direction side) {
		return power[side.ordinal()];
	}

	/**
	 * Sets one side for the program {@code caller}; a level of 0 lets go of it. The first program to drive a side
	 * owns it until it lets go or ends; another program gets an error instead of silently taking it over.
	 */
	public void setOutput(Direction side, int level, @Nullable LuaCaller caller) throws LuaException {
		int index = side.ordinal();
		LuaCaller holder = driver[index];
		if (holder != null && holder.isActive() && !holder.equals(caller)) {
			throw new LuaException("side \"" + side.getSerializedName() + "\" is driven by " + holder.describe());
		}
		boolean changed = power[index] != level;
		power[index] = level;
		driver[index] = level == 0 ? null : caller;
		if (changed) {
			changed();
		}
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, RedstonePortBlockEntity port) {
		boolean changed = false;
		for (int i = 0; i < port.power.length; i++) {
			LuaCaller caller = port.driver[i];
			if (port.power[i] > 0 && (caller == null || !caller.isActive())) {
				port.power[i] = 0;
				port.driver[i] = null;
				changed = true;
			}
		}
		if (changed) {
			port.changed();
		}
	}

	private void changed() {
		if (level != null && !level.isClientSide()) {
			level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
		}
	}
}
