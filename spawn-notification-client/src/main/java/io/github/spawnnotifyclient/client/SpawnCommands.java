package io.github.spawnnotifyclient.client;

import io.github.spawnnotifyclient.SpawnNotifyClient;
import io.github.spawnnotifyclient.core.Category;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Client-side commands (they are handled locally and never sent to the server):
 * <pre>
 * /spawnnotify                open the settings screen
 * /spawnnotify toggle         master on/off
 * /spawnnotify reload         re-read config/spawnnotifyclient.json
 * /spawnnotify test [category] preview an alert
 * </pre>
 */
public final class SpawnCommands {
	private SpawnCommands() {}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			var root = ClientCommandManager.literal("spawnnotify")
					.executes(ctx -> {
						SpawnNotifyClient.requestOpenConfig();
						return 1;
					})
					.then(ClientCommandManager.literal("toggle").executes(ctx -> {
						var cfg = SpawnNotifyClient.config();
						cfg.enabled = !cfg.enabled;
						cfg.save(SpawnNotifyClient.configPath(), SpawnNotifyClient.LOGGER::warn);
						ctx.getSource().sendFeedback(Component.translatable(
								cfg.enabled ? "spawnnotifyclient.command.enabled" : "spawnnotifyclient.command.disabled"));
						return 1;
					}))
					.then(ClientCommandManager.literal("reload").executes(ctx -> {
						SpawnNotifyClient.reloadConfig();
						ctx.getSource().sendFeedback(Component.translatable("spawnnotifyclient.command.reloaded"));
						return 1;
					}));

			var test = ClientCommandManager.literal("test").executes(ctx -> {
				AlertDispatcher.test(Category.SHINY);
				return 1;
			});
			for (Category c : Category.values()) {
				test.then(ClientCommandManager.literal(c.name().toLowerCase(Locale.ROOT)).executes(ctx -> {
					AlertDispatcher.test(c);
					return 1;
				}));
			}
			dispatcher.register(root.then(test));
		});
	}
}
