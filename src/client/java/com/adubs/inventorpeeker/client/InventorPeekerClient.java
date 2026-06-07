package com.adubs.inventorpeeker.client;

import com.adubs.inventorpeeker.InventorPeeker;
import com.adubs.inventorpeeker.net.PeekPayloads;
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

public class InventorPeekerClient implements ClientModInitializer {

	public static final String KEY_PEEK = "key.inventorpeeker.peek";
	private static final KeyMapping.Category KEY_CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(InventorPeeker.MOD_ID, "main"));

	/** Re-request a remote container's contents at most this often (ticks). ~4/sec. */
	private static final int REMOTE_REFRESH_TICKS = 5;

	private static PeekConfig config;
	private static KeyMapping peekKey;
	private static boolean toggledOn = false;

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
				context.client().execute(() -> ContainerPeeker.cacheRemote(payload.pos(), payload.items())));

		ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

		HudRenderCallback.EVENT.register((graphics, delta) -> {
			Minecraft minecraft = Minecraft.getInstance();
			if (!isActive(minecraft)) {
				return;
			}

			ContainerPeeker.PeekResult result = ContainerPeeker.resolveLookedAtContainer(minecraft);
			if (result == null) {
				return;
			}
			if (config.hideWhenEmpty && isEmpty(result)) {
				return;
			}

			PeekHud.render(graphics, minecraft, config, result);
		});

		InventorPeeker.LOGGER.info("Inventory Peeker initialized (mode={}, corner={})", config.mode, config.corner);
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

		// On remote servers we have to ask the server for contents. Skip when an integrated server
		// is present (we read those contents directly) or when the server lacks the mod.
		if (!isActive(minecraft) || minecraft.getSingleplayerServer() != null) {
			return;
		}
		if (!ClientPlayNetworking.canSend(PeekPayloads.Request.TYPE)) {
			return;
		}

		BlockPos pos = ContainerPeeker.lookedAtContainerPos(minecraft);
		if (pos == null) {
			return;
		}

		boolean targetChanged = !pos.equals(lastRequestPos);
		boolean stale = tickCounter - lastRequestTick >= REMOTE_REFRESH_TICKS;
		if (targetChanged || stale) {
			ClientPlayNetworking.send(new PeekPayloads.Request(pos.immutable()));
			lastRequestPos = pos.immutable();
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

	private static boolean isEmpty(ContainerPeeker.PeekResult result) {
		return result.items().stream().allMatch(stack -> stack.isEmpty());
	}
}
