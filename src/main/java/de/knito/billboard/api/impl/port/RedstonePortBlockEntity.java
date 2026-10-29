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
 * The signal of each side, and the program that claimed the port. Only that program sets signals. Nothing of it
 * is saved: a port gives nothing until a program claims it and says so, and it lets go of everything when that
 * program ends, is stopped, or its box is gone, so a piston never stays out after the program that pushed it.
 * What is saved is the block it is camouflaged as, if any.
 */
public class RedstonePortBlockEntity extends BlockEntity implements RenderDataBlockEntity {
	public static final int MAX_POWER = 15;

	private final int[] power = new int[Direction.values().length];
	/** The program that claimed the port, or null. Not saved: a claim ends with the program. */
	private @Nullable LuaCaller holder;

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

	/** The program holding the port, or null when nobody does (a holder that has ended no longer counts). */
	public @Nullable LuaCaller holder() {
		return holder != null && holder.isActive() ? holder : null;
	}

	/** Claims the port for {@code caller}. Another program that holds it makes this an error; claiming twice is fine. */
	public void claim(@Nullable LuaCaller caller) throws LuaException {
		if (caller == null) {
			throw new LuaException("no program to claim the port for");
		}
		LuaCaller current = holder();
		if (current != null && !current.equals(caller)) {
			throw new LuaException("the port is claimed by " + current.describe());
		}
		holder = caller;
	}

	/** Lets go of the port and of all its signals. Nothing happens if {@code caller} does not hold it. */
	public void release(@Nullable LuaCaller caller) {
		if (holder != null && holder.equals(caller)) {
			letGo();
		}
	}

	/** Sets one side; only the program that claimed the port may. A level of 0 switches the side off. */
	public void setOutput(Direction side, int level, @Nullable LuaCaller caller) throws LuaException {
		LuaCaller current = holder();
		if (current == null || !current.equals(caller)) {
			throw new LuaException(current == null ? "claim the port first, with claim()" : "the port is claimed by " + current.describe());
		}
		int index = side.ordinal();
		if (power[index] != level) {
			power[index] = level;
			changed();
		}
	}

	/** Every tick: a claim whose program has ended, been stopped or lost its box is dropped with its signals. */
	public static void serverTick(Level level, BlockPos pos, BlockState state, RedstonePortBlockEntity port) {
		if (port.holder != null && !port.holder.isActive()) {
			port.letGo();
		}
	}

	private void letGo() {
		holder = null;
		boolean any = false;
		for (int i = 0; i < power.length; i++) {
			any |= power[i] != 0;
			power[i] = 0;
		}
		if (any) {
			changed();
		}
	}

	private void changed() {
		if (level != null && !level.isClientSide()) {
			level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
		}
	}
}
