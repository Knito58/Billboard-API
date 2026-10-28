package de.knito.billboard.api.impl.box;

import java.io.ByteArrayInputStream;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import de.knito.billboard.api.impl.BillboardApiMod;
import de.knito.billboard.api.impl.ApiConfig;
import de.knito.billboard.api.lua.cobalt.Constants;
import de.knito.billboard.api.lua.cobalt.LuaError;
import de.knito.billboard.api.lua.cobalt.LuaState;
import de.knito.billboard.api.lua.cobalt.LuaTable;
import de.knito.billboard.api.lua.cobalt.LuaThread;
import de.knito.billboard.api.lua.cobalt.Varargs;
import de.knito.billboard.api.lua.cobalt.compiler.CompileException;
import de.knito.billboard.api.lua.cobalt.compiler.LoadState;
import de.knito.billboard.api.lua.cobalt.function.LuaFunction;
import de.knito.billboard.api.lua.cobalt.interrupt.InterruptAction;
import de.knito.billboard.api.lua.cobalt.lib.CoreLibraries;

/**
 * Runs one Lua program for a Lua Box, a little in every server tick. The program pauses when it calls
 * sleep() or when its time slice for this tick is used up, and goes on from there later. Not saved: after a
 * restart the program starts again from the top.
 */
final class BoxRunner {
	enum Status {
		RUNNING, SLEEPING, ENDED, FAILED, STOPPED
	}

	private final LuaBoxBlockEntity box;
	private final LuaState state;
	private final LuaThread main;
	/** The value sleep() yields with, so the runner can tell it from other yields. */
	private final LuaTable sleepToken = new LuaTable();
	private final LuaLibraries libraries;
	private Status status = Status.RUNNING;
	/** Game tick at which a sleeping program goes on. */
	private long wakeTick;
	/** True when the last slice ran out of time in the middle of the program, rather than in sleep(). */
	private boolean paused;
	/** Whether the alarm, not a sleep(), ended the slice now running. */
	private boolean outOfTime;
	private long sliceEnd;
	/** Ticks in a row in which the program ran out of time without calling sleep(). */
	private int ticksWithoutSleep;
	/** Ticks in which the program used its whole time slice, since it last slept longer than one tick. */
	private int busyTicks;

	private BoxRunner(LuaBoxBlockEntity box, LuaState state, LuaThread main, LuaLibraries libraries) {
		this.box = box;
		this.state = state;
		this.main = main;
		this.libraries = libraries;
	}

	/** Compiles the program; throws with a message for the output lines if that fails. */
	static BoxRunner start(LuaBoxBlockEntity box, String fileName, byte[] code) throws LuaError, CompileException {
		BoxRunner[] self = new BoxRunner[1];
		LuaState state = LuaState.builder()
				.interruptHandler(() -> self[0] != null && self[0].timeIsUp() ? InterruptAction.SUSPEND : InterruptAction.CONTINUE)
				.errorReporter((error, message) -> BillboardApiMod.LOGGER.warn("Lua Box at {}: {}", box.getBlockPos().toShortString(), message.get(), error))
				.build();
		CoreLibraries.standardGlobals(state);
		LuaLibraries libraries = new LuaLibraries(box, state);
		LuaFunction program = LoadState.load(state, new ByteArrayInputStream(code), "@" + fileName, state.globals());
		BoxRunner runner = new BoxRunner(box, state, new LuaThread(state, program), libraries);
		self[0] = runner;
		libraries.install(runner.sleepToken);
		return runner;
	}

	Status status() {
		return status;
	}

	boolean isAlive() {
		return status == Status.RUNNING || status == Status.SLEEPING;
	}

	/** Lets the program run until it sleeps or its time for this tick is up. */
	void tick(long gameTime) {
		if (!isAlive() || (status == Status.SLEEPING && gameTime < wakeTick)) {
			return;
		}
		status = Status.RUNNING;
		long budget = TimeUnit.MILLISECONDS.toNanos(ApiConfig.boxMillisPerTick());
		long start = System.nanoTime();
		sliceEnd = start + budget;
		outOfTime = false;
		Future<?> alarm = BoxThreads.ALARM.schedule(state::interrupt, budget, TimeUnit.NANOSECONDS);
		Varargs result;
		try {
			if (paused) {
				// Go on inside whatever coroutine the program was in when the time ran out.
				LuaThread current = state.getCurrentThread();
				result = LuaThread.run(current == null || current == state.getMainThread() ? main : current, null);
			} else {
				result = LuaThread.run(main, Constants.NONE);
			}
		} catch (LuaError e) {
			fail(e);
			return;
		} catch (RuntimeException | StackOverflowError e) {
			BillboardApiMod.LOGGER.warn("Lua Box at {} crashed", box.getBlockPos().toShortString(), e);
			fail("Java error: " + e);
			return;
		} finally {
			alarm.cancel(false);
		}

		if (System.nanoTime() - start >= budget * 9 / 10) {
			busyTicks++;
		}
		if (main.getStatus() == LuaThread.Status.DEAD) {
			end(Status.ENDED, "Program ended.", false);
			return;
		}
		if (outOfTime) {
			paused = true;
			if (++ticksWithoutSleep > ApiConfig.boxMaxSecondsWithoutSleep() * 20) {
				fail("Too long without sleep(). Stopped after " + ApiConfig.boxMaxSecondsWithoutSleep() + " seconds.");
				return;
			}
		} else {
			paused = false;
			ticksWithoutSleep = 0;
			double seconds = result != null && result.first() == sleepToken ? result.arg(2).toDouble() : 0;
			long sleepTicks = Math.max(1, (long) Math.ceil(seconds * 20));
			wakeTick = gameTime + sleepTicks;
			// A real rest, not just sleep(0), shows the program is not hogging the server.
			if (sleepTicks > 1) {
				busyTicks = 0;
			}
			status = Status.SLEEPING;
		}
		if (busyTicks > ApiConfig.boxTooBusySeconds() * 20) {
			fail("Too busy: used its whole time for " + ApiConfig.boxTooBusySeconds() + " seconds. Please sleep() longer.");
		}
	}

	/** Called from inside the Lua VM when the alarm rang. */
	private boolean timeIsUp() {
		if (System.nanoTime() < sliceEnd) {
			return false;
		}
		outOfTime = true;
		return true;
	}

	/** The hand brake: stops the program. */
	void stop() {
		if (isAlive()) {
			end(Status.STOPPED, "Stopped.", false);
		}
	}

	private void fail(LuaError error) {
		BillboardApiMod.LOGGER.debug("Lua Box at {}: {}", box.getBlockPos().toShortString(), error.getMessage());
		fail(error.getValue() == null ? String.valueOf(error.getMessage()) : error.getValue().toString());
	}

	private void fail(String message) {
		end(Status.FAILED, message, true);
	}

	private void end(Status newStatus, String message, boolean error) {
		status = newStatus;
		libraries.close();
		box.print(message, error);
		box.runnerChanged();
	}
}
