package de.knito.billboard.api.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;

/** The library's server and common side. Blocks, pipes and the client registry come here step by step. */
public class BillboardApiMod implements ModInitializer {
	public static final String MOD_ID = "billboard_api";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Billboard API loaded");
	}
}
