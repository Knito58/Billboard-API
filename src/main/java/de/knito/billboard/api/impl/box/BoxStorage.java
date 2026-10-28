package de.knito.billboard.api.impl.box;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import de.knito.billboard.api.impl.ApiConfig;
import de.knito.billboard.api.impl.BillboardApiMod;

/**
 * The files of the Lua Boxes: {@code <world>/billboard_api/boxes/<id>/} holds the program main.lua and up to
 * {@link ApiConfig#boxMaxFiles()} further files. The block itself keeps only the id.
 */
public final class BoxStorage {
	private static final Pattern ID = Pattern.compile("[0-9a-f]{12}");
	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_.-]{0,63}");
	/** The program the box runs. Other .lua files are modules for require(). */
	public static final String MAIN = "main.lua";
	private static final List<String> IMAGE_TYPES = List.of("png", "jpg", "jpeg", "gif", "bmp");

	private BoxStorage() {
	}

	public static Path root(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("billboard_api").resolve("boxes").toAbsolutePath().normalize();
	}

	/** Where Billboard Engine 1.x kept the files of its Script Boxes. */
	static Path oldRoot(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("billboard").resolve("scripts").toAbsolutePath().normalize();
	}

	/**
	 * The folder of a box id, or null for an id that is not ours (so a crafted id can never point elsewhere).
	 * A Script Box of Billboard Engine 1.x keeps its id when it becomes a Lua Box; its old folder moves here then.
	 */
	public static Path folder(MinecraftServer server, String id) {
		if (id == null || !ID.matcher(id).matches()) {
			return null;
		}
		Path folder = root(server).resolve(id);
		Path old = oldRoot(server).resolve(id);
		if (!Files.exists(folder) && Files.isDirectory(old)) {
			moveOld(old, folder);
		}
		return folder;
	}

	/** Moves a Script Box folder over and names its one program main.lua, the name a Lua Box runs. */
	private static void moveOld(Path old, Path folder) {
		try {
			Files.createDirectories(folder.getParent());
			Files.move(old, folder);
			List<Path> programs = files(folder).stream().filter(p -> isLua(p.getFileName().toString())).toList();
			if (programs.size() == 1 && !isMain(programs.getFirst().getFileName().toString())) {
				Files.move(programs.getFirst(), folder.resolve(MAIN));
			}
			BillboardApiMod.LOGGER.info("Moved Script Box files {} to {}", old, folder);
		} catch (IOException e) {
			BillboardApiMod.LOGGER.warn("Could not move Script Box files {} to {}", old, folder, e);
		}
	}

	public static String newId(MinecraftServer server) {
		while (true) {
			String id = String.format(Locale.ROOT, "%012x", java.util.concurrent.ThreadLocalRandom.current().nextLong() & 0xFFFFFFFFFFFFL);
			if (!Files.exists(root(server).resolve(id)) && !Files.exists(oldRoot(server).resolve(id))) {
				return id;
			}
		}
	}

	/** A file name a player may use: letters, digits, _ - . and no folders. */
	public static boolean isValidName(String name) {
		return NAME.matcher(name).matches() && !name.contains("..");
	}

	public static boolean isMain(String name) {
		return MAIN.equals(name);
	}

	public static boolean isLua(String name) {
		return extension(name).equals("lua");
	}

	public static boolean isImage(String name) {
		return IMAGE_TYPES.contains(extension(name));
	}

	/** Whether the server allows this kind of file in a box (box_file_types); main.lua always. */
	public static boolean isAllowedType(String name) {
		return isMain(name) || ApiConfig.boxFileTypes().contains(extension(name));
	}

	/** The part after the last dot, in lower case; "" if there is none. */
	public static String extension(String name) {
		int dot = name.lastIndexOf('.');
		return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	/** All files in the folder, sorted by name; empty if there is no folder yet. */
	public static List<Path> files(Path folder) {
		if (folder == null || !Files.isDirectory(folder)) {
			return List.of();
		}
		try (Stream<Path> stream = Files.list(folder)) {
			return stream.filter(Files::isRegularFile).sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
		} catch (IOException e) {
			BillboardApiMod.LOGGER.warn("Could not list {}", folder, e);
			return List.of();
		}
	}

	/** All files but main.lua: these count against box_max_files. */
	public static List<Path> extraFiles(Path folder) {
		return files(folder).stream().filter(p -> !isMain(p.getFileName().toString())).toList();
	}

	/** A file of the box by its name, or null if there is no such file or the name is not one a player may use. */
	public static Path file(Path folder, String name) {
		if (folder == null || name == null || !isValidName(name)) {
			return null;
		}
		Path file = folder.resolve(name);
		return Files.isRegularFile(file) ? file : null;
	}

	/**
	 * Why the folder as it is now may not run, or null if it may. Files copied straight into the folder skip the
	 * checks of an upload, so the box checks again at every start.
	 */
	public static String problem(Path folder) {
		if (file(folder, MAIN) == null) {
			boolean otherLua = files(folder).stream().anyMatch(p -> isLua(p.getFileName().toString()));
			return otherLua ? "No main.lua. Name the program main.lua; other .lua files are modules for require()."
					: "No program yet. Drop a main.lua onto this screen (or put it into " + folder + ").";
		}
		List<Path> extra = extraFiles(folder);
		if (extra.size() > ApiConfig.boxMaxFiles()) {
			return extra.size() + " files besides main.lua, at most " + ApiConfig.boxMaxFiles() + " are allowed. Remove some.";
		}
		for (Path path : files(folder)) {
			String name = path.getFileName().toString();
			String why = sizeProblem(name, size(path));
			if (!isValidName(name)) {
				why = name + ": please use only letters, digits, _ - and . in file names.";
			} else if (!isAllowedType(name)) {
				why = name + ": ." + extension(name) + " files are not allowed in a Lua Box on this server.";
			}
			if (why != null) {
				return why;
			}
		}
		return null;
	}

	/** Why a file of this size is too large, or null. */
	public static String sizeProblem(String name, long size) {
		long limitKb = isLua(name) ? ApiConfig.boxMaxLuaKb() : ApiConfig.boxMaxFileKb();
		return size > limitKb * 1024 ? name + " is larger than " + limitKb + " KB." : null;
	}

	public static long size(Path file) {
		try {
			return Files.size(file);
		} catch (IOException e) {
			return 0;
		}
	}

	public static void delete(Path folder) {
		if (folder == null || !Files.isDirectory(folder)) {
			return;
		}
		try {
			for (Path file : files(folder)) {
				Files.deleteIfExists(file);
			}
			Files.deleteIfExists(folder);
		} catch (IOException e) {
			BillboardApiMod.LOGGER.warn("Could not delete {}", folder, e);
		}
	}

	/** Copies the files of one box to a new one, for a box copied with creative pick-block. */
	public static void copy(Path from, Path to) {
		if (from == null || to == null || !Files.isDirectory(from)) {
			return;
		}
		try {
			Files.createDirectories(to);
			for (Path file : files(from)) {
				Files.copy(file, to.resolve(file.getFileName()));
			}
		} catch (IOException e) {
			BillboardApiMod.LOGGER.warn("Could not copy {} to {}", from, to, e);
		}
	}
}
