package dev.brian.farmhand;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.Villager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Farmhand implements ModInitializer {
	public static final String MOD_ID = "farmhand";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private final FarmerDepositor depositor = new FarmerDepositor();

	@Override
	public void onInitialize() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Villager villager) depositor.track(villager);
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Villager villager) depositor.untrack(villager);
		});
		ServerTickEvents.END_SERVER_TICK.register(depositor::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> depositor.clear());
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	/** Translation key following the vanilla convention: {@code category.modid.path}. */
	public static String langKey(String category, String path) {
		return category + "." + MOD_ID + "." + path;
	}
}
