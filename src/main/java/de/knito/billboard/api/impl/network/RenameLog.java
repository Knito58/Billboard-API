package de.knito.billboard.api.impl.network;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Clients renamed because of a name clash, so that every box that sees them can tell its player, not only the box
 * whose network scan did the renaming. Kept for the whole server, the newest few only.
 */
public final class RenameLog {
	public record Entry(long seq, ResourceKey<Level> dimension, BlockPos pos, String oldName, String newName) {
		public String message() {
			return oldName + " at " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " is now " + newName + " (name clash after networks joined)";
		}
	}

	private static final int KEEP = 64;
	private static final Deque<Entry> ENTRIES = new ArrayDeque<>();
	private static long seq;

	private RenameLog() {
	}

	static synchronized void add(ResourceKey<Level> dimension, BlockPos pos, String oldName, String newName) {
		ENTRIES.addLast(new Entry(++seq, dimension, pos.immutable(), oldName, newName));
		while (ENTRIES.size() > KEEP) {
			ENTRIES.removeFirst();
		}
	}

	/** The newest entry's number, 0 if there is none. */
	public static synchronized long last() {
		return seq;
	}

	/** Entries after {@code after} about one of these positions, oldest first. */
	public static synchronized List<Entry> since(long after, ResourceKey<Level> dimension, Set<BlockPos> positions) {
		List<Entry> result = new ArrayList<>();
		for (Entry entry : ENTRIES) {
			if (entry.seq() > after && entry.dimension().equals(dimension) && positions.contains(entry.pos())) {
				result.add(entry);
			}
		}
		return result;
	}
}
