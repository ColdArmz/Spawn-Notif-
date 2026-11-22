package io.github.spawnnotifyclient.client;

import io.github.spawnnotifyclient.SpawnNotifyClient;
import io.github.spawnnotifyclient.config.ModConfig;
import io.github.spawnnotifyclient.core.Category;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Settings screen built only from vanilla widgets (no Cloth Config / YACL dependency).
 * Changes apply to the live config immediately and are written to disk when the screen closes.
 */
public final class ConfigScreen extends Screen {
	private static final int COL_W = 150;
	private static final int GAP = 10;
	private static final int ROW = 24;
	private static final int TOP = 44;
	private static final int PAGES = 4;

	private record Label(Component text, int x, int y) {}

	private static int lastPage = 0;

	private final Screen parent;
	private final ModConfig cfg = SpawnNotifyClient.config();
	private final List<Label> labels = new ArrayList<>();
	private int page = lastPage;

	public ConfigScreen(Screen parent) {
		super(Component.translatable("spawnnotifyclient.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		labels.clear();
		int cx = width / 2;
		int left = cx - COL_W - GAP / 2;
		int right = cx + GAP / 2;
		int full = COL_W * 2 + GAP;

		switch (page) {
			case 0 -> buildAlerts(left, right);
			case 1 -> buildOutput(left, right);
			case 2 -> buildFilters(left, full);
			default -> buildCooldowns(left, right, full);
		}

		int footerY = height - 28;
		addRenderableWidget(Button.builder(Component.literal("<"), b -> changePage(-1))
				.bounds(left, footerY, 40, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
				.bounds(cx - 50, footerY, 100, 20).build());
		addRenderableWidget(Button.builder(Component.literal(">"), b -> changePage(1))
				.bounds(right + COL_W - 40, footerY, 40, 20).build());
	}

	private void changePage(int delta) {
		cfg.sanitize();
		page = Math.floorMod(page + delta, PAGES);
		lastPage = page;
		rebuildWidgets();
	}

	// ---- Pages ---------------------------------------------------------------------------------

	private void buildAlerts(int left, int right) {
		int y = TOP;
		toggle(left, y, "enabled", cfg.enabled, v -> cfg.enabled = v);
		toggle(right, y, "ignore_owned", cfg.ignoreOwned, v -> cfg.ignoreOwned = v);
		y += ROW;
		toggle(left, y, "shiny", cfg.alertShiny, v -> cfg.alertShiny = v);
		toggle(right, y, "legendary", cfg.alertLegendary, v -> cfg.alertLegendary = v);
		y += ROW;
		toggle(left, y, "mythical", cfg.alertMythical, v -> cfg.alertMythical = v);
		toggle(right, y, "ultra_beast", cfg.alertUltraBeast, v -> cfg.alertUltraBeast = v);
		y += ROW;
		toggle(left, y, "paradox", cfg.alertParadox, v -> cfg.alertParadox = v);
	}

	private void buildOutput(int left, int right) {
		int y = TOP;
		toggle(left, y, "visual", cfg.visualAlerts, v -> cfg.visualAlerts = v);
		toggle(right, y, "chat", cfg.chatAlerts, v -> cfg.chatAlerts = v);
		y += ROW;
		toggle(left, y, "coords", cfg.includeCoordinates, v -> cfg.includeCoordinates = v);
		toggle(right, y, "distance", cfg.includeDistance, v -> cfg.includeDistance = v);
		y += ROW;
		toggle(left, y, "sound", cfg.soundEnabled, v -> cfg.soundEnabled = v);
		addRenderableWidget(new VolumeSlider(right, y, COL_W, 20, cfg));
		y += ROW + 10;
		label("spawnnotifyclient.config.duration", left, y - 10);
		numberBox(left, y, COL_W, cfg.displayDurationSeconds, v -> cfg.displayDurationSeconds = v);
		addRenderableWidget(Button.builder(Component.translatable("spawnnotifyclient.config.test"),
				b -> AlertDispatcher.test(Category.SHINY)).bounds(right, y, COL_W, 20).build());
	}

	private void buildFilters(int left, int full) {
		int y = TOP + 8;
		label("spawnnotifyclient.config.whitelist", left, y - 10);
		textBox(left, y, full, ModConfig.joinList(cfg.speciesWhitelist), t -> cfg.speciesWhitelist = ModConfig.parseList(t));
		y += 38;
		label("spawnnotifyclient.config.blacklist", left, y - 10);
		textBox(left, y, full, ModConfig.joinList(cfg.speciesBlacklist), t -> cfg.speciesBlacklist = ModConfig.parseList(t));
		y += 38;
		label("spawnnotifyclient.config.extra_labels", left, y - 10);
		textBox(left, y, full, ModConfig.joinList(cfg.extraLabels), t -> cfg.extraLabels = ModConfig.parseList(t));
	}

	private void buildCooldowns(int left, int right, int full) {
		int y = TOP + 8;
		label("spawnnotifyclient.config.species_cooldown", left, y - 10);
		numberBox(left, y, COL_W, cfg.perSpeciesCooldownSeconds, v -> cfg.perSpeciesCooldownSeconds = v);
		label("spawnnotifyclient.config.sound_cooldown", right, y - 10);
		numberBox(right, y, COL_W, cfg.globalSoundCooldownSeconds, v -> cfg.globalSoundCooldownSeconds = v);
		y += 38;
		label("spawnnotifyclient.config.max_distance", left, y - 10);
		numberBox(left, y, COL_W, cfg.maxDistance, v -> cfg.maxDistance = (int) Math.round(v));
		toggle(right, y, "shiny_bypass", cfg.shinyBypassesCooldown, v -> cfg.shinyBypassesCooldown = v);
	}

	// ---- Widget helpers -----------------------------------------------------------------------

	private void toggle(int x, int y, String key, boolean initial, Consumer<Boolean> setter) {
		addRenderableWidget(CycleButton.onOffBuilder(initial)
				.create(x, y, COL_W, 20, Component.translatable("spawnnotifyclient.config." + key),
						(button, value) -> setter.accept(value)));
	}

	private void label(String key, int x, int y) {
		labels.add(new Label(Component.translatable(key).withStyle(ChatFormatting.YELLOW), x, y));
	}

	private void textBox(int x, int y, int w, String initial, Consumer<String> setter) {
		EditBox box = new EditBox(font, x, y, w, 20, Component.empty());
		box.setMaxLength(4096);
		box.setValue(initial);
		box.setResponder(setter);
		addRenderableWidget(box);
	}

	private void numberBox(int x, int y, int w, double initial, Consumer<Double> setter) {
		EditBox box = new EditBox(font, x, y, w, 20, Component.empty());
		box.setMaxLength(8);
		box.setFilter(t -> t.matches("\\d*\\.?\\d*"));
		box.setValue(initial == Math.rint(initial) ? Long.toString((long) initial) : Double.toString(initial));
		box.setResponder(t -> {
			try {
				if (!t.isEmpty() && !t.equals(".")) {
					setter.accept(Double.parseDouble(t));
				}
			} catch (NumberFormatException ignored) {
				// the filter prevents most bad input; anything else is ignored
			}
		});
		addRenderableWidget(box);
	}

	private static final class VolumeSlider extends AbstractSliderButton {
		private final ModConfig config;

		VolumeSlider(int x, int y, int w, int h, ModConfig config) {
			super(x, y, w, h, Component.empty(), config.volume);
			this.config = config;
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.translatable("spawnnotifyclient.config.volume", Math.round(value * 100)));
		}

		@Override
		protected void applyValue() {
			config.volume = value;
		}
	}

	// ---- Rendering / lifecycle ------------------------------------------------------------------

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// Screen.render draws the background itself in 1.21.x; do not call renderBackground() as well.
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
		Component pageTitle = Component.translatable("spawnnotifyclient.config.page." + page)
				.append(Component.literal("  (" + (page + 1) + "/" + PAGES + ")"))
				.withStyle(ChatFormatting.GRAY);
		graphics.drawCenteredString(font, pageTitle, width / 2, 26, 0xFFFFFF);
		for (Label l : labels) {
			graphics.drawString(font, l.text(), l.x(), l.y(), 0xFFFFFF);
		}
	}

	@Override
	public void removed() {
		// Runs on Esc, Done, or when another screen replaces this one.
		cfg.save(SpawnNotifyClient.configPath(), SpawnNotifyClient.LOGGER::warn);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
