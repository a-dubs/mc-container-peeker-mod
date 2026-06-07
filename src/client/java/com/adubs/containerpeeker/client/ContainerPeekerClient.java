package com.adubs.containerpeeker.client;

import com.adubs.containerpeeker.ContainerPeeker;
import com.adubs.containerpeeker.net.PeekPayloads;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class ContainerPeekerClient implements ClientModInitializer {

	public static final String KEY_PEEK = "key.containerpeeker.peek";
	private static final KeyMapping.Category KEY_CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ContainerPeeker.MOD_ID, "main"));

	/** Re-request a remote container's contents at most this often (ticks). 20/REMOTE_REFRESH_TICKS = ~10/sec. */
	private static final int REMOTE_REFRESH_TICKS = 2;

	private static PeekConfig config;
	private static KeyMapping peekKey;
	private static boolean toggledOn = false;

	/**
	 * Latest container snapshot, recomputed once per client tick and drawn each frame. Resolving
	 * here (instead of in the per-frame HUD callback) avoids redundant container lookups and item
	 * copies at high frame rates.
	 */
	private static ContainerReader.PeekResult currentResult;

	/** Exposes the live config instance for the in-game settings screen. */
	public static PeekConfig getConfig() {
		if (config == null) {
			config = PeekConfig.load();
		}
		return config;
	}

	private static int tickCounter = 0;
	private static BlockPos lastRequestPos = null;
	private static int lastRequestTick = Integer.MIN_VALUE;

	@Override
	public void onInitializeClient() {
		config = PeekConfig.load();

		peekKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				KEY_PEEK,
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_V,
				KEY_CATEGORY
		));

		ClientPlayNetworking.registerGlobalReceiver(PeekPayloads.Response.TYPE, (payload, context) ->
				context.client().execute(() -> ContainerReader.cacheRemote(payload.pos(), payload.items())));

		ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

		HudRenderCallback.EVENT.register((graphics, delta) -> {
			ContainerReader.PeekResult result = currentResult;
			if (result != null) {
				PeekHud.render(graphics, Minecraft.getInstance(), config, result);
			}
		});

		ContainerPeeker.LOGGER.info("Container Peeker initialized (mode={}, corner={})", config.mode, config.corner);
	}

	private void onClientTick(Minecraft minecraft) {
		tickCounter++;

		// Drain queued presses so TOGGLE flips exactly once per physical press.
		boolean pressed = false;
		while (peekKey.consumeClick()) {
			pressed = true;
		}
		if (pressed && config.mode == PeekConfig.ActivationMode.TOGGLE) {
			toggledOn = !toggledOn;
		}

		if (!isActive(minecraft)) {
			currentResult = null;
			return;
		}

		ContainerReader.PeekResult result = ContainerReader.resolveLookedAtContainer(minecraft);

		// On remote servers we must ask the server for the live contents. Do this before the
		// hide-when-empty check so data still arrives even while the panel is hidden as empty.
		if (result != null
				&& minecraft.getSingleplayerServer() == null
				&& ClientPlayNetworking.canSend(PeekPayloads.Request.TYPE)) {
			maybeRequestContents(result.pos());
		}

		if (result != null && config.hideWhenEmpty && isEmpty(result)) {
			result = null;
		}
		currentResult = result;
	}

	private void maybeRequestContents(BlockPos pos) {
		boolean targetChanged = !pos.equals(lastRequestPos);
		boolean stale = tickCounter - lastRequestTick >= REMOTE_REFRESH_TICKS;
		if (targetChanged || stale) {
			ClientPlayNetworking.send(new PeekPayloads.Request(pos));
			lastRequestPos = pos;
			lastRequestTick = tickCounter;
		}
	}

	private static boolean isActive(Minecraft minecraft) {
		if (!config.enabled) {
			return false;
		}
		if (minecraft.level == null || minecraft.player == null) {
			return false;
		}
		if (minecraft.screen != null || minecraft.options.hideGui) {
			return false;
		}
		return switch (config.mode) {
			case HOLD -> peekKey.isDown();
			case TOGGLE -> toggledOn;
		};
	}

	private static boolean isEmpty(ContainerReader.PeekResult result) {
		return result.items().stream().allMatch(stack -> stack.isEmpty());
	}
}
