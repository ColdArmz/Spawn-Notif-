package io.github.spawnnotifyclient.client;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import io.github.spawnnotifyclient.SpawnNotifyClient;
import io.github.spawnnotifyclient.config.ModConfig;
import io.github.spawnnotifyclient.core.AlertTracker;
import io.github.spawnnotifyclient.core.Evaluator;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Event-driven detection. There is no world scanning: Fabric tells us when the client starts or
 * stops tracking an entity, and we only look at Cobblemon Pokémon entities.
 *
 * <p>A Pokémon's species and shiny state arrive from the server shortly <em>after</em> the entity
 * is added, so reading it at load time could give a placeholder. Newly loaded entities therefore
 * wait in a small queue for {@code settleTicks} client ticks and are read exactly once.
 */
public final class EntityWatcher {
	private static final int MAX_PER_TICK = 16;
	private static final int MAX_LOGGED_FAILURES = 3;
	private static final int FAILURES_BEFORE_DISABLE = 25;

	private record Pending(PokemonEntity entity, long loadedAtTick) {}

	private final AlertTracker tracker;
	private final Map<UUID, Pending> pending = new LinkedHashMap<>();
	private ClientLevel currentLevel;
	private long tick;
	private int failures;
	private boolean disabledByErrors;

	public EntityWatcher(AlertTracker tracker) {
		this.tracker = tracker;
	}

	public void register() {
		ClientEntityEvents.ENTITY_LOAD.register(this::onLoad);
		ClientEntityEvents.ENTITY_UNLOAD.register(this::onUnload);
		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
		// New server session: drop all queued work and alert memory.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
	}

	/** Forget everything (join, disconnect, /spawnnotify reload). */
	public void reset() {
		pending.clear();
		tracker.clear();
		currentLevel = null;
		failures = 0;
		disabledByErrors = false;
		SpawnNotifyClient.hud().clear();
	}

	private void onLoad(Entity entity, ClientLevel level) {
		syncLevel(level);
		if (disabledByErrors || !SpawnNotifyClient.config().enabled) {
			return;
		}
		if (entity instanceof PokemonEntity pokemonEntity) {
			pending.put(entity.getUUID(), new Pending(pokemonEntity, tick));
		}
	}

	private void onUnload(Entity entity, ClientLevel level) {
		if (!pending.isEmpty()) {
			pending.remove(entity.getUUID());
		}
	}

	/** A different ClientLevel instance means a dimension change or a fresh world: drop queued entities. */
	private void syncLevel(ClientLevel level) {
		if (level != currentLevel) {
			pending.clear();
			currentLevel = level;
		}
	}

	private void onTick(Minecraft mc) {
		tick++;
		if (mc.level == null) {
			pending.clear();
			currentLevel = null;
			return;
		}
		syncLevel(mc.level);
		ModConfig cfg = SpawnNotifyClient.config();

		if (tick % 20 == 0) {
			tracker.prune(System.currentTimeMillis(), cfg);
		}
		if (pending.isEmpty()) {
			return;
		}
		if (!cfg.enabled || disabledByErrors) {
			pending.clear();
			return;
		}

		int processed = 0;
		for (Iterator<Pending> it = pending.values().iterator(); it.hasNext() && processed < MAX_PER_TICK; ) {
			Pending p = it.next();
			if (tick - p.loadedAtTick() < cfg.settleTicks) {
				continue;
			}
			it.remove();
			processed++;
			try {
				process(p.entity(), mc, cfg);
			} catch (Throwable t) { // LinkageError included: guards against a Cobblemon API mismatch
				onFailure(t);
			}
		}
	}

	private void process(PokemonEntity entity, Minecraft mc, ModConfig cfg) {
		if (entity.isRemoved() || mc.player == null) {
			return;
		}
		Entity asEntity = entity;
		if (cfg.ignoreOwned && asEntity instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null) {
			return;
		}
		double distance = mc.player.distanceTo(entity);
		if (cfg.maxDistance > 0 && distance > cfg.maxDistance) {
			return;
		}

		PokemonReader.Read read = PokemonReader.read(entity);
		Evaluator.Result result = Evaluator.evaluate(read.info(), cfg);
		if (!result.isAlert()) {
			return;
		}

		AlertTracker.Verdict verdict = tracker.decide(entity.getUUID(), read.info().speciesId(),
				result.categories(), System.currentTimeMillis(), cfg);
		if (verdict == AlertTracker.Verdict.SUPPRESS) {
			return;
		}
		AlertEvent event = new AlertEvent(read.displayName(), result.categories(), result.primary(),
				entity.getBlockX(), entity.getBlockY(), entity.getBlockZ(), distance);
		AlertDispatcher.dispatch(event, verdict == AlertTracker.Verdict.ALERT);
	}

	private void onFailure(Throwable t) {
		failures++;
		if (failures <= MAX_LOGGED_FAILURES) {
			SpawnNotifyClient.LOGGER.warn("Failed to evaluate a Pokémon entity (is this Cobblemon version supported?)", t);
		}
		if (failures >= FAILURES_BEFORE_DISABLE && !disabledByErrors) {
			disabledByErrors = true;
			SpawnNotifyClient.LOGGER.error("Too many errors; Spawn Notification Client is pausing detection until reconnect.");
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null) {
				mc.player.displayClientMessage(Component.translatable("spawnnotifyclient.error.disabled"), false);
			}
		}
	}
}
