package com.adubs.inventorpeeker.net;

import com.adubs.inventorpeeker.InventorPeeker;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Network payloads used to sync container contents on multiplayer servers.
 *
 * <p>The client asks the server "what's in the container at this position?" and the server replies
 * with a snapshot. This is what lets the overlay work on dedicated servers, where vanilla never
 * sends container contents to the client until the container is actually opened.
 */
public final class PeekPayloads {

	private PeekPayloads() {
	}

	/** Client -> Server: "tell me the contents of the container I'm looking at." */
	public record Request(BlockPos pos) implements CustomPacketPayload {
		public static final Type<Request> TYPE = new Type<>(Identifier.fromNamespaceAndPath(InventorPeeker.MOD_ID, "peek_request"));

		public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, Request::pos,
				Request::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Server -> Client: a snapshot of the container contents at a position. */
	public record Response(BlockPos pos, List<ItemStack> items) implements CustomPacketPayload {
		public static final Type<Response> TYPE = new Type<>(Identifier.fromNamespaceAndPath(InventorPeeker.MOD_ID, "peek_response"));

		public static final StreamCodec<RegistryFriendlyByteBuf, Response> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, Response::pos,
				ItemStack.OPTIONAL_LIST_STREAM_CODEC, Response::items,
				Response::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
