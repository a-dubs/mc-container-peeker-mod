package com.adubs.containerpeeker.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Draws the little container-contents overlay in a configurable screen corner. */
public final class PeekHud {

	private static final int CELL = 18;
	private static final int ITEM = 16;
	private static final int PADDING = 6;
	private static final int TITLE_GAP = 3;

	private static final int SLOT_COLOR = 0x40FFFFFF;
	private static final int BORDER_COLOR = 0xFF000000;

	private PeekHud() {
	}

	public static void render(GuiGraphics graphics, Minecraft minecraft, PeekConfig config, ContainerReader.PeekResult result) {
		List<ItemStack> items = result.items();
		int size = items.size();
		if (size <= 0) {
			return;
		}

		Font font = minecraft.font;
		int cols = columnsFor(size);
		int rows = (size + cols - 1) / cols;

		int gridW = cols * CELL;
		int gridH = rows * CELL;

		boolean showTitle = config.showTitle;
		int titleH = showTitle ? font.lineHeight + TITLE_GAP : 0;

		int panelW = gridW + PADDING * 2;
		int panelH = gridH + PADDING * 2 + titleH;

		double scale = config.scale;
		int screenW = minecraft.getWindow().getGuiScaledWidth();
		int screenH = minecraft.getWindow().getGuiScaledHeight();

		double effW = panelW * scale;
		double effH = panelH * scale;

		double originX;
		double originY;
		switch (config.corner) {
			case TOP_LEFT -> {
				originX = config.marginX;
				originY = config.marginY;
			}
			case TOP_RIGHT -> {
				originX = screenW - config.marginX - effW;
				originY = config.marginY;
			}
			case BOTTOM_LEFT -> {
				originX = config.marginX;
				originY = screenH - config.marginY - effH;
			}
			default -> { // BOTTOM_RIGHT
				originX = screenW - config.marginX - effW;
				originY = screenH - config.marginY - effH;
			}
		}

		graphics.pose().pushMatrix();
		graphics.pose().translate((float) originX, (float) originY);
		graphics.pose().scale((float) scale, (float) scale);

		int bgAlpha = (int) (config.backgroundOpacity / 100.0 * 255.0) & 0xFF;
		int bgColor = (bgAlpha << 24) | 0x0F0F12;
		graphics.fill(0, 0, panelW, panelH, bgColor);
		if (bgAlpha > 0) {
			drawOutline(graphics, 0, 0, panelW, panelH, BORDER_COLOR);
		}

		int contentY = PADDING;
		if (showTitle) {
			Component title = result.title();
			graphics.drawString(font, title, PADDING, PADDING, 0xFFFFFFFF);
			contentY += titleH;
		}

		int gridX = PADDING;
		for (int i = 0; i < size; i++) {
			int col = i % cols;
			int row = i / cols;
			int cx = gridX + col * CELL;
			int cy = contentY + row * CELL;

			graphics.fill(cx, cy, cx + ITEM, cy + ITEM, SLOT_COLOR);

			ItemStack stack = items.get(i);
			if (!stack.isEmpty()) {
				graphics.renderItem(stack, cx, cy);
				graphics.renderItemDecorations(font, stack, cx, cy);
			}
		}

		graphics.pose().popMatrix();
	}

	private static void drawOutline(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
		graphics.fill(x1, y1, x2, y1 + 1, color);
		graphics.fill(x1, y2 - 1, x2, y2, color);
		graphics.fill(x1, y1, x1 + 1, y2, color);
		graphics.fill(x2 - 1, y1, x2, y2, color);
	}

	/**
	 * Picks a column count that matches the real container layout for the sizes the user cares
	 * about, and falls back to the vanilla 9-wide grid for everything else.
	 */
	private static int columnsFor(int size) {
		return switch (size) {
			case 3 -> 3;   // furnace / brewing-style rows
			case 5 -> 5;   // hopper
			case 9 -> 3;   // dropper / dispenser (3x3)
			default -> 9;  // chest (27 -> 9x3), double chest (54 -> 9x6), barrel, shulker, etc.
		};
	}
}
