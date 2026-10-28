package de.knito.billboard.api.impl;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;

import de.knito.billboard.api.impl.box.LuaBoxMenu;

public class ModMenus {
	public static final MenuType<LuaBoxMenu> LUA_BOX = Registry.register(
			BuiltInRegistries.MENU,
			BillboardApiMod.id("lua_box"),
			new ExtendedMenuType<>(LuaBoxMenu::new, LuaBoxMenu.OpenData.STREAM_CODEC)
	);

	public static void initialize() {
	}
}
