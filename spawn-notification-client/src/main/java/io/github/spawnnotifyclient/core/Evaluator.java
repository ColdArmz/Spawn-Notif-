package io.github.spawnnotifyclient.core;

import io.github.spawnnotifyclient.config.ModConfig;

import java.util.EnumSet;

/** Decides which alert categories a Pokémon falls into under the player's configuration. */
public final class Evaluator {
	private Evaluator() {}

	public record Result(EnumSet<Category> categories) {
		public boolean isAlert() {
			return !categories.isEmpty();
		}

		/** Highest-priority category (enum order). Only valid when {@link #isAlert()}. */
		public Category primary() {
			return categories.iterator().next();
		}
	}

	public static Result evaluate(PokemonInfo info, ModConfig cfg) {
		EnumSet<Category> cats = EnumSet.noneOf(Category.class);
		String species = Names.norm(info.speciesId());
		if (species.isEmpty()) {
			species = Names.norm(info.speciesName());
		}
		// The never-alert list always wins, even over shiny.
		if (cfg.blacklistSet().contains(species)) {
			return new Result(cats);
		}
		if (cfg.alertShiny && info.shiny()) {
			cats.add(Category.SHINY);
		}
		for (String label : info.labels()) {
			String l = Names.norm(label);
			if (cfg.alertLegendary && l.equals("legendary")) cats.add(Category.LEGENDARY);
			if (cfg.alertMythical && l.equals("mythical")) cats.add(Category.MYTHICAL);
			if (cfg.alertUltraBeast && l.equals("ultrabeast")) cats.add(Category.ULTRA_BEAST);
			if (cfg.alertParadox && l.equals("paradox")) cats.add(Category.PARADOX);
			if (cfg.extraLabelSet().contains(l)) cats.add(Category.CUSTOM);
		}
		if (cfg.whitelistSet().contains(species)) {
			cats.add(Category.CUSTOM);
		}
		return new Result(cats);
	}
}
