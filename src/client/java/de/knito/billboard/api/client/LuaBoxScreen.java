package de.knito.billboard.api.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.Blaze3D;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import de.knito.billboard.api.impl.box.LuaBoxMenu;
import de.knito.billboard.api.impl.net.LuaBoxActionPayload;
import de.knito.billboard.api.impl.net.LuaBoxStatePayload;
import de.knito.billboard.api.impl.net.BoxUploadPayload;

/**
 * The Lua Box screen: its files, its output lines, Restart and Stop. Files are dropped onto it from the
 * file manager: the program main.lua and up to 8 more files. There is no editor; programs are written in any
 * text editor. In single player the folder button and a click on a file open them on this computer. The Network
 * button swaps the file list for the machines the box reaches, where a click picks one to rename.
 */
public class LuaBoxScreen extends AbstractContainerScreen<LuaBoxMenu> {
	private static final int WIDTH = 256;
	private static final int HEIGHT = 222;
	private static final int FILES_Y = 18;
	private static final int FILE_ROW = 11;
	private static final int MAX_FILE_ROWS = 7;
	/** Machine rows in the Network view; the last row holds the name field. */
	private static final int NETWORK_ROWS = 6;
	private static final int OUTPUT_Y = 112;
	private static final int OUTPUT_LINES = 7;
	private static final int PANEL = 0xFFC6C6C6;
	private static final int DARK = 0xFF555555;
	private static final int LABEL = 0xFF404040;

	private LuaBoxStatePayload state;
	/** Our own message, such as an upload in progress or a file the client refused; cleared by the next state. */
	private Component note = Component.empty();
	private boolean noteError;
	/** Shows the machines of the network instead of the files. */
	private boolean showNetwork;
	/** The machine picked for renaming, as a block position, or null. */
	private Long selected;
	private EditBox nameBox;

	public LuaBoxScreen(LuaBoxMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
	}

	@Override
	protected void init() {
		super.init();
		boolean canEdit = state != null && state.canEdit();
		boolean canControl = state != null && state.canControl();
		Button restart = addRenderableWidget(Button.builder(Component.translatable("gui.billboard_api.box.restart"), b -> send(LuaBoxActionPayload.Action.RESTART, ""))
				.bounds(leftPos + 8, topPos + HEIGHT - 22, 70, 18).build());
		Button stop = addRenderableWidget(Button.builder(Component.translatable("gui.billboard_api.box.stop"), b -> send(LuaBoxActionPayload.Action.STOP, ""))
				.bounds(leftPos + 82, topPos + HEIGHT - 22, 70, 18).build());
		restart.active = canControl;
		stop.active = canControl;
		addRenderableWidget(Button.builder(Component.translatable(showNetwork ? "gui.billboard_api.box.files_view" : "gui.billboard_api.box.network_view"), b -> {
			showNetwork = !showNetwork;
			rebuildWidgets();
		}).bounds(leftPos + 60, topPos + 3, 58, 12).build());
		if (showNetwork) {
			initNetwork(canControl);
			return;
		}
		Path folder = localFolder();
		if (folder != null) {
			Button open = addRenderableWidget(Button.builder(Component.translatable("gui.billboard_api.box.folder"), b -> Blaze3D.openPath(folder))
					.bounds(leftPos + WIDTH - 66, topPos + FILES_Y - 3, 58, 12).build());
			open.setTooltip(Tooltip.create(Component.translatable("gui.billboard_api.box.folder_tooltip")));
		}
		if (state != null && canEdit) {
			List<LuaBoxStatePayload.FileEntry> files = state.files();
			for (int i = 0; i < Math.min(files.size(), MAX_FILE_ROWS); i++) {
				String name = files.get(i).name();
				Button remove = addRenderableWidget(Button.builder(Component.literal("x"), b -> send(LuaBoxActionPayload.Action.DELETE, name))
						.bounds(leftPos + WIDTH - 20, topPos + FILES_Y + 12 + i * FILE_ROW - 1, 11, 10).build());
				remove.setTooltip(Tooltip.create(Component.translatable("gui.billboard_api.box.remove", name)));
			}
		}
	}

