package io.github.spawnnotifyclient.client;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import io.github.spawnnotifyclient.core.PokemonInfo;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The only place that reads Cobblemon's data classes. Everything it touches is data that the
 * server already sends to every client that can see the entity (species, form, shiny flag).
 */
final class PokemonReader {
	private PokemonReader() {}

	record Read(PokemonInfo info, Component displayName) {}

	static Read read(PokemonEntity entity) {
		Pokemon pokemon = entity.getPokemon();
		Species species = pokemon.getSpecies();
		String id = species.getResourceIdentifier().getPath();
		Set<String> labels = new HashSet<>();
		for (String label : pokemon.getForm().getLabels()) {
			labels.add(label.toLowerCase(Locale.ROOT));
		}
		PokemonInfo info = new PokemonInfo(id, species.getName(), labels, pokemon.getShiny());
		return new Read(info, species.getTranslatedName());
	}
}
