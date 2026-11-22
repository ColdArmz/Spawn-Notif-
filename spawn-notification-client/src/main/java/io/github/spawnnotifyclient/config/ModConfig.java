package io.github.spawnnotifyclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import io.github.spawnnotifyclient.core.Category;
import io.github.spawnnotifyclient.core.Names;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * User configuration, stored as JSON (default: {@code config/spawnnotifyclient.json}).
 * Missing keys fall back to the defaults below; out-of-range values are clamped by {@link #sanitize()}.
 */
public final class ModConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	// ---- Master switch -------------------------------------------------------------------
	public boolean enabled = true;

	// ---- What to alert on ------------------------------------------------------------------
	public boolean alertShiny = true;
	public boolean alertLegendary = true;
	public boolean alertMythical = true;
	public boolean alertUltraBeast = true;
	public boolean alertParadox = true;
	/** Skip Pokémon that have an owner (other players' Pokémon). */
	public boolean ignoreOwned = true;

	// ---- Custom lists (names are matched ignoring case, spaces, punctuation and "cobblemon:") ----
	/** Species that always alert, whatever their rarity. */
	public List<String> speciesWhitelist = new ArrayList<>();
	/** Species that never alert, not even when shiny or legendary. */
	public List<String> speciesBlacklist = new ArrayList<>();
	/** Extra Cobblemon labels that should alert (for example "restricted" or "baby"). */
	public List<String> extraLabels = new ArrayList<>();

	// ---- Output ----------------------------------------------------------------------------
	public boolean visualAlerts = true;
	public boolean chatAlerts = true;
	public boolean includeCoordinates = true;
	public boolean includeDistance = true;
	/** How long an on-screen banner stays up, in seconds. */
	public double displayDurationSeconds = 6.0;
	public int maxVisibleBanners = 3;
	/** Vertical offset of the banner stack from the top of the screen, in GUI pixels. */
	public int hudOffsetY = 12;

	// ---- Sound -----------------------------------------------------------------------------
	public boolean soundEnabled = true;
	/** 0.0 – 1.0 */
	public double volume = 1.0;
	/** 0.5 – 2.0 */
	public double pitch = 1.0;
	/** Category name -> sound event id. An empty string silences that category. */
	public Map<String, String> sounds = defaultSounds();

	// ---- Cooldowns & detection -------------------------------------------------------------
	/** The same species will not alert again within this many seconds. 0 disables. */
	public double perSpeciesCooldownSeconds = 30.0;
	/** Alerts within this many seconds of the last sound are shown without sound. 0 disables. */
	public double globalSoundCooldownSeconds = 2.0;
	/** Shiny alerts ignore both cooldowns. */
	public boolean shinyBypassesCooldown = true;
	/** How long an already-alerted entity is remembered (prevents re-alerts when chunks reload). */
	public int entityMemorySeconds = 900;
	/** Ignore Pokémon farther away than this many blocks. 0 = no limit. */
	public int maxDistance = 0;
	/**
	 * Ticks to wait after an entity loads before reading its data, so that Cobblemon has time to
	 * sync species/shiny state to the client. Raise this if you ever see wrong species in alerts.
	 */
	public int settleTicks = 10;

	// ---- Derived lookup sets (rebuilt by sanitize(); not serialised) -------------------------
	private transient Set<String> whitelistSet = Set.of();
	private transient Set<String> blacklistSet = Set.of();
	private transient Set<String> extraLabelSet = Set.of();

	public static Map<String, String> defaultSounds() {
		Map<String, String> m = new LinkedHashMap<>();
		for (Category c : Category.values()) {
			m.put(c.name(), c.defaultSound);
		}
		return m;
	}

	public Set<String> whitelistSet() { return whitelistSet; }
	public Set<String> blacklistSet() { return blacklistSet; }
	public Set<String> extraLabelSet() { return extraLabelSet; }

	/** The configured sound id for a category, or "" if silenced. */
	public String soundFor(Category c) {
		String s = sounds == null ? null : sounds.get(c.name());
		return s == null ? c.defaultSound : s.trim();
	}

	/** Clamp values, repair nulls, merge default sounds and rebuild the lookup sets. */
	public void sanitize() {
		if (speciesWhitelist == null) speciesWhitelist = new ArrayList<>();
		if (speciesBlacklist == null) speciesBlacklist = new ArrayList<>();
		if (extraLabels == null) extraLabels = new ArrayList<>();
		Map<String, String> merged = defaultSounds();
		if (sounds != null) {
			for (Map.Entry<String, String> e : sounds.entrySet()) {
				if (e.getKey() != null && e.getValue() != null && merged.containsKey(e.getKey().toUpperCase(java.util.Locale.ROOT))) {
					merged.put(e.getKey().toUpperCase(java.util.Locale.ROOT), e.getValue());
				}
			}
		}
		sounds = merged;
		displayDurationSeconds = clamp(displayDurationSeconds, 1.0, 60.0);
		maxVisibleBanners = (int) clamp(maxVisibleBanners, 1, 8);
		hudOffsetY = (int) clamp(hudOffsetY, 0, 400);
		volume = clamp(volume, 0.0, 1.0);
		pitch = clamp(pitch, 0.5, 2.0);
		perSpeciesCooldownSeconds = clamp(perSpeciesCooldownSeconds, 0.0, 3600.0);
		globalSoundCooldownSeconds = clamp(globalSoundCooldownSeconds, 0.0, 600.0);
		entityMemorySeconds = (int) clamp(entityMemorySeconds, 10, 86400);
		maxDistance = (int) clamp(maxDistance, 0, 4096);
		settleTicks = (int) clamp(settleTicks, 1, 200);
		whitelistSet = normSet(speciesWhitelist);
		blacklistSet = normSet(speciesBlacklist);
		extraLabelSet = normSet(extraLabels);
	}

	private static Set<String> normSet(List<String> list) {
		Set<String> out = new HashSet<>();
		for (String s : list) {
			String n = Names.norm(s);
			if (!n.isEmpty()) out.add(n);
		}
		return out;
	}

	private static double clamp(double v, double lo, double hi) {
		if (Double.isNaN(v)) return lo;
		return Math.max(lo, Math.min(hi, v));
	}

	/** Parse "pikachu, Mr. Mime ;ditto" style text into a clean list. */
	public static List<String> parseList(String text) {
		List<String> out = new ArrayList<>();
		if (text == null) return out;
		for (String part : text.split("[,;\\n]")) {
			String t = part.trim();
			if (!t.isEmpty()) out.add(t);
		}
		return out;
	}

	public static String joinList(List<String> list) {
		return list == null ? "" : String.join(", ", list);
	}

	// ---- Persistence ---------------------------------------------------------------------------

	/** Load from disk. Creates the file with defaults if absent; backs up and replaces a corrupt one. */
	public static ModConfig load(Path path, Consumer<String> warn) {
		ModConfig cfg = null;
		if (Files.exists(path)) {
			try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				cfg = GSON.fromJson(r, ModConfig.class);
			} catch (IOException | JsonParseException e) {
				warn.accept("Could not read " + path + " (" + e.getMessage() + "); using defaults and keeping a backup.");
				try {
					Files.move(path, path.resolveSibling(path.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException ignored) {
					// best effort
				}
			}
		}
		boolean fresh = cfg == null;
		if (fresh) cfg = new ModConfig();
		cfg.sanitize();
		if (fresh || !Files.exists(path)) {
			cfg.save(path, warn);
		}
		return cfg;
	}

	/** Atomic-ish save: write a temp file, then move it over the real one. */
	public void save(Path path, Consumer<String> warn) {
		sanitize();
		try {
			Path parent = path.toAbsolutePath().getParent();
			if (parent != null) Files.createDirectories(parent);
			Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
			try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(this, w);
			}
			try {
				Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (java.nio.file.AtomicMoveNotSupportedException e) {
				Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			warn.accept("Could not save " + path + ": " + e.getMessage());
		}
	}

	/** Copy every persisted value from another instance (used by "reload"). */
	public void copyFrom(ModConfig o) {
		enabled = o.enabled; alertShiny = o.alertShiny; alertLegendary = o.alertLegendary;
		alertMythical = o.alertMythical; alertUltraBeast = o.alertUltraBeast; alertParadox = o.alertParadox;
		ignoreOwned = o.ignoreOwned;
		speciesWhitelist = new ArrayList<>(o.speciesWhitelist); speciesBlacklist = new ArrayList<>(o.speciesBlacklist);
		extraLabels = new ArrayList<>(o.extraLabels);
		visualAlerts = o.visualAlerts; chatAlerts = o.chatAlerts; includeCoordinates = o.includeCoordinates;
		includeDistance = o.includeDistance; displayDurationSeconds = o.displayDurationSeconds;
		maxVisibleBanners = o.maxVisibleBanners; hudOffsetY = o.hudOffsetY;
		soundEnabled = o.soundEnabled; volume = o.volume; pitch = o.pitch; sounds = new LinkedHashMap<>(o.sounds);
		perSpeciesCooldownSeconds = o.perSpeciesCooldownSeconds; globalSoundCooldownSeconds = o.globalSoundCooldownSeconds;
		shinyBypassesCooldown = o.shinyBypassesCooldown; entityMemorySeconds = o.entityMemorySeconds;
		maxDistance = o.maxDistance; settleTicks = o.settleTicks;
		sanitize();
	}
}