	private void initNetwork(boolean canControl) {
		String old = nameBox != null ? nameBox.getValue() : "";
		nameBox = new EditBox(font, leftPos + 10, topPos + FILES_Y + 12 + NETWORK_ROWS * FILE_ROW, 168, 12, Component.translatable("gui.billboard_api.box.name"));
		nameBox.setMaxLength(32);
		nameBox.setValue(old);
		Button rename = Button.builder(Component.translatable("gui.billboard_api.box.rename"), b -> rename())
				.bounds(leftPos + WIDTH - 72, topPos + FILES_Y + 12 + NETWORK_ROWS * FILE_ROW, 62, 12).build();
		LuaBoxStatePayload.Client client = selectedClient();
		boolean editable = canControl && client != null && client.usable();
		nameBox.visible = editable;
		rename.visible = editable;
		addRenderableWidget(nameBox);
		addRenderableWidget(rename);
	}

	private LuaBoxStatePayload.Client selectedClient() {
		if (state == null || selected == null) {
			return null;
		}
		return state.network().clients().stream().filter(c -> c.pos() == selected).findFirst().orElse(null);
	}

	private void rename() {
		LuaBoxStatePayload.Client client = selectedClient();
		String name = nameBox.getValue().strip();
		if (client != null && !name.isEmpty() && !name.equals(client.name())) {
			ClientPlayNetworking.send(new LuaBoxActionPayload(LuaBoxActionPayload.Action.RENAME, name, client.pos()));
		}
	}

