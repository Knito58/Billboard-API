package de.knito.billboard.api.impl.box;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;

import de.knito.billboard.api.impl.ApiConfig;
import de.knito.billboard.api.impl.ModBlockEntities;
import de.knito.billboard.api.impl.net.LuaBoxStatePayload;

/**
 * One Lua Box: the id of its file folder, its owner, and the program running now. The program starts when
 * the box is loaded and stops when it is unloaded; it keeps nothing between runs.
 */
public class LuaBoxBlockEntity extends BlockEntity implements ExtendedMenuProvider<LuaBoxMenu.OpenData> {
	/** Output lines kept for the screen and the chat summary. */
	public static final int MAX_LINES = 50;
	private static final int MAX_LINE_LENGTH = 200;

	public record Line(String text, boolean error) {
	}

	private String id;
	private UUID owner;
	/** Set by Stop; the program stays off, also after a restart of the world, until Restart. */
	private boolean halted;
	private BoxRunner runner;
	/** True once this load tried to start the program, so a broken program is not compiled every tick. */
	private boolean started;
	private final Deque<Line> lines = new ArrayDeque<>();
	/** Counts every change an open screen should see: output, state, files. */
	private int version;

	public LuaBoxBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.LUA_BOX, pos, state);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, LuaBoxBlockEntity box) {
		if (!box.started) {
			box.started = true;
			if (!box.halted) {
				box.start();
			}
		}
		if (box.runner != null) {
			box.runner.tick(level.getGameTime());
		}
	}

	/** Time of day for os.time: overworld clock ticks, 0 = 6:00 in the morning. */
	static long dayTime(ServerLevel level) {
		return level.getOverworldClockTime();
	}

	// ---- program control ----

	/** Starts the program from the top, stopping a running one first. */
	public void restart() {
		halted = false;
		setChanged();
		stopRunner();
		start();
	}

	/** The hand brake. The program stays off until Restart. */
	public void stop() {
		halted = true;
		setChanged();
		if (runner != null && runner.isAlive()) {
			runner.stop();
		} else {
			print("Stopped.", false);
		}
		runner = null;
		runnerChanged();
	}

	private void stopRunner() {
		if (runner != null && runner.isAlive()) {
			runner.stop();
		}
		runner = null;
	}

	private void start() {
		started = true;
		runner = null;
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		if (!ApiConfig.boxesEnabled()) {
			print("Lua Boxes are switched off on this server (boxes_enabled in billboard_api.properties).", true);
			runnerChanged();
			return;
		}
		Path folder = folder(serverLevel.getServer());
		String problem = BoxStorage.problem(folder);
		if (problem != null) {
			print(problem, true);
			runnerChanged();
			return;
		}
		Path program = folder.resolve(BoxStorage.MAIN);
		try {
			runner = BoxRunner.start(this, BoxStorage.MAIN, Files.readAllBytes(program));
			print("Started " + BoxStorage.MAIN + ".", false);
		} catch (IOException e) {
			print("Could not read " + BoxStorage.MAIN + ".", true);
		} catch (Exception e) {
			// Compile errors: the message already names the file and line.
			print(e.getMessage(), true);
		}
		runnerChanged();
	}

	public BoxState boxState() {
		if (runner != null && runner.isAlive()) {
			return BoxState.RUNNING;
		}
		Line last = lines.peekLast();
		return last != null && last.error() ? BoxState.ERROR : BoxState.STOPPED;
	}

	/** Shows the program's state on the block, for the light on its label. */
	void runnerChanged() {
		version++;
		if (level == null || isRemoved()) {
			return;
		}
		BlockState state = getBlockState();
		BoxState now = boxState();
		if (state.hasProperty(LuaBoxBlock.STATE) && state.getValue(LuaBoxBlock.STATE) != now) {
			level.setBlock(worldPosition, state.setValue(LuaBoxBlock.STATE, now), Block.UPDATE_CLIENTS);
		}
	}

	void print(String text, boolean error) {
		version++;
		for (String line : text.split("\n", -1)) {
			lines.addLast(new Line(line.length() > MAX_LINE_LENGTH ? line.substring(0, MAX_LINE_LENGTH) + "..." : line, error));
		}
		while (lines.size() > MAX_LINES) {
			lines.removeFirst();
		}
	}

	public List<Line> lines() {
		return new ArrayList<>(lines);
	}

	public String statusText() {
		if (runner != null) {
			return switch (runner.status()) {
				case RUNNING, SLEEPING -> "Running";
				case ENDED -> "Ended";
				case FAILED -> "Error";
				case STOPPED -> "Stopped";
			};
		}
		return boxState() == BoxState.ERROR ? "Error" : "Stopped";
	}

	// ---- files ----

	/** This box's folder; gives the box an id first if it has none (for instance after /setblock). */
	public Path folder(MinecraftServer server) {
		if (id == null || BoxStorage.folder(server, id) == null) {
			id = BoxStorage.newId(server);
			setChanged();
		}
		return BoxStorage.folder(server, id);
	}

	/** This box's folder, or null on the client. */
	Path folder() {
		return level instanceof ServerLevel serverLevel ? folder(serverLevel.getServer()) : null;
	}

	/** A file of this box by its name, or null if there is none. */
	Path file(String name) {
		return level instanceof ServerLevel serverLevel ? BoxStorage.file(folder(serverLevel.getServer()), name) : null;
	}

	void onServerThread(Runnable task) {
		if (level instanceof ServerLevel serverLevel) {
			serverLevel.getServer().execute(task);
		}
	}

	/** Called when a player places the box: a fresh id, so a copied box never shares files with the original. */
	void placedBy(UUID player) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		Path old = id == null ? null : BoxStorage.folder(serverLevel.getServer(), id);
		id = BoxStorage.newId(serverLevel.getServer());
		owner = player;
		BoxStorage.copy(old, BoxStorage.folder(serverLevel.getServer(), id));
		setChanged();
	}

	public UUID owner() {
		return owner;
	}

	public int version() {
		return version;
	}

	/** Whether this player may give the box files or remove them: its owner, or an operator. */
	public boolean canEdit(ServerPlayer player) {
		boolean op = player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
		if (ApiConfig.boxesOpsOnly()) {
			return op;
		}
		return op || player.getUUID().equals(owner);
	}

	/** Whether this player may press Restart and Stop: everybody, unless the server allows only operators. */
	public boolean canControl(ServerPlayer player) {
		return !ApiConfig.boxesOpsOnly() || player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	/** Everything the screen shows, for this player. */
	public LuaBoxStatePayload statePayload(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Path folder = folder(server);
		List<LuaBoxStatePayload.FileEntry> files = new ArrayList<>();
		for (Path file : BoxStorage.files(folder)) {
			long size;
			try {
				size = Files.size(file);
			} catch (IOException e) {
				size = 0;
			}
			files.add(new LuaBoxStatePayload.FileEntry(file.getFileName().toString(), (int) Math.min(Integer.MAX_VALUE, size)));
		}
		List<LuaBoxStatePayload.Line> shown = lines.stream().map(line -> new LuaBoxStatePayload.Line(line.text(), line.error())).toList();
		boolean canEdit = canEdit(player);
		boolean canControl = canControl(player);
		// A server's paths mean nothing on the player's computer and are none of their business.
		String localFolder = "";
		if (canEdit && !server.isDedicatedServer()) {
			try {
				Files.createDirectories(folder); // so the Folder button has something to open
				localFolder = folder.toString();
			} catch (IOException e) {
				// no button then
			}
		}
		LuaBoxStatePayload.Limits limits = new LuaBoxStatePayload.Limits(ApiConfig.boxMaxLuaKb(), ApiConfig.boxMaxFiles(),
				ApiConfig.boxMaxFileKb(), String.join(",", ApiConfig.boxFileTypes()));
		return new LuaBoxStatePayload(statusText(), canEdit, canControl, files, shown, limits, localFolder);
	}

	/**
	 * Stores a file a player dropped onto the screen, already checked by {@link BoxUploads}. A new main.lua replaces
	 * the program and starts it. Any other file replaces the file of the same name; a running program sees it the next
	 * time it reads it, and a program that ended (perhaps missing this file) starts again.
	 */
	void storeFile(String name, byte[] data) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		Path folder = folder(serverLevel.getServer());
		try {
			Files.createDirectories(folder);
			Files.write(folder.resolve(name), data);
		} catch (IOException e) {
			print("Could not save " + name + ".", true);
			return;
		}
		print("Got " + name + " (" + Math.max(1, data.length / 1024) + " KB).", false);
		if (BoxStorage.isMain(name) || !halted && boxState() != BoxState.RUNNING) {
			restart();
		}
	}

	void deleteFile(String name) {
		if (!(level instanceof ServerLevel serverLevel) || !BoxStorage.isValidName(name)) {
			return;
		}
		try {
			if (Files.deleteIfExists(folder(serverLevel.getServer()).resolve(name))) {
				print("Removed " + name + ".", false);
			}
		} catch (IOException e) {
			print("Could not remove " + name + ".", true);
		}
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.billboard_api.lua_box");
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
		return new LuaBoxMenu(containerId, inventory, this);
	}

	@Override
	public LuaBoxMenu.OpenData getScreenOpeningData(ServerPlayer player) {
		return new LuaBoxMenu.OpenData(worldPosition);
	}

	// ---- life cycle ----

	@Override
	public void setRemoved() {
		// Unloaded or broken: the program ends. Removed first, so nothing touches the block any more.
		super.setRemoved();
		if (runner != null && runner.isAlive()) {
			runner.stop();
		}
		runner = null;
		started = false;
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		// The box is broken: it drops blank, and its files go with it. They never were items in the world.
		if (level instanceof ServerLevel serverLevel && id != null) {
			BoxStorage.delete(BoxStorage.folder(serverLevel.getServer(), id));
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		id = input.getStringOr("script_id", null);
		owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
		halted = input.getBooleanOr("halted", false);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (id != null) {
			output.putString("script_id", id);
		}
		if (owner != null) {
			output.store("owner", UUIDUtil.CODEC, owner);
		}
		output.putBoolean("halted", halted);
	}
}
