package de.knito.billboard.api;

import java.nio.file.Path;

import org.jspecify.annotations.Nullable;

/**
 * The program that calls a {@link LuaMethod}: one run of one Lua Box. The same object stands for the whole run,
 * so a client can remember it, for instance to let one program at a time claim the machine; compare with
 * {@code equals}. After the program ends or the box is broken, {@link #isActive()} turns false.
 *
 * @since 0.1.0
 */
public interface LuaCaller {
	/** For players and error messages, such as "Lua Box at 12 64 -3". */
	String describe();

	/** False once the program has ended, been stopped, or its box is gone. */
	boolean isActive();

	/**
	 * A file of the calling box by its name, such as an image a program wants shown, or null if the box has no file
	 * of that name. Read-only by contract: the box's files belong to its owner.
	 */
	@Nullable Path file(String name);

	/**
	 * Another machine of the calling box's network by its name (compared without case), or null if there is none
	 * or this box may not reach it. For clients that work together with a second machine, such as an inventory
	 * port moving items to another one; the caller names it as a string. A caller that cannot look machines up
	 * (tests) returns null.
	 *
	 * @since 0.3.0
	 */
	default @Nullable LuaClient client(String name) {
		return null;
	}
}
