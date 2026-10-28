package de.knito.billboard.api.impl.box;

import java.util.Locale;

import net.minecraft.util.StringRepresentable;

/** What the Lua Box's light shows. A sleeping program counts as running, so the block state rarely changes. */
public enum BoxState implements StringRepresentable {
	STOPPED, RUNNING, ERROR;

	@Override
	public String getSerializedName() {
		return name().toLowerCase(Locale.ROOT);
	}
}
