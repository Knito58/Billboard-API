package de.knito.billboard.api.impl.box;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import de.knito.billboard.api.impl.ModMenus;

/** The Lua Box screen: no slots, only state, files and output lines, sent by {@link LuaBoxBlockEntity#statePayload}. */
public class LuaBoxMenu extends AbstractContainerMenu {
	/** Sent to the client when the screen opens. */
	public record OpenData(BlockPos pos) {
		public static final StreamCodec<ByteBuf, OpenData> STREAM_CODEC = BlockPos.STREAM_CODEC.map(OpenData::new, OpenData::pos);
	}

	/** Only set on the server. */
	private final LuaBoxBlockEntity box;
	private final Player player;
	/** The box's change counter at the last state sent to this player; -1 = nothing sent yet. */
	private int sentVersion = -1;

	/** Client side. */
	public LuaBoxMenu(int containerId, Inventory inventory, OpenData openData) {
		this(containerId, inventory, (LuaBoxBlockEntity) null);
	}

	/** Server side. */
	public LuaBoxMenu(int containerId, Inventory inventory, LuaBoxBlockEntity box) {
		super(ModMenus.LUA_BOX, containerId);
		this.box = box;
		this.player = inventory.player;
	}

	public LuaBoxBlockEntity box() {
		return box;
	}

	/** Called every tick on the server while the screen is open: sends the state again when it changed. */
	@Override
	public void broadcastChanges() {
		super.broadcastChanges();
		if (box != null && player instanceof ServerPlayer serverPlayer && box.version() != sentVersion) {
			sentVersion = box.version();
			ServerPlayNetworking.send(serverPlayer, box.statePayload(serverPlayer));
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return box == null || Container.stillValidBlockEntity(box, player);
	}
}
