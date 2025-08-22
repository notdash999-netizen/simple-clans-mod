package com.clans.commands;

import com.clans.Simpleclans;
import com.clans.data.ClanDataManager;
import com.clans.model.Clan;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import java.util.Map;
import java.util.WeakHashMap;

public class ClanAdminCommand {
    
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("clanadmin")
            .requires(source -> source.hasPermissionLevel(2)) // OP level
            .then(CommandManager.literal("war")
                .then(CommandManager.literal("on")
                    .executes(context -> toggleWars(context, true)))
                .then(CommandManager.literal("off")
                    .executes(context -> toggleWars(context, false))))
            .then(CommandManager.literal("disband")
                .then(CommandManager.argument("clanName", StringArgumentType.string())
                    .executes(ClanAdminCommand::adminDisbandClan)))
            .then(CommandManager.literal("reload")
                .executes(ClanAdminCommand::reloadConfig))
            .then(CommandManager.literal("info")
                .executes(ClanAdminCommand::adminInfo))
            .then(CommandManager.literal("save")
                .executes(ClanAdminCommand::forceSave)));
    }
    
    private static int toggleWars(CommandContext<ServerCommandSource> context, boolean enable) throws CommandSyntaxException {
        ClanDataManager dataManager = Simpleclans.getDataManager();
        dataManager.setWarsEnabled(enable);
        
        String status = enable ? "enabled" : "disabled";
        context.getSource().sendFeedback(() -> Text.literal("Wars " + status + "!")
            .formatted(Formatting.GREEN), true);
        
        return 1;
    }
    
    private static int adminDisbandClan(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String clanName = StringArgumentType.getString(context, "clanName");
        ClanDataManager dataManager = Simpleclans.getDataManager();
        
        Clan clan = dataManager.getClan(clanName);
        if (clan == null) {
            context.getSource().sendFeedback(() -> Text.literal("Clan '" + clanName + "' not found!")
                .formatted(Formatting.RED), false);
            return 0;
        }
        
        if (dataManager.disbandClan(clanName)) {
            context.getSource().sendFeedback(() -> Text.literal("Successfully disbanded clan: " + clanName)
                .formatted(Formatting.GREEN), true);
            return 1;
        } else {
            context.getSource().sendFeedback(() -> Text.literal("Failed to disband clan!")
                .formatted(Formatting.RED), false);
            return 0;
        }
    }
    
    private static int reloadConfig(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        try {
            // Force reload config
            Simpleclans.getConfig().forceReload();
            
            // Provide detailed feedback
            context.getSource().sendFeedback(() -> Text.literal("🔄 Configuration reloaded successfully!")
                .formatted(Formatting.GREEN), true);
            
            context.getSource().sendFeedback(() -> Text.literal("�� Current Values:")
                .formatted(Formatting.YELLOW), false);
            
            context.getSource().sendFeedback(() -> Text.literal("  • Alliance Cost: " + Simpleclans.getConfig().allyCost + " gold")
                .formatted(Formatting.WHITE), false);
            
            context.getSource().sendFeedback(() -> Text.literal("  • Enemy Damage: " + (Simpleclans.getConfig().enemyDamageBonus * 100) + "%")
                .formatted(Formatting.WHITE), false);
            
            context.getSource().sendFeedback(() -> Text.literal("  • Proximity Damage: " + (Simpleclans.getConfig().proximityDamageBonus * 100) + "%")
                .formatted(Formatting.WHITE), false);
            
            return 1;
        } catch (Exception e) {
            context.getSource().sendFeedback(() -> Text.literal("❌ Failed to reload config: " + e.getMessage())
                .formatted(Formatting.RED), false);
            return 0;
        }
    }
    
    private static int adminInfo(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ClanDataManager dataManager = Simpleclans.getDataManager();
        
        int totalClans = dataManager.getAllClans().size();
        int totalPlayers = (int) dataManager.getAllClans().stream()
            .mapToLong(clan -> clan.getMembers().size()).sum();
        
        context.getSource().sendFeedback(() -> Text.literal("=== Clan System Info ===")
            .formatted(Formatting.GOLD, Formatting.BOLD), false);
        
        context.getSource().sendFeedback(() -> Text.literal("Total Clans: " + totalClans)
            .formatted(Formatting.YELLOW), false);
        
        context.getSource().sendFeedback(() -> Text.literal("Total Clan Members: " + totalPlayers)
            .formatted(Formatting.YELLOW), false);
        
        context.getSource().sendFeedback(() -> Text.literal("Wars Enabled: " + dataManager.isWarsEnabled())
            .formatted(Formatting.YELLOW), false);
        
        return 1;
    }
    
    private static int forceSave(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        Simpleclans.getDataManager().save();
        context.getSource().sendFeedback(() -> Text.literal("Clan data saved!")
            .formatted(Formatting.GREEN), true);
        return 1;
    }
}

