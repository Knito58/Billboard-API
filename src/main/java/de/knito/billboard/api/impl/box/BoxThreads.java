package de.knito.billboard.api.impl.box;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/** Helper threads for the Lua Boxes. The Lua programs themselves always run in the server tick. */
final class BoxThreads {
	/** Wakes a Lua program when its time slice for this tick is used up, so it pauses until the next tick. */
	static final ScheduledExecutorService ALARM = Executors.newSingleThreadScheduledExecutor(
			Thread.ofPlatform().daemon().name("Billboard API Box Alarm").factory());
	/** Checks uploaded images beside the game, so a large GIF does not hold up the tick. */
	static final ExecutorService WORKER = Executors.newFixedThreadPool(2,
			Thread.ofPlatform().daemon().name("Billboard API Box Worker ", 1).factory());

	private BoxThreads() {
	}
}
