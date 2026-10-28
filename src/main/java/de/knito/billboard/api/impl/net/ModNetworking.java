package de.knito.billboard.api.impl.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import de.knito.billboard.api.impl.box.BoxUploads;

public class ModNetworking {
	public static void initialize() {
		PayloadTypeRegistry.serverboundPlay().register(LuaBoxActionPayload.TYPE, LuaBoxActionPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(BoxUploadPayload.TYPE, BoxUploadPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(LuaBoxStatePayload.TYPE, LuaBoxStatePayload.STREAM_CODEC);

		ServerPlayNetworking.registerGlobalReceiver(LuaBoxActionPayload.TYPE, (payload, context) -> BoxUploads.action(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(BoxUploadPayload.TYPE, (payload, context) -> BoxUploads.receive(context.player(), payload));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> BoxUploads.forget(handler.getPlayer().getUUID()));
	}
}
