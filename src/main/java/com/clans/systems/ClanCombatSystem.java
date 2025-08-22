package com.clans.systems;

import com.clans.Simpleclans;
import com.clans.data.ClanDataManager;
import com.clans.model.Clan;
import com.clans.util.ClanUtils;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;

// 🔧 FIXED: Added missing imports for status effects
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

import java.util.UUID;
import java.util.WeakHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public class ClanCombatSystem {
    
    // Enhanced tracking for kill attribution
    private final Map<ServerPlayerEntity, ServerPlayerEntity> lastDamagers = new WeakHashMap<>();
    private final Map<ServerPlayerEntity, Long> lastDamageTime = new WeakHashMap<>();
    private static final long DAMAGE_TIMEOUT = 10000; // 10 seconds to attribute kill
    
    // 🔧 Prevent infinite loop by tracking processed damage
    private final Set<LivingEntity> processingDamage = new HashSet<>();
    
    public void initialize() {
        // Register PvP attack events for damage calculation and kill attribution
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayerEntity attacker && entity instanceof ServerPlayerEntity victim) {
                // Track last damager with timestamp for kill attribution
                lastDamagers.put(victim, attacker);
                lastDamageTime.put(victim, System.currentTimeMillis());
                
                return handlePvPAttack(attacker, victim);
            }
            return ActionResult.PASS;
        });
        
        Simpleclans.LOGGER.info("🚀 Enhanced clan combat system initialized!");
    }
    
    private ActionResult handlePvPAttack(ServerPlayerEntity attacker, ServerPlayerEntity victim) {
        ClanDataManager dataManager = Simpleclans.getDataManager();
        
        Clan attackerClan = dataManager.getPlayerClan(attacker.getUuid());
        Clan victimClan = dataManager.getPlayerClan(victim.getUuid());
        
        // Same clan members cannot attack each other (friendly fire protection)
        if (attackerClan != null && victimClan != null && 
            attackerClan.getName().equals(victimClan.getName())) {
            attacker.sendMessage(Text.literal("⛔ You cannot attack your clan members!")
                .formatted(Formatting.RED), false);
            return ActionResult.FAIL;
        }
        
        // Allied clans cannot attack each other
        if (attackerClan != null && victimClan != null) {
            String victimClanKey = victimClan.getName().toLowerCase();
            if (attackerClan.getAllies().contains(victimClanKey)) {
                attacker.sendMessage(Text.literal("⛔ You cannot attack allied clan members!")
                    .formatted(Formatting.RED), false);
                return ActionResult.FAIL;
            }
        }
        
        return ActionResult.PASS;
    }
    
    // 🔧 Safe damage handling with proper loop prevention
    public boolean handleDamageEvent(LivingEntity entity, DamageSource damageSource, float damageAmount) {
        // 🔧 CRITICAL: Prevent infinite loops by ignoring magic damage from our system
        if (damageSource.isOf(DamageTypes.MAGIC) || processingDamage.contains(entity)) {
            return true; // Allow damage but don't process it
        }
        
        // Only process if attacker is a player
        if (!(damageSource.getAttacker() instanceof ServerPlayerEntity attacker)) {
            return true; // Allow damage if no player attacker
        }
        
        ClanDataManager dataManager = Simpleclans.getDataManager();
        Clan attackerClan = dataManager.getPlayerClan(attacker.getUuid());
        
        if (attackerClan == null) {
            return true; // No clan buffs to apply
        }
        
        // Calculate damage multiplier
        float damageMultiplier = 1.0f;
        
        // Proximity buff calculation (applies to ALL targets)
        int nearbyMembers = getNearbyMembersCount(attacker, attackerClan);
        if (nearbyMembers > 0) {
            float proximityBonus = nearbyMembers * (float)Simpleclans.getConfig().proximityDamageBonus;
            damageMultiplier += proximityBonus;
            
            Simpleclans.LOGGER.debug("🏹 Proximity bonus applied: +{}% ({} nearby members)", 
                proximityBonus * 100, nearbyMembers);
        }
        
        // Handle PvP-specific logic
        if (entity instanceof ServerPlayerEntity victim) {
            Clan victimClan = dataManager.getPlayerClan(victim.getUuid());
            
            // Prevent friendly fire (clan members)
            if (victimClan != null && victimClan.getName().equals(attackerClan.getName())) {
                Simpleclans.LOGGER.debug("⛔ Blocked friendly fire damage");
                return false; // Block clan member damage
            }
            
            // Prevent ally damage
            if (victimClan != null) {
                String victimKey = victimClan.getName().toLowerCase();
                if (attackerClan.getAllies().contains(victimKey)) {
                    Simpleclans.LOGGER.debug("⛔ Blocked ally damage");
                    return false; // Block ally damage
                }
                
                // Apply enemy damage bonus
                if (attackerClan.getEnemies().contains(victimKey)) {
                    float enemyBonus = (float)Simpleclans.getConfig().enemyDamageBonus;
                    damageMultiplier += enemyBonus;
                    
                    Simpleclans.LOGGER.debug("⚔️ Enemy bonus applied: +{}%", enemyBonus * 100);
                }
            }
        }
        
        // 🔧 SAFE: Apply additional damage with proper loop prevention
        if (damageMultiplier > 1.0f && entity.getWorld() instanceof ServerWorld serverWorld) {
            // Mark entity as being processed to prevent loops
            processingDamage.add(entity);
            
            try {
                float additionalDamage = damageAmount * (damageMultiplier - 1.0f);
                
                // Apply additional damage with magic damage type (filtered out above)
                entity.damage(serverWorld, serverWorld.getDamageSources().magic(), additionalDamage);
                
                Simpleclans.LOGGER.debug("💥 Applied {}x damage multiplier ({} additional damage)", 
                    damageMultiplier, additionalDamage);
                    
            } finally {
                // Always remove from processing set
                processingDamage.remove(entity);
            }
        }
        
        return true; // Allow original damage
    }
    
    private int getNearbyMembersCount(ServerPlayerEntity player, Clan clan) {
        int count = 0;
        double proximityRadius = Simpleclans.getConfig().proximityRadius;
        
        for (UUID memberUuid : clan.getMembers()) {
            if (memberUuid.equals(player.getUuid())) continue; // Don't count self
            
            ServerPlayerEntity member = player.getServer().getPlayerManager().getPlayer(memberUuid);
            if (member != null && ClanUtils.getDistance(player, member) <= proximityRadius) {
                count++;
            }
        }
        return count;
    }
    
    // 🔧 Enhanced kill tracking with better logging
    public void handlePlayerDeath(ServerPlayerEntity victim) {
        ClanDataManager dataManager = Simpleclans.getDataManager();
        
        // Find the killer with timeout check
        ServerPlayerEntity killer = getValidKiller(victim);
        
        Clan victimClan = dataManager.getPlayerClan(victim.getUuid());
        if (victimClan != null) {
            victimClan.addDeath();
            Simpleclans.LOGGER.info("💀 Added death to clan: {} (Total deaths: {})", 
                victimClan.getOriginalName(), victimClan.getDeaths());
        }
        
        if (killer != null) {
            Clan killerClan = dataManager.getPlayerClan(killer.getUuid());
            if (killerClan != null) {
                killerClan.addKill(killer.getUuid(), victim.getUuid());
                
                Simpleclans.LOGGER.info("⚔️ KILL TRACKED: {} ({}) killed {} ({}) | {} now has {} total kills", 
                    killer.getName().getString(), killerClan.getOriginalName(),
                    victim.getName().getString(), victimClan != null ? victimClan.getOriginalName() : "No Clan",
                    killerClan.getOriginalName(), killerClan.getKills());
                
                // Check for war victory condition
                checkWarVictory(killerClan, victimClan);
            }
        } else {
            Simpleclans.LOGGER.debug("No valid killer found for {} (timeout or no attacker)", 
                victim.getName().getString());
        }
        
        dataManager.save();
    }
    
    private void checkWarVictory(Clan killerClan, Clan victimClan) {
        if (killerClan == null || victimClan == null) return;
        
        // Check if both clans are at war with each other
        if (killerClan.isAtWar() && victimClan.isAtWar() &&
            killerClan.getWarTarget().equals(victimClan.getName().toLowerCase()) &&
            victimClan.getWarTarget().equals(killerClan.getName().toLowerCase())) {
            
            // Check if killer clan has killed all enemy members
            if (killerClan.hasKilledAllEnemies(victimClan.getMembers())) {
                handleWarVictory(killerClan, victimClan);
            }
        }
    }
    
    private ServerPlayerEntity getValidKiller(ServerPlayerEntity victim) {
        ServerPlayerEntity killer = lastDamagers.get(victim);
        if (killer == null) return null;
        
        Long damageTime = lastDamageTime.get(victim);
        if (damageTime == null || (System.currentTimeMillis() - damageTime) > DAMAGE_TIMEOUT) {
            return null; // Damage too old to attribute
        }
        
        return killer;
    }
    
    private void handleWarVictory(Clan winnerClan, Clan loserClan) {
        ClanDataManager dataManager = Simpleclans.getDataManager();
        
        Simpleclans.LOGGER.info("🏆 War victory triggered for clan: {}", winnerClan.getOriginalName());
        
        // Give rewards to winning clan
        for (UUID memberUuid : winnerClan.getMembers()) {
            ServerPlayerEntity member = dataManager.getServer().getPlayerManager().getPlayer(memberUuid);
            if (member != null) {
                // Give netherite and gold to all members
                ItemStack netherite = new ItemStack(Items.NETHERITE_INGOT, 
                    Simpleclans.getConfig().warWinnerNetherite);
                ItemStack gold = new ItemStack(Items.GOLD_INGOT, 
                    Simpleclans.getConfig().warWinnerGold);
                
                member.getInventory().insertStack(netherite);
                member.getInventory().insertStack(gold);
                
                // Give king extra netherite ingot
                if (winnerClan.isKing(memberUuid)) {
                    ItemStack extraNetherite = new ItemStack(Items.NETHERITE_INGOT, 1);
                    member.getInventory().insertStack(extraNetherite);
                    
                    member.sendMessage(Text.literal("You received an extra netherite ingot for leading your clan to victory!")
                        .formatted(Formatting.GOLD), false);
                }
                
                // 🔧 FIXED: Now with proper imports, these will compile correctly
                // Apply 2-hour speed and strength buffs
                int duration = Simpleclans.getConfig().warBuffDurationHours * 60 * 60 * 20; // Convert to ticks
                member.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, duration, 0, false, true));
                member.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, duration, 0, false, true));
                
                member.sendMessage(Text.literal("🏆 Your clan won the war! You received rewards and buffs!")
                    .formatted(Formatting.GOLD), false);
            }
        }
        
        // Broadcast victory
        Text victoryMessage = Text.literal("🏆 WAR ENDED! " + winnerClan.getOriginalName() + " defeated " + loserClan.getOriginalName())
            .formatted(Formatting.GOLD, Formatting.BOLD);
        
        ClanUtils.broadcastToClan(winnerClan, victoryMessage, dataManager.getServer());
        ClanUtils.broadcastToClan(loserClan, victoryMessage, dataManager.getServer());
        
        // Reset war status
        winnerClan.resetWar();
        loserClan.resetWar();
        
        Simpleclans.LOGGER.info("War ended: {} defeated {}", winnerClan.getOriginalName(), loserClan.getOriginalName());
    }
}