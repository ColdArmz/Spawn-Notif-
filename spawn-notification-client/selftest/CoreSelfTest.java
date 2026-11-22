import io.github.spawnnotifyclient.config.ModConfig;
import io.github.spawnnotifyclient.core.*;

import java.nio.file.*;
import java.util.*;

/** Dependency-free tests for the Minecraft-independent logic. Run with selftest/run.sh. */
public class CoreSelfTest {
	static int checks = 0;

	static void check(boolean ok, String what) {
		checks++;
		if (!ok) throw new AssertionError("FAILED: " + what);
	}

	static PokemonInfo mon(String id, boolean shiny, String... labels) {
		return new PokemonInfo(id, id, new HashSet<>(Arrays.asList(labels)), shiny);
	}

	static ModConfig cfg() {
		ModConfig c = new ModConfig();
		c.sanitize();
		return c;
	}

	public static void main(String[] args) throws Exception {
		// --- Names ---
		check(Names.norm("Mr. Mime").equals("mrmime"), "norm strips punctuation/case");
		check(Names.norm("cobblemon:mr_mime").equals("mrmime"), "norm strips namespace/underscore");
		check(Names.norm(null).isEmpty(), "norm(null)");

		// --- Evaluator: categories ---
		ModConfig c = cfg();
		check(!Evaluator.evaluate(mon("rattata", false), c).isAlert(), "common mon does not alert");
		check(Evaluator.evaluate(mon("rattata", true), c).categories().equals(EnumSet.of(Category.SHINY)), "shiny");
		check(Evaluator.evaluate(mon("mewtwo", false, "legendary"), c).primary() == Category.LEGENDARY, "legendary");
		check(Evaluator.evaluate(mon("mew", false, "mythical"), c).primary() == Category.MYTHICAL, "mythical");
		check(Evaluator.evaluate(mon("nihilego", false, "ultra_beast"), c).primary() == Category.ULTRA_BEAST, "ultra beast (underscore label)");
		check(Evaluator.evaluate(mon("greattusk", false, "paradox"), c).primary() == Category.PARADOX, "paradox");
		Evaluator.Result both = Evaluator.evaluate(mon("mewtwo", true, "legendary"), c);
		check(both.categories().equals(EnumSet.of(Category.SHINY, Category.LEGENDARY)) && both.primary() == Category.SHINY,
				"shiny legendary has both, shiny is primary");

		// --- Evaluator: toggles ---
		c.alertLegendary = false;
		check(!Evaluator.evaluate(mon("mewtwo", false, "legendary"), c).isAlert(), "legendary toggle off");
		check(Evaluator.evaluate(mon("mewtwo", true, "legendary"), c).categories().equals(EnumSet.of(Category.SHINY)), "shiny legendary with legendary off -> shiny only");
		c.alertLegendary = true;

		// --- Evaluator: lists ---
		c.speciesWhitelist = new ArrayList<>(List.of("Pikachu", "mr. mime"));
		c.speciesBlacklist = new ArrayList<>(List.of("cobblemon:Mewtwo"));
		c.extraLabels = new ArrayList<>(List.of("Restricted"));
		c.sanitize();
		check(Evaluator.evaluate(mon("pikachu", false), c).primary() == Category.CUSTOM, "whitelist species alerts");
		check(Evaluator.evaluate(mon("mr_mime", false), c).primary() == Category.CUSTOM, "whitelist normalises names");
		check(!Evaluator.evaluate(mon("mewtwo", true, "legendary"), c).isAlert(), "blacklist beats shiny+legendary");
		check(Evaluator.evaluate(mon("zacian", false, "restricted"), c).primary() == Category.CUSTOM, "extra label alerts");
		check(!Evaluator.evaluate(mon("zacian", false, "baby"), c).isAlert(), "other labels do not");
		c.shinyBypassesCooldown = true;

		// --- AlertTracker ---
		ModConfig t = cfg();
		AlertTracker tr = new AlertTracker();
		UUID a = UUID.randomUUID(), b = UUID.randomUUID(), d = UUID.randomUUID();
		EnumSet<Category> leg = EnumSet.of(Category.LEGENDARY), shiny = EnumSet.of(Category.SHINY);
		long T0 = 1_000_000;
		check(tr.decide(a, "mewtwo", leg, T0, t) == AlertTracker.Verdict.ALERT, "first alert");
		check(tr.decide(a, "mewtwo", leg, T0 + 1000, t) == AlertTracker.Verdict.SUPPRESS, "same entity suppressed (dedupe)");
		check(tr.decide(a, "mewtwo", leg, T0 + 600_000, t) == AlertTracker.Verdict.SUPPRESS, "entity remembered across reload within memory window");
		check(tr.decide(b, "mewtwo", leg, T0 + 5_000, t) == AlertTracker.Verdict.SUPPRESS, "same species inside species cooldown");
		check(tr.decide(d, "mew", leg, T0 + 500, t) == AlertTracker.Verdict.ALERT_SILENT, "different species inside sound cooldown: shown without sound");
		UUID e = UUID.randomUUID();
		check(tr.decide(e, "mewtwo", shiny, T0 + 5_500, t) == AlertTracker.Verdict.ALERT, "shiny bypasses species cooldown and sound cooldown");
		UUID f = UUID.randomUUID();
		check(tr.decide(f, "mewtwo", leg, T0 + 40_000, t) == AlertTracker.Verdict.ALERT, "species alerts again after cooldown");
		t.shinyBypassesCooldown = false;
		UUID g = UUID.randomUUID();
		check(tr.decide(g, "mewtwo", shiny, T0 + 41_000, t) == AlertTracker.Verdict.SUPPRESS, "bypass off: shiny obeys cooldown");
		tr.clear();
		check(tr.decide(a, "mewtwo", leg, T0, t) == AlertTracker.Verdict.ALERT, "after clear (reconnect) alerts again");
		// expiry of entity memory
		ModConfig m = cfg();
		m.entityMemorySeconds = 60;
		m.perSpeciesCooldownSeconds = 0;
		m.globalSoundCooldownSeconds = 0;
		m.sanitize();
		AlertTracker tr2 = new AlertTracker();
		check(tr2.decide(a, "x", leg, 0, m) == AlertTracker.Verdict.ALERT, "tr2 first");
		check(tr2.decide(a, "x", leg, 59_000, m) == AlertTracker.Verdict.SUPPRESS, "within memory");
		check(tr2.decide(a, "x", leg, 61_000, m) == AlertTracker.Verdict.ALERT, "after memory expiry re-alerts");
		tr2.prune(10_000_000, m);
		check(tr2.rememberedEntities() == 0, "prune drops expired entries");
		// cap
		AlertTracker tr3 = new AlertTracker();
		for (int i = 0; i < 6000; i++) tr3.decide(UUID.randomUUID(), "s" + i, leg, i, m);
		check(tr3.rememberedEntities() <= 4096, "memory is capped");

		// --- ModConfig ---
		ModConfig w = new ModConfig();
		w.volume = 7; w.pitch = -1; w.displayDurationSeconds = 9999; w.settleTicks = 0; w.maxVisibleBanners = 0;
		w.sounds = new LinkedHashMap<>(Map.of("shiny", "minecraft:entity.player.levelup", "bogus", "x"));
		w.sanitize();
		check(w.volume == 1.0 && w.pitch == 0.5 && w.displayDurationSeconds == 60 && w.settleTicks == 1 && w.maxVisibleBanners == 1, "sanitize clamps");
		check(w.soundFor(Category.SHINY).equals("minecraft:entity.player.levelup"), "sound override (case-insensitive key)");
		check(w.soundFor(Category.LEGENDARY).equals(Category.LEGENDARY.defaultSound), "unset sound falls back to default");
		check(!w.sounds.containsKey("bogus") && w.sounds.size() == Category.values().length, "unknown sound keys dropped");
		check(ModConfig.parseList(" a, B ;c\nd,, ").equals(List.of("a", "B", "c", "d")), "parseList");

		Path dir = Files.createTempDirectory("sncfg");
		Path file = dir.resolve("cfg.json");
		List<String> warnings = new ArrayList<>();
		ModConfig created = ModConfig.load(file, warnings::add);
		check(Files.exists(file) && warnings.isEmpty(), "fresh load writes defaults");
		created.alertShiny = false; created.speciesWhitelist = new ArrayList<>(List.of("ditto"));
		created.save(file, warnings::add);
		ModConfig again = ModConfig.load(file, warnings::add);
		check(!again.alertShiny && again.whitelistSet().contains("ditto"), "save/load round trip");
		Files.writeString(file, "{ \"volume\": 0.25 }");
		ModConfig partial = ModConfig.load(file, warnings::add);
		check(partial.volume == 0.25 && partial.alertLegendary && partial.sounds.size() == Category.values().length, "partial file keeps defaults for missing keys");
		Files.writeString(file, "{ not json");
		ModConfig broken = ModConfig.load(file, warnings::add);
		check(broken.enabled && !warnings.isEmpty() && Files.exists(dir.resolve("cfg.json.broken")), "corrupt file backed up, defaults used");

		System.out.println("CoreSelfTest: " + checks + " checks passed");
	}
}
