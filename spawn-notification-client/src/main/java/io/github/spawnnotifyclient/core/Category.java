package io.github.spawnnotifyclient.core;

/**
 * Alert categories, in priority order (the first matching category is the "primary" one that
 * decides colour and sound). Pure data: no Minecraft types, so it can be unit-tested anywhere.
 */
public enum Category {
	SHINY("GOLD", "cobblemon:particle.wild_shiny_chime", null),
	LEGENDARY("LIGHT_PURPLE", "minecraft:ui.toast.challenge_complete", "legendary"),
	MYTHICAL("AQUA", "minecraft:block.beacon.activate", "mythical"),
	ULTRA_BEAST("RED", "minecraft:block.bell.use", "ultra_beast"),
	PARADOX("GREEN", "minecraft:block.amethyst_block.resonate", "paradox"),
	/** Species on the player's own "always alert" list, or carrying one of their extra labels. */
	CUSTOM("YELLOW", "minecraft:entity.experience_orb.pickup", null);

	/** Name of a {@code net.minecraft.ChatFormatting} constant. Resolved on the client side. */
	public final String colorName;
	/** Default sound event id. The player can override it per category in the config. */
	public final String defaultSound;
	/** Cobblemon species/form label that maps to this category, or null if not label-based. */
	public final String cobblemonLabel;

	Category(String colorName, String defaultSound, String cobblemonLabel) {
		this.colorName = colorName;
		this.defaultSound = defaultSound;
		this.cobblemonLabel = cobblemonLabel;
	}
}