	/** The machine row under the mouse, or -1. */
	private int clientRowAt(double mouseX, double mouseY) {
		if (state == null || mouseX < leftPos + 8 || mouseX >= leftPos + WIDTH - 8) {
			return -1;
		}
		double y = mouseY - topPos - FILES_Y - 12;
		int row = y < 0 ? -1 : (int) (y / FILE_ROW);
		return row >= 0 && row < Math.min(state.network().clients().size(), NETWORK_ROWS) ? row : -1;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) {
			return super.keyPressed(event);
		}
		// While typing a name, letters like E must go into the box instead of closing the screen.
		if (showNetwork && nameBox != null && nameBox.isFocused()) {
			if (event.key() == InputConstants.KEY_RETURN) {
				rename();
				return true;
			}
			if (nameBox.keyPressed(event) || nameBox.canConsumeInput()) {
				return true;
			}
		}
		return super.keyPressed(event);
	}

	/** The box's folder if it is on this computer (single player), else null. */
	private Path localFolder() {
		if (state == null || state.folder().isEmpty() || !Minecraft.getInstance().hasSingleplayerServer()) {
			return null;
		}
		return Path.of(state.folder());
	}

	/** The file row under the mouse, or -1. */
	private int fileRowAt(double mouseX, double mouseY) {
		if (state == null || mouseX < leftPos + 8 || mouseX >= leftPos + WIDTH - 22) {
			return -1;
		}
		double y = mouseY - topPos - FILES_Y - 12;
		int row = y < 0 ? -1 : (int) (y / FILE_ROW);
		return row >= 0 && row < Math.min(state.files().size(), MAX_FILE_ROWS) ? row : -1;
	}

	/** A click on a file opens it with the program the system uses for it, such as the text editor for .lua. */
	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (showNetwork) {
			int clientRow = clientRowAt(event.x(), event.y());
			if (clientRow >= 0 && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
				LuaBoxStatePayload.Client client = state.network().clients().get(clientRow);
				selected = client.pos();
				if (nameBox != null) {
					nameBox.setValue(client.name());
				}
				rebuildWidgets();
				if (nameBox != null && nameBox.visible) {
					setFocused(nameBox);
				}
				return true;
			}
			return super.mouseClicked(event, doubleClick);
		}
		Path folder = localFolder();
		int row = fileRowAt(event.x(), event.y());
		if (folder != null && row >= 0 && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			Path file = folder.resolve(state.files().get(row).name());
			if (Files.isRegularFile(file)) {
				Blaze3D.openPath(file);
				note(Component.translatable("gui.billboard_api.box.opened", file.getFileName().toString()), false);
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	/** New state from the server; the file buttons are rebuilt to match. */
	public void setState(LuaBoxStatePayload newState) {
		boolean filesChanged = state == null || state.canEdit() != newState.canEdit() || state.canControl() != newState.canControl()
				|| !state.files().equals(newState.files())
				|| !state.folder().equals(newState.folder())
				|| showNetwork && !state.network().clients().equals(newState.network().clients());
		state = newState;
		note = Component.empty();
		if (filesChanged) {
			rebuildWidgets();
		}
	}

	private void send(LuaBoxActionPayload.Action action, String name) {
		ClientPlayNetworking.send(new LuaBoxActionPayload(action, name));
	}

	private void note(Component message, boolean error) {
		note = message;
		noteError = error;
	}

	/** Files dropped from the file manager: checked here first, then sent in pieces. The server checks again. */
	@Override
	public void onFilesDrop(List<Path> paths) {
		if (state == null) {
			return;
		}
		if (!state.canEdit()) {
			note(Component.translatable("gui.billboard_api.box.not_yours"), true);
			return;
		}
		List<String> types = Arrays.asList(state.limits().types().split(","));
		List<Path> send = new ArrayList<>();
		Path main = null;
		List<String> ignored = new ArrayList<>();
		for (Path path : paths) {
			String name = path.getFileName().toString();
			int dot = name.lastIndexOf('.');
			String type = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
			if (!Files.isRegularFile(path)) {
				ignored.add(name);
			} else if (name.equals("main.lua")) {
				main = path;
			} else if (types.contains(type)) {
				send.add(path);
			} else {
				ignored.add(name);
			}
		}
		if (main != null) {
			send.add(main); // the program last, so it starts with its files already there
		}
		String problem = null;
		for (Path path : send) {
			String name = path.getFileName().toString();
			long limit = (name.toLowerCase(Locale.ROOT).endsWith(".lua") ? state.limits().maxLuaKb() : state.limits().maxFileKb()) * 1024L;
			try {
				byte[] data = Files.readAllBytes(path);
				if (data.length > limit) {
					problem = name + " > " + limit / 1024 + " KB";
					continue;
				}
				upload(name, data);
			} catch (IOException e) {
				problem = name + ": " + e.getMessage();
			}
		}
		if (problem != null) {
			note(Component.translatable("gui.billboard_api.box.not_sent", problem), true);
		} else if (!ignored.isEmpty()) {
			note(Component.translatable("gui.billboard_api.box.ignored", String.join(", ", ignored)), true);
		} else if (!send.isEmpty()) {
			note(Component.translatable("gui.billboard_api.box.sending", send.size()), false);
		}
	}

	private static void upload(String name, byte[] data) {
		for (int offset = 0; offset < data.length; offset += BoxUploadPayload.CHUNK) {
			int length = Math.min(BoxUploadPayload.CHUNK, data.length - offset);
			ClientPlayNetworking.send(new BoxUploadPayload(name, data.length, offset, Arrays.copyOfRange(data, offset, offset + length)));
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		int x = leftPos;
		int y = topPos;
		graphics.fill(x, y, x + WIDTH, y + HEIGHT, PANEL);
		graphics.outline(x, y, WIDTH, HEIGHT, DARK);
		// Files and output sit in sunken fields.
		graphics.fill(x + 7, y + FILES_Y + 10, x + WIDTH - 7, y + FILES_Y + 12 + MAX_FILE_ROWS * FILE_ROW, 0xFFE8E8E8);
		graphics.outline(x + 7, y + FILES_Y + 10, WIDTH - 14, MAX_FILE_ROWS * FILE_ROW + 2, DARK);
		graphics.fill(x + 7, y + OUTPUT_Y + 10, x + WIDTH - 7, y + OUTPUT_Y + 12 + OUTPUT_LINES * 10, 0xFF1E1E1E);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
		graphics.text(font, title, 8, 6, LABEL, false);
		if (state == null) {
			return;
		}
		int statusColor = switch (state.status()) {
			case "Running" -> 0xFF207020;
			case "Error" -> 0xFFA02020;
			default -> 0xFF806000;
		};
		graphics.text(font, state.status(), WIDTH - 8 - font.width(state.status()), 6, statusColor, false);

		if (showNetwork) {
			extractNetwork(graphics);
		} else {
			extractFiles(graphics);
		}

		graphics.text(font, Component.translatable("gui.billboard_api.box.output"), 8, OUTPUT_Y, LABEL, false);
		List<LuaBoxStatePayload.Line> lines = state.lines();
		int first = Math.max(0, lines.size() - OUTPUT_LINES);
		for (int i = first; i < lines.size(); i++) {
			LuaBoxStatePayload.Line line = lines.get(i);
			graphics.text(font, fit(line.text(), WIDTH - 22), 11, OUTPUT_Y + 13 + (i - first) * 10, line.error() ? 0xFFFF6060 : 0xFFE0E0E0, false);
		}

		if (!note.getString().isEmpty()) {
			graphics.text(font, fit(note.getString(), WIDTH - 164), 158, HEIGHT - 17, noteError ? 0xFFA02020 : 0xFF207020, false);
		}
	}

	private void extractFiles(GuiGraphicsExtractor graphics) {
		String filesLabel = Component.translatable("gui.billboard_api.box.files", state.limits().maxFiles()).getString();
		graphics.text(font, fit(filesLabel, localFolder() != null ? WIDTH - 78 : WIDTH - 16), 8, FILES_Y, LABEL, false);
		List<LuaBoxStatePayload.FileEntry> files = state.files();
		if (files.isEmpty()) {
			graphics.text(font, Component.translatable("gui.billboard_api.box.drop_hint"), 12, FILES_Y + 13, 0xFF707070, false);
		}
		for (int i = 0; i < Math.min(files.size(), MAX_FILE_ROWS); i++) {
			LuaBoxStatePayload.FileEntry file = files.get(i);
			int rowY = FILES_Y + 13 + i * FILE_ROW;
			boolean program = file.name().toLowerCase(Locale.ROOT).endsWith(".lua");
			boolean main = file.name().equals("main.lua");
			String size = file.size() < 1024 ? file.size() + " B" : (file.size() + 512) / 1024 + " KB";
			graphics.text(font, fit(file.name(), 170), 12, rowY, main ? 0xFF1030C0 : program ? 0xFF2050A0 : LABEL, false);
			graphics.text(font, size, WIDTH - 26 - font.width(size), rowY, 0xFF707070, false);
		}
	}

	private void extractNetwork(GuiGraphicsExtractor graphics) {
		List<LuaBoxStatePayload.Client> clients = state.network().clients();
		graphics.text(font, fit(Component.translatable("gui.billboard_api.box.network", clients.size()).getString(), WIDTH - 16), 8, FILES_Y, LABEL, false);
		int rowY = FILES_Y + 13;
		if (!state.network().problem().isEmpty()) {
			graphics.text(font, fit(state.network().problem(), WIDTH - 24), 12, rowY, 0xFFA02020, false);
			return;
		}
		if (clients.isEmpty()) {
			graphics.text(font, fit(Component.translatable("gui.billboard_api.box.no_clients").getString(), WIDTH - 24), 12, rowY, 0xFF707070, false);
		}
		for (int i = 0; i < Math.min(clients.size(), NETWORK_ROWS); i++) {
			LuaBoxStatePayload.Client client = clients.get(i);
			int y = rowY + i * FILE_ROW;
			boolean picked = selected != null && selected == client.pos();
			int color = !client.usable() ? 0xFF909090 : picked ? 0xFF1030C0 : LABEL;
			graphics.text(font, fit((picked ? "> " : "") + client.name(), 110), 12, y, color, false);
			String type = client.usable() ? client.type() : Component.translatable("gui.billboard_api.box.other_owner", client.type()).getString();
			String shown = fit(type, WIDTH - 140);
			graphics.text(font, shown, WIDTH - 12 - font.width(shown), y, 0xFF707070, false);
		}
		if (clients.size() > NETWORK_ROWS) {
			String more = "+" + (clients.size() - NETWORK_ROWS);
			graphics.text(font, more, WIDTH - 12 - font.width(more), FILES_Y + 13 + NETWORK_ROWS * FILE_ROW, 0xFF707070, false);
		}
	}

	/** Cuts a text to the width, ending in "..." if it was too long. */
	private String fit(String text, int width) {
		if (font.width(text) <= width) {
			return text;
		}
		return font.plainSubstrByWidth(text, width - font.width("...")).stripTrailing() + "...";
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		// The full line under the mouse, for output lines and notes cut short.
		if (state != null && mouseX >= leftPos + 7 && mouseX < leftPos + WIDTH - 7) {
			int row = (mouseY - topPos - OUTPUT_Y - 13) / 10;
			List<LuaBoxStatePayload.Line> lines = state.lines();
			int first = Math.max(0, lines.size() - OUTPUT_LINES);
			if (mouseY >= topPos + OUTPUT_Y + 13 && row < OUTPUT_LINES && first + row < lines.size()) {
				String text = lines.get(first + row).text();
				if (font.width(text) > WIDTH - 22) {
					graphics.setTooltipForNextFrame(Component.literal(text), mouseX, mouseY);
				}
			}
		}
		if (!showNetwork && localFolder() != null && fileRowAt(mouseX, mouseY) >= 0) {
			graphics.setTooltipForNextFrame(Component.translatable("gui.billboard_api.box.open_file"), mouseX, mouseY);
		}
		if (!note.getString().isEmpty() && mouseX >= leftPos + 158 && mouseY >= topPos + HEIGHT - 18 && mouseY < topPos + HEIGHT - 8) {
			graphics.setTooltipForNextFrame(note, mouseX, mouseY);
		}
	}
}
