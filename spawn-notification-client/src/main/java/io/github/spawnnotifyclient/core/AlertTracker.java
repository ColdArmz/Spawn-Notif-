package io.github.spawnnotifyclient.core;

import io.github.spawnnotifyclient.config.ModConfig;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Duplicate suppression and cooldowns. Time is passed in so the logic is deterministic in tests.
 *
 * <ul>
 *   <li>Per-entity memory: an entity UUID alerts once, even if its chunk unloads and reloads.</li>
 *   <li>Per-species cooldown: the same species will not alert again within N seconds.</li>
 *   <li>Global sound cooldown: alerts still show, but the sound is skipped if one just played.</li>
 *   <li>Shiny alerts can bypass both cooldowns (configurable).</li>
 * </ul>
 */
public final class AlertTracker {
	/** Hard cap so a very long session cannot grow the memory without bound. */
	private static final int MAX_REMEMBERED = 4096;

	public enum Verdict {
		/** Show the alert and play the sound. */
		ALERT,
		/** Show the alert but skip the sound (global sound cooldown). */
		ALERT_SILENT,
		/** Drop it: already alerted for this entity, or species cooldown. */
		SUPPRESS
	}

	private final LinkedHashMap<UUID, Long> seen = new LinkedHashMap<>();
	private final Map<String, Long> speciesLast = new HashMap<>();
	private long lastSoundMs = Long.MIN_VALUE / 2;

	public Verdict decide(UUID entity, String speciesId, Set<Category> cats, long nowMs, ModConfig cfg) {
		Long prev = seen.get(entity);
		if (prev != null && nowMs - prev < cfg.entityMemorySeconds * 1000L) {
			return Verdict.SUPPRESS;
		}
		seen.remove(entity); // refresh insertion order
		seen.put(entity, nowMs);
		while (seen.size() > MAX_REMEMBERED) {
			Iterator<UUID> it = seen.keySet().iterator();
			it.next();
			it.remove();
		}

		boolean bypass = cfg.shinyBypassesCooldown && cats.contains(Category.SHINY);
		String key = Names.norm(speciesId);
		Long last = speciesLast.get(key);
		if (!bypass && last != null && nowMs - last < (long) (cfg.perSpeciesCooldownSeconds * 1000.0)) {
			return Verdict.SUPPRESS;
		}
		speciesLast.put(key, nowMs);

		if (!bypass && nowMs - lastSoundMs < (long) (cfg.globalSoundCooldownSeconds * 1000.0)) {
			return Verdict.ALERT_SILENT;
		}
		lastSoundMs = nowMs;
		return Verdict.ALERT;
	}

	/** Drop expired memory. Call occasionally (e.g. once a second). */
	public void prune(long nowMs, ModConfig cfg) {
		long memory = cfg.entityMemorySeconds * 1000L;
		seen.values().removeIf(t -> nowMs - t >= memory);
		long longestCooldown = (long) (cfg.perSpeciesCooldownSeconds * 1000.0);
		speciesLast.values().removeIf(t -> nowMs - t >= longestCooldown);
	}

	/** Forget everything (reconnect / disconnect). */
	public void clear() {
		seen.clear();
		speciesLast.clear();
		lastSoundMs = Long.MIN_VALUE / 2;
	}

	public int rememberedEntities() {
		return seen.size();
	}
}
