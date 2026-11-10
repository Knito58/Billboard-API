package de.knito.billboard.api.impl.port;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;

import de.knito.billboard.api.LuaCaller;
import de.knito.billboard.api.LuaException;
import de.knito.billboard.api.impl.ModBlockEntities;

/**
 * The program that claimed the port; nothing else, and nothing saved. Only the program that holds a port may move
 * items through it, so two programs never fight over one chest. Reading needs no claim.
 */
public class InventoryPortBlockEntity extends BlockEntity {
	/** The program that claimed the port, or null. Not saved: a claim ends with the program. */
	private @Nullable LuaCaller holder;

	public InventoryPortBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.INVENTORY_PORT, pos, state);
	}

	/** The program holding the port, or null when nobody does (a holder that has ended no longer counts). */
	public @Nullable LuaCaller holder() {
		return holder != null && holder.isActive() ? holder : null;
	}

	/** Claims the port for {@code caller}. Returns null when the caller holds it now, or the program that holds it. */
	public @Nullable LuaCaller claim(LuaCaller caller) {
		LuaCaller current = holder();
		if (current != null && !current.equals(caller)) {
			return current;
		}
		holder = caller;
		return null;
	}

	/** Lets go of the port; false if {@code caller} does not hold it. */
	public boolean release(@Nullable LuaCaller caller) {
		if (holder() == null || !holder.equals(caller)) {
			return false;
		}
		holder = null;
		return true;
	}

	/** An error unless {@code caller} holds the port. */
	public void requireClaim(@Nullable LuaCaller caller) throws LuaException {
		LuaCaller current = holder();
		if (current == null || !current.equals(caller)) {
			throw new LuaException(current == null ? "claim the port first, with claim()" : "the port is claimed by " + current.describe());
		}
	}

	/** The position of what the given side touches. */
	public BlockPos neighbour(Direction side) {
		return worldPosition.relative(side);
	}

	/**
	 * The item storage next to that side, or null if there is none. Never loads a chunk: a neighbour in an unloaded
	 * chunk is reported as an error.
	 */
	public @Nullable Storage<ItemVariant> storage(Direction side) throws LuaException {
		if (!(level instanceof ServerLevel serverLevel)) {
			return null;
		}
		BlockPos pos = neighbour(side);
		if (!serverLevel.isLoaded(pos)) {
			throw new LuaException("the " + side.getSerializedName() + " side is in an unloaded chunk");
		}
		return ItemStorage.SIDED.find(serverLevel, pos, side.getOpposite());
	}

	/** Every tick: a claim whose program has ended, been stopped or lost its box is dropped. */
	public static void serverTick(Level level, BlockPos pos, BlockState state, InventoryPortBlockEntity port) {
		if (port.holder != null && !port.holder.isActive()) {
			port.holder = null;
		}
	}
}
