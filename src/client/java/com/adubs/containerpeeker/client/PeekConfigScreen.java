package com.adubs.containerpeeker.client;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Builds the Cloth Config settings screen exposed through ModMenu. */
public final class PeekConfigScreen {

	private PeekConfigScreen() {
	}

	public static Screen create(Screen parent) {
		PeekConfig config = ContainerPeekerClient.getConfig();
		PeekConfig defaults = new PeekConfig();

		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Component.literal("Container Peeker"))
				.setSavingRunnable(config::save);

		ConfigEntryBuilder entry = builder.entryBuilder();
		ConfigCategory general = builder.getOrCreateCategory(Component.literal("General"));

		general.addEntry(entry.startBooleanToggle(Component.literal("Enabled"), config.enabled)
				.setDefaultValue(defaults.enabled)
				.setTooltip(Component.literal("Master switch for the peek overlay."))
				.setSaveConsumer(value -> config.enabled = value)
				.build());

		general.addEntry(entry.startEnumSelector(Component.literal("Activation mode"),
						PeekConfig.ActivationMode.class, config.mode)
				.setDefaultValue(defaults.mode)
				.setTooltip(Component.literal("HOLD: show while the key is held. TOGGLE: press to flip on/off."))
				.setSaveConsumer(value -> config.mode = value)
				.build());

		general.addEntry(entry.startEnumSelector(Component.literal("Screen corner"),
						PeekConfig.Corner.class, config.corner)
				.setDefaultValue(defaults.corner)
				.setTooltip(Component.literal("Which corner the overlay anchors to."))
				.setSaveConsumer(value -> config.corner = value)
				.build());

		general.addEntry(entry.startDoubleField(Component.literal("Scale"), config.scale)
				.setMin(0.25)
				.setMax(4.0)
				.setDefaultValue(defaults.scale)
				.setTooltip(Component.literal("Overall size multiplier for the overlay."))
				.setSaveConsumer(value -> config.scale = value)
				.build());

		general.addEntry(entry.startIntField(Component.literal("Margin X"), config.marginX)
				.setMin(0)
				.setMax(400)
				.setDefaultValue(defaults.marginX)
				.setTooltip(Component.literal("Horizontal gap from the screen edge, in GUI pixels."))
				.setSaveConsumer(value -> config.marginX = value)
				.build());

		general.addEntry(entry.startIntField(Component.literal("Margin Y"), config.marginY)
				.setMin(0)
				.setMax(400)
				.setDefaultValue(defaults.marginY)
				.setTooltip(Component.literal("Vertical gap from the screen edge, in GUI pixels."))
				.setSaveConsumer(value -> config.marginY = value)
				.build());

		general.addEntry(entry.startIntSlider(Component.literal("Background opacity"), config.backgroundOpacity, 0, 100)
				.setDefaultValue(defaults.backgroundOpacity)
				.setTooltip(Component.literal("Panel background opacity (0 = transparent, 100 = opaque)."))
				.setSaveConsumer(value -> config.backgroundOpacity = value)
				.build());

		general.addEntry(entry.startBooleanToggle(Component.literal("Show title"), config.showTitle)
				.setDefaultValue(defaults.showTitle)
				.setTooltip(Component.literal("Draw the container name above the grid."))
				.setSaveConsumer(value -> config.showTitle = value)
				.build());

		general.addEntry(entry.startBooleanToggle(Component.literal("Hide when empty"), config.hideWhenEmpty)
				.setDefaultValue(defaults.hideWhenEmpty)
				.setTooltip(Component.literal("Hide the overlay when the targeted container is empty."))
				.setSaveConsumer(value -> config.hideWhenEmpty = value)
				.build());

		return builder.build();
	}
}
