package de.knito.billboard.api.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.Identifier;

import net.fabricmc.api.ModInitializer;

import de.knito.billboard.api.impl.clients.SignClient;
import de.knito.billboard.api.impl.net.ModNetworking;
import de.knito.billboard.api.impl.network.ClientData;

/** The library's server and common side: the Lua Box, data pipes and the network of Lua clients. */
public class BillboardApiMod implements ModInitializer {
	public static final String MOD_ID = "billboard_api";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ApiConfig.load();
		ModBlocks.initialize();
		ModBlockEntities.initialize();
		ScriptBoxAlias.initialize();
		ClientData.initialize();
		SignClient.initialize();
		ModMenus.initialize();
		ModNetworking.initialize();
		LuaBoxCommand.initialize();
		LOGGER.info("Billboard API loaded");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
