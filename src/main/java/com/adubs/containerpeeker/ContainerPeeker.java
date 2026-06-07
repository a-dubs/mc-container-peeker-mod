package com.adubs.containerpeeker;

import com.adubs.containerpeeker.net.PeekPayloads;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ContainerPeeker implements ModInitializer {
	public static final String MOD_ID = "containerpeeker";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Max squared distance (blocks^2) a player may peek a container from. ~8 blocks. */
	private static final double MAX_PEEK_DISTANCE_SQ = 8.0 * 8.0;

	@Override
	public void onInitialize() {
		// Register payload types on both sides so client and server agree on the channels.
		PayloadTypeRegistry.playC2S().register(PeekPayloads.Request.TYPE, PeekPayloads.Request.CODEC);
		PayloadTypeRegistry.playS2C().register(PeekPayloads.Response.TYPE, PeekPayloads.Response.CODEC);

		// Server-side: answer a client's request with a live snapshot of the container.
		ServerPlayNetworking.registerGlobalReceiver(PeekPayloads.Request.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			BlockPos pos = payload.pos();
			player.level().getServer().execute(() -> respond(player, pos));
		});
	}

	private static void respond(ServerPlayer player, BlockPos pos) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}

		double distSq = player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
		if (distSq > MAX_PEEK_DISTANCE_SQ) {
			return;
		}

		Container container = HopperBlockEntity.getContainerAt(level, pos);
		if (container == null) {
			return;
		}

		int size = container.getContainerSize();
		List<ItemStack> items = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			items.add(container.getItem(i).copy());
		}

		ServerPlayNetworking.send(player, new PeekPayloads.Response(pos, items));
	}
}
