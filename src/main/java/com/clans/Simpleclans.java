package com.clans;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.util.ActionResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.clans.commands.ClanCommand;
import com.clans.commands.ClanAdminCommand;
import com.clans.data.ClanDataManager;
import com.clans.data.ClanMemberStatus;
import com.clans.config.ClanConfig;
import com.clans.systems.ClanTimerSystem;
import com.clans.systems.ClanProximitySystem;
import com.clans.systems.ClanCombatSystem;
import com.clans.systems.ClanChatSystem;

public class Simpleclans implements ModInitializer {
	public static final String MOD_ID = "simpleclans";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static ClanDataManager dataManager;
	private static ClanConfig config;
	private static ClanTimerSystem timerSystem;
	private static ClanProximitySystem proximitySystem;
	private static ClanCombatSystem combatSystem;
	private static ClanChatSystem chatSystem;

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Simple Clans Mod...");
		
		// Initialize config first
		config = ClanConfig.loadOrCreate();
		
		// Initialize data manager
		dataManager = new ClanDataManager();
		dataManager.load();
		
		// Initialize systems
		timerSystem = new ClanTimerSystem();
		proximitySystem = new ClanProximitySystem();
		combatSystem = new ClanCombatSystem();
		chatSystem = new ClanChatSystem();
		
		// Register commands
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			ClanCommand.register(dispatcher);
			ClanAdminCommand.register(dispatcher);
		});
		
		// Register server lifecycle events
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			dataManager.setServer(server);
			timerSystem.initialize(server);
			proximitySystem.initialize(server);
			combatSystem.initialize();
			chatSystem.initialize();
		});
		
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			if (dataManager != null) {
				dataManager.save();
			}
			if (timerSystem != null) {
				timerSystem.shutdown();
			}
			if (proximitySystem != null) {
				proximitySystem.shutdown();
			}
		});
		
		// Register player connection events
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ClanMemberStatus.setOnline(handler.player.getUuid());
			dataManager.onPlayerJoin(handler.player);
		});
		
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ClanMemberStatus.setOffline(handler.player.getUuid());
			dataManager.onPlayerDisconnect(handler.player);
		});
		
		// 🔧 FIXED: Use proper death event for kill tracking
		ServerLivingEntityEvents.AFTER_DEATH.register((LivingEntity entity, DamageSource damageSource) -> {
			if (entity instanceof ServerPlayerEntity deadPlayer) {
				combatSystem.handlePlayerDeath(deadPlayer);
			}
		});
		
		// Register damage events for combat bonuses
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(combatSystem::handleDamageEvent);
		
		LOGGER.info("Simple Clans Mod initialized successfully!");
	}
	
	// Getters for systems
	public static ClanDataManager getDataManager() { return dataManager; }
	public static ClanConfig getConfig() { return config; }
	public static ClanTimerSystem getTimerSystem() { return timerSystem; }
	public static ClanProximitySystem getProximitySystem() { return proximitySystem; }
	public static ClanCombatSystem getCombatSystem() { return combatSystem; }
	public static ClanChatSystem getChatSystem() { return chatSystem; }
}