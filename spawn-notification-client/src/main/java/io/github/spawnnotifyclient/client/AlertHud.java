package io.github.spawnnotifyclient.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.Deque;

/** On-screen banners stacked at the top-centre of the screen, with a short fade in/out. */
public final class AlertHud {
	private static final int HEIGHT = 30;
	private static final int GAP = 4;
	private static final long FADE_IN_MS = 200;
	private static final long FADE_OUT_MS = 500;

	private static final class Banner {
		final Component title;
		final Component subtitle;
		final int rgb;
		final long createdMs;
		final long durationMs;

		Banner(Component title, Component subtitle, int rgb, long createdMs, long durationMs) {
			this.title = title;
			this.subtitle = subtitle;
			this.rgb = rgb;
			this.createdMs = createdMs;
			this.durationMs = durationMs;
		}
	}

	private final Deque<Banner> banners = new ArrayDeque<>();

	public void push(Component title, Component subtitle, int rgb, long nowMs, long durationMs, int maxVisible) {
		banners.addLast(new Banner(title, subtitle, rgb & 0xFFFFFF, nowMs, durationMs));
		while (banners.size() > maxVisible) {
			banners.removeFirst();
		}
	}

	public void clear() {
		banners.clear();
	}

	public void render(GuiGraphics g, int offsetY) {
		if (banners.isEmpty()) {
			return;
		}
		long now = System.currentTimeMillis();
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		int screenWidth = mc.getWindow().getGuiScaledWidth();

		banners.removeIf(b -> now - b.createdMs >= b.durationMs);

		int y = offsetY;
		for (Banner b : banners) {
			long elapsed = now - b.createdMs;
			long remaining = b.durationMs - elapsed;
			double a = Math.min(1.0, Math.min(elapsed / (double) FADE_IN_MS, remaining / (double) FADE_OUT_MS));
			a = Math.max(0.0, a);

			int width = Math.max(font.width(b.title), font.width(b.subtitle)) + 22;
			int x = (screenWidth - width) / 2;

			int bgAlpha = (int) (a * 200);
			int accentAlpha = (int) (a * 255);
			g.fill(x, y, x + width, y + HEIGHT, (bgAlpha << 24) | 0x101018);
			g.fill(x, y, x + 3, y + HEIGHT, (accentAlpha << 24) | b.rgb);

			if (a > 0.12) { // very low alpha text would be drawn fully opaque by the font renderer
				int textColor = (Math.max(26, (int) (a * 255)) << 24) | 0xFFFFFF;
				g.drawString(font, b.title, x + 11, y + 6, textColor);
				g.drawString(font, b.subtitle, x + 11, y + 18, textColor);
			}
			y += HEIGHT + GAP;
		}
	}
}
