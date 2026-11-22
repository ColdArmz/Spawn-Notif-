package io.github.spawnnotifyclient.client;

import io.github.spawnnotifyclient.SpawnNotifyClient;
import io.github.spawnnotifyclient.config.ModConfig;
import io.github.spawnnotifyclient.core.Category;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import java.util.EnumSet;

/** Turns an {@link AlertEvent} into chat text, an on-screen banner and a sound, per the config. */
public final class AlertDispatcher {
	private AlertDispatcher() {}

	static void dispatch(AlertEvent ev, boolean playSound) {
		ModConfig cfg = SpawnNotifyClient.config();
		Minecraft mc = Minecraft.getInstance();

		MutableComponent labels = labels(ev.categories());
		MutableComponent name = ev.pokemonName().copy().withStyle(colorOf(ev.primary()), ChatFormatting.BOLD);
		MutableComponent where = location(ev, cfg);

		if (cfg.chatAlerts && mc.player != null) {
			MutableComponent line = Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
					.append(Component.translatable("spawnnotifyclient.prefix").withStyle(ChatFormatting.GRAY))
					.append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY))
					.append(Component.translatable("spawnnotifyclient.alert.spotted", labels, name, where));
			mc.player.displayClientMessage(line, false);
		}

		if (cfg.visualAlerts) {
			MutableComponent title = labels(ev.categories()).append(Component.literal(" "))
					.append(ev.pokemonName().copy().withStyle(colorOf(ev.primary()), ChatFormatting.BOLD));
			int rgb = colorOf(ev.primary()).getColor() == null ? 0xFFFFFF : colorOf(ev.primary()).getColor();
			SpawnNotifyClient.hud().push(title, where.copy().withStyle(ChatFormatting.GRAY), rgb,
					System.currentTimeMillis(), (long) (cfg.displayDurationSeconds * 1000.0), cfg.maxVisibleBanners);
		}

		if (playSound && cfg.soundEnabled) {
			playSound(ev.primary(), cfg);
		}
	}

	/** Preview used by the settings screen and /spawnnotify test. Bypasses filters and cooldowns. */
	public static void test(Category category) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		int x = p == null ? 0 : p.blockPosition().getX();
		int y = p == null ? 0 : p.blockPosition().getY();
		int z = p == null ? 0 : p.blockPosition().getZ();
		EnumSet<Category> cats = EnumSet.of(category);
		dispatch(new AlertEvent(Component.literal("Test Pokémon"), cats, category, x, y, z, 0), true);
	}

	private static MutableComponent labels(EnumSet<Category> cats) {
		MutableComponent out = Component.empty();
		boolean first = true;
		for (Category c : cats) {
			if (!first) {
				out.append(Component.literal(" "));
			}
			first = false;
			out.append(Component.translatable("spawnnotifyclient.category." + c.name().toLowerCase(java.util.Locale.ROOT))
					.withStyle(colorOf(c)));
		}
		return out;
	}

	private static MutableComponent location(AlertEvent ev, ModConfig cfg) {
		MutableComponent out = Component.empty();
		if (cfg.includeCoordinates) {
			out.append(Component.translatable("spawnnotifyclient.alert.at", ev.x(), ev.y(), ev.z())
					.withStyle(ChatFormatting.WHITE));
		}
		if (cfg.includeDistance && ev.distance() > 0) {
			out.append(Component.translatable("spawnnotifyclient.alert.away", Math.round(ev.distance()))
					.withStyle(ChatFormatting.GRAY));
		}
		return out;
	}

	private static ChatFormatting colorOf(Category c) {
		ChatFormatting f = ChatFormatting.getByName(c.colorName);
		return f == null ? ChatFormatting.WHITE : f;
	}

	private static void playSound(Category category, ModConfig cfg) {
		String id = cfg.soundFor(category);
		if (id.isEmpty() || cfg.volume <= 0.0) {
			return;
		}
		ResourceLocation location = ResourceLocation.tryParse(id);
		if (location == null) {
			return;
		}
		SoundEvent event = SoundEvent.createVariableRangeEvent(location);
		Minecraft.getInstance().getSoundManager()
				.play(SimpleSoundInstance.forUI(event, (float) cfg.pitch, (float) cfg.volume));
	}
}
