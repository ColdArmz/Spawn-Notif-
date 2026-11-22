package io.github.spawnnotifyclient;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.spawnnotifyclient.client.AlertHud;
import io.github.spawnnotifyclient.client.ConfigScreen;
import io.github.spawnnotifyclient.client.EntityWatcher;
import io.github.spawnnotifyclient.client.SpawnCommands;
import io.github.spawnnotifyclient.config.ModConfig;
import io.github.spawnnotifyclient.core.AlertTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Client entrypoint. This mod is declared {@code "environment": "client"}, registers nothing on
 * the network, and never needs to be installed on the server.
 */
public final class SpawnNotifyClient implements ClientModInitializer {
	public static final String MOD_ID = "spawnnotifyclient";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static ModConfig config;
	private static final AlertHud HUD = new AlertHud();
	private static final AlertTracker TRACKER = new AlertTracker();
	private static final EntityWatcher WATCHER = new EntityWatcher(TRACKER);
	private static boolean openConfigRequested;

	private static KeyMapping openConfigKey;

	public static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json");
	}

	public static ModConfig config() {
		if (config == null) {
			config = ModConfig.load(configPath(), LOGGER::warn);
		}
		return config;
	}

	public static AlertHud hud() {
		return HUD;
	}

	public static EntityWatcher watcher() {
		return WATCHER;
	}

	/** Re-read the JSON file and apply it live. */
	public static void reloadConfig() {
		ModConfig fresh = ModConfig.load(configPath(), LOGGER::warn);
		config().copyFrom(fresh);
		WATCHER.reset();
	}

	/**
	 * Opening a screen straight from a chat command is undone when the chat screen closes, so
	 * commands ask for it here and the next client tick opens it.
	 */
	public static void requestOpenConfig() {
		openConfigRequested = true;
	}

	@Override
	public void onInitializeClient() {
		config(); // load (or create) the config file now so problems show up at startup

		openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.spawnnotifyclient.open_config", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN,
				"key.categories.spawnnotifyclient"));

		WATCHER.register();
		HudRenderCallback.EVENT.register((graphics, tickCounter) -> HUD.render(graphics, config().hudOffsetY));
		SpawnCommands.register();
		ClientTickEvents.END_CLIENT_TICK.register(SpawnNotifyClient::onTick);

		LOGGER.info("Spawn Notification Client loaded (client-side only; no server component).");
	}

	private static void onTick(Minecraft mc) {
		boolean open = false;
		while (openConfigKey.consumeClick()) {
			open = true;
		}
		if (openConfigRequested) {
			openConfigRequested = false;
			open = true;
		}
		if (open && !(mc.screen instanceof ConfigScreen)) {
			mc.setScreen(new ConfigScreen(mc.screen));
		}
	}
}
