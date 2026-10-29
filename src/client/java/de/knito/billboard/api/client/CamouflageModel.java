package de.knito.billboard.api.client;

import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;

/** The model of a redstone port: its own, or that of the block it is camouflaged as. */
public final class CamouflageModel extends WrapperBlockStateModel {
	public CamouflageModel(BlockStateModel wrapped) {
		super(wrapped);
	}

	private static @Nullable BlockState look(BlockAndTintGetter view, BlockPos pos) {
		return view.getBlockEntityRenderData(pos) instanceof BlockState state ? state : null;
	}

	private static BlockStateModel modelOf(BlockState state) {
		return Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
	}

	@Override
	public void emitQuads(QuadEmitter emitter, BlockAndTintGetter view, BlockPos pos, BlockState state, RandomSource random, Predicate<Direction> cullTest) {
		BlockState look = look(view, pos);
		if (look == null) {
			super.emitQuads(emitter, view, pos, state, random, cullTest);
		} else {
			modelOf(look).emitQuads(emitter, view, pos, look, random, cullTest);
		}
	}

	@Override
	public Object createGeometryKey(BlockAndTintGetter view, BlockPos pos, BlockState state, RandomSource random) {
		BlockState look = look(view, pos);
		return look == null ? super.createGeometryKey(view, pos, state, random) : modelOf(look).createGeometryKey(view, pos, look, random);
	}

	@Override
	public Material.Baked particleMaterial(BlockAndTintGetter view, BlockPos pos, BlockState state) {
		BlockState look = look(view, pos);
		return look == null ? super.particleMaterial(view, pos, state) : modelOf(look).particleMaterial(view, pos, look);
	}

	@Override
	public int materialFlags(BlockAndTintGetter view, BlockPos pos, BlockState state, RandomSource random) {
		BlockState look = look(view, pos);
		return look == null ? super.materialFlags(view, pos, state, random) : modelOf(look).materialFlags(view, pos, look, random);
	}

	@Override
	public boolean hasMaterialFlag(BlockAndTintGetter view, BlockPos pos, BlockState state, RandomSource random, int flag) {
		BlockState look = look(view, pos);
		return look == null ? super.hasMaterialFlag(view, pos, state, random, flag) : modelOf(look).hasMaterialFlag(view, pos, look, random, flag);
	}
}
