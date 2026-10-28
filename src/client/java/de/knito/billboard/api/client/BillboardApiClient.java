package de.knito.billboard.api.client;

import net.minecraft.client.gui.screens.MenuScreens;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import de.knito.billboard.api.impl.ModMenus;
import de.knito.billboard.api.impl.net.LuaBoxStatePayload;

/** The library's client side: the Lua Box screen. */
public class BillboardApiClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(ModMenus.LUA_BOX, LuaBoxScreen::new);

		ClientPlayNetworking.registerGlobalReceiver(LuaBoxStatePayload.TYPE, (payload, context) -> {
			if (context.client().gui.screen() instanceof LuaBoxScreen screen) {
				screen.setState(payload);
			}
		});
	}
}
