package com.clans.systems;

import com.clans.Simpleclans;
import com.clans.data.ClanDataManager;
import com.clans.model.Clan;
import com.clans.util.ClanUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ClanProximitySystem {
    private ScheduledExecutorService scheduler;
    private MinecraftServer server;
    private final Map<UUID, Integer> activeProximityBuffs = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> fullClanBonusActive = new ConcurrentHashMap<>();
    
    // Track current effect levels to avoid unnecessary reapplication
    private final Map<UUID, Integer> currentSpeedLevel = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> currentFullBonus = new ConcurrentHashMap<>();
    
    public void initialize(MinecraftServer server) {
        this.server = server;
        this.scheduler = Executors.newScheduledThreadPool(1);
        
        int checkInterval = Simpleclans.getConfig().proximityCheckSeconds;
        scheduler.scheduleAtFixedRate(this::updateProximityEffects, 
            checkInterval, checkInterval, TimeUnit.SECONDS);
        
        Simpleclans.LOGGER.info("✅ Enhanced clan proximity system initialized ({}s intervals, {}m range)", 
            checkInterval, Simpleclans.getConfig().proximityRadius);
    }
    
    public void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
        }
    }
    
    private void updateProximityEffects() {
        try {
            ClanDataManager dataManager = Simpleclans.getDataManager();
            Map<UUID, Integer> newBuffs = new HashMap<>();
            Map<UUID, Boolean> newFullBonuses = new HashMap<>();
            
            // Process each clan
            for (Clan clan : dataManager.getAllClans()) {
                List<ServerPlayerEntity> onlineMembers = new ArrayList<>();
                
                // Get online clan members
                for (UUID memberUuid : clan.getMembers()) {
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(memberUuid);
                    if (player != null) {
                        onlineMembers.add(player);
                    }
                }
                
                // Calculate proximity bonuses for each member
                for (ServerPlayerEntity player : onlineMembers) {
                    int nearbyMembers = 0;
                    
                    for (ServerPlayerEntity other : onlineMembers) {
                        if (!player.equals(other) && 
                            ClanUtils.getDistance(player, other) <= Simpleclans.getConfig().proximityRadius) {
                            nearbyMembers++;
                        }
                    }
                    
                    // Store buff level
                    newBuffs.put(player.getUuid(), nearbyMembers);
                    
                    // Check for full clan bonus (all 4 members within range)
                    boolean hasFullBonus = false;
                    if (nearbyMembers > 0) {
                        if (clan.getMembers().size() >= 4 && onlineMembers.size() >= 4 && nearbyMembers >= 3) {
                            // Verify all 4 members are actually close to each other
                            int tightGroupMembers = 0;
                            for (ServerPlayerEntity member : onlineMembers) {
                                int closeCount = 0;
                                for (ServerPlayerEntity other : onlineMembers) {
                                    if (!member.equals(other) && 
                                        ClanUtils.getDistance(member, other) <= Simpleclans.getConfig().proximityRadius) {
                                        closeCount++;
                                    }
                                }
                                if (closeCount >= 3) {
                                    tightGroupMembers++;
                                }
                            }
                            hasFullBonus = (tightGroupMembers >= 4);
                        }
                        
                        // Apply effects only if they changed
                        applyProximityEffects(player, nearbyMembers, hasFullBonus);
                        updateActionBar(player, nearbyMembers, hasFullBonus);
                    } else {
                        // Remove effects if no nearby members
                        removeProximityEffects(player);
                    }
                    
                    newFullBonuses.put(player.getUuid(), hasFullBonus);
                }
            }
            
            // Update tracking
            activeProximityBuffs.clear();
            activeProximityBuffs.putAll(newBuffs);
            fullClanBonusActive.clear();
            fullClanBonusActive.putAll(newFullBonuses);
            
        } catch (Exception e) {
            Simpleclans.LOGGER.error("Error updating proximity effects", e);
        }
    }
    
    // 🔧 FIXED: Proper speed effect calculation for 10% per member
    private void applyProximityEffects(ServerPlayerEntity player, int nearbyMembers, boolean hasFullBonus) {
        UUID playerId = player.getUuid();
        int duration = (Simpleclans.getConfig().proximityCheckSeconds + 2) * 20; // Effect duration in ticks
        
        // Calculate speed level to achieve 10% per nearby member
        // Since Speed Level 0 = +20%, Level 1 = +40%, Level 2 = +60%, Level 3 = +80%
        // We need custom calculation for 10%, 20%, 30%, 40%
        int desiredSpeedLevel = Math.min(nearbyMembers, 4) - 1; // -1 because Level 0 is the minimum
        
        // Only apply if changed to avoid effect flickering
        Integer currentSpeed = currentSpeedLevel.get(playerId);
        Boolean currentFull = currentFullBonus.get(playerId);
        
        if (currentSpeed == null || !currentSpeed.equals(desiredSpeedLevel)) {
            // Remove old speed effect first
            if (player.hasStatusEffect(StatusEffects.SPEED)) {
                player.removeStatusEffect(StatusEffects.SPEED);
            }
            
            // Apply new speed effect
            if (desiredSpeedLevel >= 0) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, duration, desiredSpeedLevel, true, false));
                currentSpeedLevel.put(playerId, desiredSpeedLevel);
                
                Simpleclans.LOGGER.debug("Applied Speed Level {} to {} ({} nearby members)", 
                    desiredSpeedLevel, player.getName().getString(), nearbyMembers);
            }
        }
        
        // Apply full clan bonus effects
        if (hasFullBonus && (currentFull == null || !currentFull)) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, duration, 0, true, false));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, duration, 0, true, false));
            currentFullBonus.put(playerId, true);
            
            Simpleclans.LOGGER.debug("Applied full clan bonus to {}", player.getName().getString());
        } else if (!hasFullBonus && currentFull != null && currentFull) {
            // Remove full bonus effects if no longer applicable
            player.removeStatusEffect(StatusEffects.RESISTANCE);
            player.removeStatusEffect(StatusEffects.HASTE);
            currentFullBonus.put(playerId, false);
        }
    }
    
    // 🆕 NEW: Remove proximity effects when no members nearby
    private void removeProximityEffects(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        
        // Remove speed effects
        if (player.hasStatusEffect(StatusEffects.SPEED)) {
            player.removeStatusEffect(StatusEffects.SPEED);
        }
        
        // Remove full bonus effects
        player.removeStatusEffect(StatusEffects.RESISTANCE);
        player.removeStatusEffect(StatusEffects.HASTE);
        
        // Clear tracking
        currentSpeedLevel.remove(playerId);
        currentFullBonus.remove(playerId);
    }
    
    private void updateActionBar(ServerPlayerEntity player, int nearbyMembers, boolean hasFullBonus) {
        double speedBonus = nearbyMembers * Simpleclans.getConfig().proximitySpeedBonus * 100;
        double damageBonus = nearbyMembers * Simpleclans.getConfig().proximityDamageBonus * 100;
        
        String bonusText = hasFullBonus ? " + FULL CLAN BONUS!" : "";
        Text message = Text.literal(String.format("⚡ Clan Buffs: +%.0f%% Speed, +%.0f%% Damage%s", 
                speedBonus, damageBonus, bonusText))
            .formatted(hasFullBonus ? Formatting.GOLD : Formatting.YELLOW);
        
        player.sendMessage(message, true); // true = action bar
    }
    
    public int getProximityBonus(UUID playerId) {
        return activeProximityBuffs.getOrDefault(playerId, 0);
    }
    
    public boolean hasFullClanBonus(UUID playerId) {
        return fullClanBonusActive.getOrDefault(playerId, false);
    }
}

