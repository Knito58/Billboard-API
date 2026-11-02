package de.knito.billboard.api.client;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.config.IPluginConfig;

import de.knito.billboard.api.impl.BillboardApiMod;

/**
 * Jade plugin: adds "Network name: ..." to Jade's box for every machine of a network, whichever mod it comes from.
 * Jade loads this class through the {@code jade} entrypoint in fabric.mod.json; without Jade nothing loads it, so
 * Jade stays optional. The line can be switched off in Jade's plugin settings.
 */
public final class JadeNames implements IWailaPlugin, IBlockComponentProvider {
	private static final Identifier UID = BillboardApiMod.id("client_name");

	@Override
	public void registerClient(IWailaClientRegistration registration) {
		registration.registerBlockComponent(this, Block.class);
	}

	@Override
	public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
		String name = NameHint.name(accessor.getLevel(), accessor.getPosition());
		if (name != null) {
			tooltip.add(Component.translatable("tooltip.billboard_api.client_name", name));
		}
	}

	@Override
	public Identifier getUid() {
		return UID;
	}
}
