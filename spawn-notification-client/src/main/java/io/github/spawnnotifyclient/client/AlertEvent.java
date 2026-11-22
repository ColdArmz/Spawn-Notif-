package io.github.spawnnotifyclient.client;

import io.github.spawnnotifyclient.core.Category;
import net.minecraft.network.chat.Component;

import java.util.EnumSet;

/** Everything needed to present one alert. */
record AlertEvent(Component pokemonName, EnumSet<Category> categories, Category primary,
				  int x, int y, int z, double distance) {
}
