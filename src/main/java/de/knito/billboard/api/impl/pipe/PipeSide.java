package de.knito.billboard.api.impl.pipe;

import java.util.Locale;

import net.minecraft.util.StringRepresentable;

/** One side of a data pipe: nothing there, joined to a pipe, box or client, or switched off by a player. */
public enum PipeSide implements StringRepresentable {
	NONE, CONNECTED, OFF;

	@Override
	public String getSerializedName() {
		return name().toLowerCase(Locale.ROOT);
	}
}
