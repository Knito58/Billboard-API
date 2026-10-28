package de.knito.billboard.api.impl;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;

/**
 * The library's settings, {@code config/billboard_api.properties}. Read at game or server start. On the first start
 * the Lua Box limits are taken over from Billboard Engine's {@code billboard.properties}, if there is one, so a
 * server keeps the limits it had for its Script Boxes.
 */
public final class ApiConfig {
	private static final String FILE = "billboard_api.properties";
	private static final String OLD_FILE = "billboard.properties";
	/** Largest file a Lua Box may hold, whatever the settings say; the uploads are kept in memory. */
	public static final int LIMIT_FILE_KB = 16384;
	private static final String DEFAULT_TYPES = "png,jpg,jpeg,gif,bmp,txt,json,csv,lua";

	private static boolean boxesEnabled = true;
	private static boolean boxesOpsOnly = false;
	private static int boxMillisPerTick = 2;
	private static int boxMaxSecondsWithoutSleep = 5;
	private static int boxTooBusySeconds = 30;
	private static int boxMaxLuaKb = 64;
	private static int boxMaxFiles = 8;
	private static int boxMaxFileKb = 2048;
	private static List<String> boxFileTypes = types(DEFAULT_TYPES);

	private ApiConfig() {
	}

	public static boolean boxesEnabled() {
		return boxesEnabled;
	}

	/** Only operators may give boxes files, remove files, restart or stop them. */
	public static boolean boxesOpsOnly() {
		return boxesOpsOnly;
	}

	public static int boxMillisPerTick() {
		return boxMillisPerTick;
	}

	public static int boxMaxSecondsWithoutSleep() {
		return boxMaxSecondsWithoutSleep;
	}

	public static int boxTooBusySeconds() {
		return boxTooBusySeconds;
	}

	public static int boxMaxLuaKb() {
		return boxMaxLuaKb;
	}

	/** Files a box may hold besides main.lua. */
	public static int boxMaxFiles() {
		return boxMaxFiles;
	}

	public static int boxMaxFileKb() {
		return boxMaxFileKb;
	}

	/** Allowed file extensions, lower case, without the dot. */
	public static List<String> boxFileTypes() {
		return boxFileTypes;
	}

	static void load() {
		Path dir = FabricLoader.getInstance().getConfigDir();
		Path file = dir.resolve(FILE);
		boolean fresh = !Files.exists(file);
		Properties properties = read(fresh ? dir.resolve(OLD_FILE) : file);
		if (fresh && !properties.isEmpty()) {
			// Billboard Engine's names for the same limits
			rename(properties, "scripts_enabled", "boxes_enabled");
			rename(properties, "scripts_ops_only", "boxes_ops_only");
			rename(properties, "script_ms_per_tick", "box_ms_per_tick");
			rename(properties, "script_max_busy_seconds", "box_max_busy_seconds");
			rename(properties, "script_too_busy_seconds", "box_too_busy_seconds");
			rename(properties, "script_max_lua_kb", "box_max_lua_kb");
			rename(properties, "script_max_images", "box_max_files");
			rename(properties, "script_max_image_kb", "box_max_file_kb");
			BillboardApiMod.LOGGER.info("Took the Script Box settings from {} for the Lua Box", OLD_FILE);
		}
		boxesEnabled = readBoolean(properties, "boxes_enabled", boxesEnabled);
		boxesOpsOnly = readBoolean(properties, "boxes_ops_only", boxesOpsOnly);
		boxMillisPerTick = read(properties, "box_ms_per_tick", boxMillisPerTick, 1, 20);
		boxMaxSecondsWithoutSleep = read(properties, "box_max_busy_seconds", boxMaxSecondsWithoutSleep, 1, 60);
		boxTooBusySeconds = read(properties, "box_too_busy_seconds", boxTooBusySeconds, 5, 3600);
		boxMaxLuaKb = read(properties, "box_max_lua_kb", boxMaxLuaKb, 1, 1024);
		boxMaxFiles = read(properties, "box_max_files", boxMaxFiles, 0, 64);
		boxMaxFileKb = read(properties, "box_max_file_kb", boxMaxFileKb, 1, LIMIT_FILE_KB);
		boxFileTypes = types(properties.getProperty("box_file_types", String.join(",", boxFileTypes)));
		// A file from an older version lacks the newer keys: write it again with the values just read.
		if (fresh || !properties.containsKey("box_file_types")) {
			write(file);
		}
	}

	private static Properties read(Path file) {
		Properties properties = new Properties();
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				properties.load(reader);
			} catch (IOException e) {
				BillboardApiMod.LOGGER.warn("Could not read {}, using defaults", file, e);
			}
		}
		return properties;
	}

	private static void rename(Properties properties, String from, String to) {
		String value = properties.getProperty(from);
		if (value != null) {
			properties.setProperty(to, value);
		}
	}

	private static List<String> types(String list) {
		return Arrays.stream(list.split(","))
				.map(type -> type.strip().toLowerCase(Locale.ROOT))
				.map(type -> type.startsWith(".") ? type.substring(1) : type)
				.filter(type -> !type.isEmpty())
				.distinct()
				.toList();
	}

	private static int read(Properties properties, String key, int fallback, int min, int max) {
		String value = properties.getProperty(key);
		if (value == null) {
			return fallback;
		}
		try {
			return Math.clamp(Integer.parseInt(value.strip()), min, max);
		} catch (NumberFormatException e) {
			BillboardApiMod.LOGGER.warn("{}: {}={} is not a number, using {}", FILE, key, value, fallback);
			return fallback;
		}
	}

	private static boolean readBoolean(Properties properties, String key, boolean fallback) {
		String value = properties.getProperty(key);
		return value == null ? fallback : Boolean.parseBoolean(value.strip());
	}

	private static void write(Path file) {
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file)) {
				writer.write("""
						# Billboard API settings. Read at game or server start.

						# Lua Boxes run Lua programs on the server. Switch them off, or allow only operators
						# to give them files and to restart or stop them (good for public servers).
						boxes_enabled=%b
						boxes_ops_only=%b

						# Lua time per box and tick in milliseconds; then the program pauses until the next tick.
						box_ms_per_tick=%d
						# A program that runs this many seconds without sleep() is stopped.
						box_max_busy_seconds=%d
						# A program that uses its whole time for this many seconds, with no sleep() longer than one tick, is stopped as too busy.
						box_too_busy_seconds=%d

						# Largest .lua file, number of files besides main.lua, largest other file (up to %d KB).
						box_max_lua_kb=%d
						box_max_files=%d
						box_max_file_kb=%d
						# File types a box may hold besides main.lua, separated by commas.
						box_file_types=%s
						""".formatted(boxesEnabled, boxesOpsOnly, boxMillisPerTick, boxMaxSecondsWithoutSleep, boxTooBusySeconds,
						LIMIT_FILE_KB, boxMaxLuaKb, boxMaxFiles, boxMaxFileKb, String.join(",", boxFileTypes)));
			}
		} catch (IOException e) {
			BillboardApiMod.LOGGER.warn("Could not write {}", file, e);
		}
	}
}
