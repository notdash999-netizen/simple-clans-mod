package com.clans.util;

import com.clans.model.Clan;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.HashSet;
import java.util.Set;
import com.clans.commands.ClanCommand;
import com.clans.data.ClanDataManager;
import com.clans.Simpleclans;
import net.minecraft.item.Items;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.text.MutableText;
import java.util.concurrent.ConcurrentHashMap;

public class ClanUtils {
    // Confirmation system for dangerous actions
    private static final Map<UUID, Map<String, Long>> confirmations = new HashMap<>();
    private static final long CONFIRMATION_TIMEOUT = 30000; // 30 seconds
    
    // FIXED: Simple daily operation tracking without complex schedulers
    private static final Map<UUID, Map<String, Integer>> dailyOperations = new ConcurrentHashMap<>();
    private static long lastMidnightReset = System.currentTimeMillis() - (System.currentTimeMillis() % (24 * 60 * 60 * 1000));
    
    // Enhanced clan name validation
    private static final Pattern VALID_CLAN_NAME = Pattern.compile("^[a-zA-Z]{4,12}$");
    
    public static boolean isValidClanName(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        return VALID_CLAN_NAME.matcher(name).matches();
    }
    
    public static String validateClanName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "❌ Clan name cannot be empty!";
        }
        if (name.length() < 4) {
            return "❌ Clan name must be at least 4 characters long!";
        }
        if (name.length() > 12) {
            return "❌ Clan name cannot exceed 12 characters!";
        }
        if (!name.matches("^[a-zA-Z]+$")) {
            return "❌ Clan name can only contain letters!";
        }
        return null; // Valid name
    }
    
    public static boolean consumeItemsWithFeedback(ServerPlayerEntity player, Item item, int count) {
        if (consumeItems(player, item, count)) {
            // Send gold consumption feedback
            String itemName = getItemDisplayName(item);
            player.sendMessage(Text.literal("💰 " + count + " " + itemName + " consumed for this command.")
                .formatted(Formatting.YELLOW), false);
            return true;
        }
        return false;
    }
    
    private static String getItemDisplayName(Item item) {
        if (item.toString().contains("gold_ingot")) return "gold ingots";
        if (item.toString().contains("netherite_ingot")) return "netherite ingots";
        return "items";
    }
    
    public static boolean consumeItems(ServerPlayerEntity player, Item item, int count) {
        int remaining = count;
        
        ItemStack mainHand = player.getStackInHand(Hand.MAIN_HAND);
        if (mainHand.getItem() == item) {
            int consumed = Math.min(remaining, mainHand.getCount());
            mainHand.decrement(consumed);
            remaining -= consumed;
            
            if (remaining == 0) {
                return true;
            }
        }
        
        ItemStack offHand = player.getStackInHand(Hand.OFF_HAND);
        if (offHand.getItem() == item) {
            int consumed = Math.min(remaining, offHand.getCount());
            offHand.decrement(consumed);
            remaining -= consumed;
            
            if (remaining == 0) {
                return true;
            }
        }
        
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.getItem() == item) {
                int consumed = Math.min(remaining, stack.getCount());
                stack.decrement(consumed);
                remaining -= consumed;
                
                if (remaining == 0) {
                    return true;
                }
            }
        }
        
        return remaining == 0;
    }
    
    public static boolean hasItems(ServerPlayerEntity player, Item item, int count) {
        int found = 0;
        
        ItemStack mainHand = player.getStackInHand(Hand.MAIN_HAND);
        if (mainHand.getItem() == item) {
            found += mainHand.getCount();
        }
        
        ItemStack offHand = player.getStackInHand(Hand.OFF_HAND);
        if (offHand.getItem() == item) {
            found += offHand.getCount();
        }
        
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.getItem() == item) {
                found += stack.getCount();
            }
        }
        
        return found >= count;
    }
    
    public static void broadcastToClan(Clan clan, Text message, MinecraftServer server) {
        for (UUID memberUuid : clan.getMembers()) {
            ServerPlayerEntity member = server.getPlayerManager().getPlayer(memberUuid);
            if (member != null) {
                member.sendMessage(message, false);
            }
        }
    }
    
    public static void setConfirmation(ServerPlayerEntity player, String action) {
        confirmations.computeIfAbsent(player.getUuid(), k -> new HashMap<>())
            .put(action, System.currentTimeMillis() + CONFIRMATION_TIMEOUT);
    }
    
    public static boolean hasConfirmation(ServerPlayerEntity player, String action) {
        if (player == null || action == null) return false;
        
        Map<String, Long> playerConfirmations = confirmations.get(player.getUuid());
        if (playerConfirmations == null) return false;
        
        Long timestamp = playerConfirmations.get(action);
        if (timestamp == null) return false;
        
        // Auto-cleanup expired confirmations
        if ((System.currentTimeMillis() - timestamp) > CONFIRMATION_TIMEOUT) {
            playerConfirmations.remove(action);
            if (playerConfirmations.isEmpty()) {
                confirmations.remove(player.getUuid());
            }
            return false;
        }
        
        return true;
    }
    
    public static void clearConfirmation(ServerPlayerEntity player, String action) {
        Map<String, Long> playerConfirmations = confirmations.get(player.getUuid());
        if (playerConfirmations != null) {
            playerConfirmations.remove(action);
            if (playerConfirmations.isEmpty()) {
                confirmations.remove(player.getUuid());
            }
        }
    }
    
    public static void cleanupConfirmations() {
        long now = System.currentTimeMillis();
        confirmations.entrySet().removeIf(entry -> {
            entry.getValue().entrySet().removeIf(confirmation -> now > confirmation.getValue());
            return entry.getValue().isEmpty();
        });
    }

    // FIXED: Manual cleanup method that can be called from commands instead of automatic scheduling
    public static void cleanupExpiredConfirmations() {
        long now = System.currentTimeMillis();
        confirmations.entrySet().removeIf(entry -> {
            entry.getValue().entrySet().removeIf(confirmation -> now > confirmation.getValue());
            return entry.getValue().isEmpty();
        });
    }
    
    public static double getDistance(ServerPlayerEntity player1, ServerPlayerEntity player2) {
        if (!player1.getWorld().equals(player2.getWorld())) {
            return Double.MAX_VALUE;
        }
        return player1.getPos().distanceTo(player2.getPos());
    }
    
    public static String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return days + " day" + (days != 1 ? "s" : "");
        } else if (hours > 0) {
            return hours + " hour" + (hours != 1 ? "s" : "");
        } else if (minutes > 0) {
            return minutes + " minute" + (minutes != 1 ? "s" : "");
        } else {
            return seconds + " second" + (seconds != 1 ? "s" : "");
        }
    }

    // FIXED: Efficient clan existence check - O(1) instead of O(n)
    public static boolean clanExistsIgnoreCase(String name, ClanDataManager dataManager) {
        return dataManager.clanExists(name); // Uses efficient HashMap.containsKey()
    }

    public static void notifyGoldConsumption(ServerPlayerEntity player, int amount, String reason) {
        Text message = Text.literal("💰 ")
            .formatted(Formatting.YELLOW)
            .append(Text.literal(amount + " gold consumed")
                .formatted(Formatting.GOLD, Formatting.BOLD))
            .append(Text.literal(" for ")
                .formatted(Formatting.GRAY))
            .append(Text.literal(reason)
                .formatted(Formatting.WHITE))
            .append(Text.literal("!")
                .formatted(Formatting.GRAY));
        
        player.sendMessage(message, false);
    }

    public static boolean hasRecentConfirmation(UUID playerId, String action) {
        Map<String, Long> playerConfirmations = confirmations.get(playerId);
        if (playerConfirmations == null) return false;
        
        Long timestamp = playerConfirmations.get(action);
        if (timestamp == null) return false;
        
        return (System.currentTimeMillis() - timestamp) < CONFIRMATION_TIMEOUT;
    }

    public static void addConfirmation(ServerPlayerEntity player, String action) {
        confirmations.computeIfAbsent(player.getUuid(), k -> new HashMap<>())
            .put(action, System.currentTimeMillis());
    }

    // FIXED: Simple daily operation limits without complex scheduling
    public static boolean canPerformDailyOperation(UUID playerId, String operationType) {
        checkMidnightReset(); // Simple inline check
        
        Map<String, Integer> playerOps = dailyOperations.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
        int currentOps = playerOps.getOrDefault(operationType, 0);
        
        return currentOps < Simpleclans.getConfig().dailyVaultOperations;
    }

    public static void incrementDailyOperation(UUID playerId, String operationType) {
        checkMidnightReset(); // Simple inline check
        
        Map<String, Integer> playerOps = dailyOperations.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
        playerOps.put(operationType, playerOps.getOrDefault(operationType, 0) + 1);
    }

    public static int getRemainingDailyOperations(UUID playerId, String operationType) {
        checkMidnightReset(); // Simple inline check
        
        Map<String, Integer> playerOps = dailyOperations.get(playerId);
        if (playerOps == null) return Simpleclans.getConfig().dailyVaultOperations;
        
        int used = playerOps.getOrDefault(operationType, 0);
        return Math.max(0, Simpleclans.getConfig().dailyVaultOperations - used);
    }

    // FIXED: Simple midnight reset without complex scheduling
    private static void checkMidnightReset() {
        long currentMidnight = System.currentTimeMillis() - (System.currentTimeMillis() % (24 * 60 * 60 * 1000));
        if (currentMidnight > lastMidnightReset) {
            dailyOperations.clear();
            lastMidnightReset = currentMidnight;
            Simpleclans.LOGGER.info("🔄 Daily vault operation counters reset");
        }
    }
}
