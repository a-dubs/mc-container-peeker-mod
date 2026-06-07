package com.adubs.containerpeeker.client;

import com.adubs.containerpeeker.ContainerPeeker;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Lightweight JSON-backed configuration. Lives in {@code config/containerpeeker.json} and is
 * regenerated with defaults if missing or malformed.
 */
public class PeekConfig {

	public enum ActivationMode {
		/** Overlay shows only while the key is physically held. */
		HOLD,
		/** Each key press flips the overlay on/off. */
		TOGGLE
	}

	public enum Corner {
		TOP_LEFT,
		TOP_RIGHT,
		BOTTOM_LEFT,
		BOTTOM_RIGHT
	}

	// --- Persisted fields (names are the JSON keys) ---

	/** Master switch for the whole feature. */
	public boolean enabled = true;

	/** Whether the hotkey is hold-to-show or press-to-toggle. */
	public ActivationMode mode = ActivationMode.HOLD;

	/** Which screen corner the overlay anchors to. */
	public Corner corner = Corner.BOTTOM_RIGHT;

	/** Overall size multiplier for the overlay. */
	public double scale = 1.0;

	/** Horizontal gap (in GUI pixels) between the overlay and the screen edge. */
	public int marginX = 8;

	/** Vertical gap (in GUI pixels) between the overlay and the screen edge. */
	public int marginY = 8;

	/** Draw the container name above the grid. */
	public boolean showTitle = true;

	/** Background opacity, 0 (transparent) to 100 (opaque). */
	public int backgroundOpacity = 75;

	/** Hide the overlay when the targeted container is empty. */
	public boolean hideWhenEmpty = false;

	// --- Loading / saving ---

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve(ContainerPeeker.MOD_ID + ".json");
	}

	public static PeekConfig load() {
		Path path = configPath();
		PeekConfig config = null;
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				config = GSON.fromJson(reader, PeekConfig.class);
			} catch (Exception e) {
				ContainerPeeker.LOGGER.warn("Failed to read {}, regenerating defaults", path, e);
			}
		}
		if (config == null) {
			config = new PeekConfig();
		}
		config.sanitize();
		config.save();
		return config;
	}

	public void save() {
		Path path = configPath();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			ContainerPeeker.LOGGER.warn("Failed to write config {}", path, e);
		}
	}

	private void sanitize() {
		if (mode == null) {
			mode = ActivationMode.HOLD;
		}
		if (corner == null) {
			corner = Corner.BOTTOM_RIGHT;
		}
		scale = clamp(scale, 0.25, 4.0);
		marginX = (int) clamp(marginX, 0, 400);
		marginY = (int) clamp(marginY, 0, 400);
		backgroundOpacity = (int) clamp(backgroundOpacity, 0, 100);
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
