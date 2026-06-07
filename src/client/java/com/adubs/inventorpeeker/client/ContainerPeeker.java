package com.adubs.inventorpeeker.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the container the player is currently looking at and snapshots its contents.
 *
 * <p>In singleplayer / LAN-host worlds we read straight from the integrated server's authoritative
 * block entity so the contents are real and live (including double chests). On a remote server the
 * client does not have container contents, so we rely on a short-lived cache that is populated by
 * {@link com.adubs.inventorpeeker.net.PeekPayloads.Response} packets requested from the server.
 */
public final class ContainerPeeker {

	/** How long a remote snapshot stays valid before we stop showing it (ms). */
	private static final long REMOTE_CACHE_TTL_MS = 3_000L;

	private static BlockPos cachedPos;
	private static List<ItemStack> cachedItems;
	private static long cachedAtMs;

	private ContainerPeeker() {
	}

	/** Immutable snapshot of a container's contents for rendering. */
	public record PeekResult(BlockPos pos, Component title, List<ItemStack> items) {
		public int size() {
			return items.size();
		}
	}

	/** Stores a snapshot received from the server (multiplayer path). */
	public static void cacheRemote(BlockPos pos, List<ItemStack> items) {
		cachedPos = pos.immutable();
		cachedItems = items;
		cachedAtMs = System.currentTimeMillis();
	}

	public static PeekResult resolveLookedAtContainer(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.player == null) {
			return null;
		}

		BlockPos pos = raycastBlock(minecraft);
		if (pos == null) {
			return null;
		}

		// The client always has the container's block entity (and thus its size/type), even on a
		// remote server. Contents may be empty until the server replies.
		Container clientContainer = HopperBlockEntity.getContainerAt(minecraft.level, pos);
		if (clientContainer == null) {
			return null;
		}

		BlockState state = minecraft.level.getBlockState(pos);
		Component title = state.getBlock().getName();

		// Singleplayer / LAN host: read authoritative live contents from the integrated server.
		MinecraftServer server = minecraft.getSingleplayerServer();
		if (server != null) {
			ServerLevel serverLevel = server.getLevel(minecraft.level.dimension());
			if (serverLevel != null) {
				Container serverContainer = HopperBlockEntity.getContainerAt(serverLevel, pos);
				if (serverContainer != null) {
					return new PeekResult(pos, title, snapshot(serverContainer));
				}
			}
			return new PeekResult(pos, title, snapshot(clientContainer));
		}

		// Remote server: use the cached snapshot if it matches and is fresh.
		if (cachedItems != null && pos.equals(cachedPos)
				&& System.currentTimeMillis() - cachedAtMs <= REMOTE_CACHE_TTL_MS) {
			return new PeekResult(pos, title, cachedItems);
		}

		// No fresh data yet; show the (empty) grid so the panel appears immediately and fills in
		// once the response arrives.
		return new PeekResult(pos, title, snapshot(clientContainer));
	}

	private static BlockPos raycastBlock(Minecraft minecraft) {
		HitResult hit = minecraft.hitResult;
		if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
			return null;
		}
		return ((BlockHitResult) hit).getBlockPos();
	}

	private static List<ItemStack> snapshot(Container container) {
		int size = container.getContainerSize();
		List<ItemStack> items = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			items.add(container.getItem(i).copy());
		}
		return items;
	}
}
