package io.github.spawnnotifyclient.core;

import java.util.Set;

/**
 * The few client-visible facts about a Pokémon that matter for alerting. Built from
 * Cobblemon's classes in {@code client.PokemonReader}; everything downstream is plain Java.
 *
 * @param speciesId   species resource path, e.g. {@code mewtwo} (any case; normalised on use)
 * @param speciesName display name of the species, e.g. {@code Mewtwo}
 * @param labels      Cobblemon form labels, e.g. {@code legendary}, {@code ultra_beast}
 * @param shiny       whether the entity is shiny
 */
public record PokemonInfo(String speciesId, String speciesName, Set<String> labels, boolean shiny) {
}
