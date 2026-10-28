package de.knito.billboard.api.impl;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import de.knito.billboard.api.impl.box.LuaBoxBlockEntity;

/** {@code /luabox <box pos> restart|stop|status}: controls a Lua Box. Needs op level 2, so command blocks can use it. */
public final class LuaBoxCommand {
	private LuaBoxCommand() {
	}

	public static void initialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				Commands.literal("luabox")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("box", BlockPosArgument.blockPos())
								.then(Commands.literal("restart").executes(context -> run(context, "restart")))
								.then(Commands.literal("stop").executes(context -> run(context, "stop")))
								.then(Commands.literal("status").executes(context -> run(context, "status"))))));
	}

	/** Restarts, stops or shows a Lua Box; status also shows its folder and its output lines. */
	private static int run(CommandContext<CommandSourceStack> context, String action) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "box");
		if (!(source.getLevel().getBlockEntity(pos) instanceof LuaBoxBlockEntity box)) {
			source.sendFailure(Component.translatable("message.billboard_api.no_lua_box", pos.getX(), pos.getY(), pos.getZ()));
			return 0;
		}
		switch (action) {
			case "restart" -> box.restart();
			case "stop" -> box.stop();
			default -> {
			}
		}
		source.sendSuccess(() -> Component.translatable("message.billboard_api.box_status", box.statusText()), false);
		if (action.equals("status")) {
			source.sendSuccess(() -> Component.literal(box.folder(source.getServer()).toString()), false);
			for (LuaBoxBlockEntity.Line line : box.lines()) {
				source.sendSuccess(() -> Component.literal(line.text()).withStyle(line.error() ? ChatFormatting.RED : ChatFormatting.GRAY), false);
			}
		}
		return 1;
	}
}
