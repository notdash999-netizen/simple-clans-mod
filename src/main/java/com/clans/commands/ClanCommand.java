package com.clans.commands;

import com.clans.Simpleclans;
import com.clans.data.ClanDataManager;
import com.clans.data.ClanMemberStatus;
import com.clans.model.*;
import com.clans.util.ClanUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.item.Items;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.UUID;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.text.MutableText;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;
import net.minecraft.util.ActionResult;
import java.util.ArrayList;

public class ClanCommand {
    
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("clan")
            // All commands visible - permissions checked inside methods
            .then(CommandManager.literal("create")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .executes(ClanCommand::createClan)))
            
            .then(CommandManager.literal("invite")
                .then(CommandManager.argument("player", StringArgumentType.string())
                    .executes(ClanCommand::invitePlayer)))
            
            .then(CommandManager.literal("join")
                .then(CommandManager.argument("clanName", StringArgumentType.string())
                    .executes(ClanCommand::joinClan)))
            
            .then(CommandManager.literal("leave")
                .executes(ClanCommand::leaveClan))
            
            .then(CommandManager.literal("disband")
                .executes(ClanCommand::disbandClan))
            
            .then(CommandManager.literal("ally")
                .then(CommandManager.argument("clanName", StringArgumentType.string())
                    .executes(ClanCommand::allyClan)))
            
            .then(CommandManager.literal("enemy")
                .then(CommandManager.argument("clanName", StringArgumentType.string())
                    .executes(ClanCommand::enemyClan)))
            
            .then(CommandManager.literal("neutral")
                .then(CommandManager.argument("clanName", StringArgumentType.string())
                    .executes(ClanCommand::neutralClan)))
            
            .then(CommandManager.literal("vault")
                .then(CommandManager.argument("count", IntegerArgumentType.integer(1))
                    .executes(ClanCommand::vaultStore))
                .then(CommandManager.literal("remove")
                    .then(CommandManager.argument("count", IntegerArgumentType.integer(1))
                        .executes(ClanCommand::vaultRemove)))
                .executes(ClanCommand::vaultList))
            
            .then(CommandManager.literal("info")
                .then(CommandManager.argument("clanName", StringArgumentType.string())
                    .executes(ClanCommand::clanInfoOther))
                .executes(ClanCommand::clanInfoSelf))
            
            .then(CommandManager.literal("boards")
                .executes(ClanCommand::clanBoards))
            
            .then(CommandManager.literal("declare")
                .then(CommandManager.argument("clanName", StringArgumentType.string())
                    .executes(ClanCommand::declareWar)))
            
            .then(CommandManager.literal("list")
                .executes(ClanCommand::listClans))
            
            .then(CommandManager.literal("transfer")
                .then(CommandManager.argument("player", StringArgumentType.string())
                    .executes(ClanCommand::transferKing)))
            
            .then(CommandManager.literal("roles")
                .executes(ClanCommand::showRoles))
            
            .then(CommandManager.literal("kick")
                .then(CommandManager.argument("player", StringArgumentType.string())
                    .executes(ClanCommand::kickPlayer)))
            
            .then(CommandManager.literal("promote")
                .then(CommandManager.argument("player", StringArgumentType.string())
                    .executes(ClanCommand::promotePlayer)))
            
            .then(CommandManager.literal("chat")
                .then(CommandManager.argument("message", StringArgumentType.greedyString())
                    .executes(ClanCommand::sendClanMessage)))
            
            .then(CommandManager.literal("declareEnemy")
                .then(CommandManager.argument("clanName", StringArgumentType.string())
                    .executes(ClanCommand::declareEnemy))));
        
        // /cc alias - visible to everyone
        dispatcher.register(CommandManager.literal("cc")
            .then(CommandManager.argument("message", StringArgumentType.greedyString())
                .executes(ClanCommand::sendClanMessage)));
    }
    
    // BEAUTIFUL /clan create
    private static int createClan(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String clanName = StringArgumentType.getString(context, "name");
        ClanDataManager dataManager = Simpleclans.getDataManager();
        
        // Enhanced validation with beautiful error messages
        String validationError = ClanUtils.validateClanName(clanName);
        if (validationError != null) {
            player.sendMessage(Text.literal(validationError).formatted(Formatting.RED), false);
            return 0;
        }
        
        // Check if player is already in a clan
        if (dataManager.isPlayerInClan(player.getUuid())) {
            player.sendMessage(Text.literal("❌ You are already in a clan! Use ")
                .formatted(Formatting.RED)
                .append(Text.literal("/clan leave")
                    .formatted(Formatting.YELLOW))
                .append(Text.literal(" first.")
                    .formatted(Formatting.RED)), false);
            return 0;
        }
        
        // Check for case-insensitive duplicates
        if (ClanUtils.clanExistsIgnoreCase(clanName, dataManager)) {
            player.sendMessage(Text.literal("❌ A clan with that name already exists!")
                .formatted(Formatting.RED), false);
            return 0;
        }
        
        // Check gold requirement
        if (!ClanUtils.consumeItems(player, Items.GOLD_INGOT, Simpleclans.getConfig().clanCreationCost)) {
            player.sendMessage(Text.literal("❌ Insufficient funds! You need ")
                .formatted(Formatting.RED)
                .append(Text.literal(Simpleclans.getConfig().clanCreationCost + " gold ingots")
                    .formatted(Formatting.GOLD, Formatting.BOLD))
                .append(Text.literal(" to create a clan.")
                    .formatted(Formatting.RED)), false);
            return 0;
        }
        
        // Create clan successfully
        boolean success = dataManager.createClan(clanName, player.getUuid());
        if (success) {
            // Store player name in clan after creation
            Clan newClan = dataManager.getClan(clanName);
            if (newClan != null) {
                newClan.storeMemberName(player.getUuid(), player.getName().getString());
            }
            
            // Beautiful success message
            player.sendMessage(Text.literal("🏰 ")
                .formatted(Formatting.GOLD, Formatting.BOLD)
                .append(Text.literal("Clan created successfully!")
                    .formatted(Formatting.GREEN, Formatting.BOLD))
                .append(Text.literal("\n✨ Welcome to ")
                    .formatted(Formatting.YELLOW))
                .append(Text.literal(clanName)
                    .formatted(Formatting.GOLD, Formatting.BOLD))
                .append(Text.literal(", King!")
                    .formatted(Formatting.YELLOW)), false);
            
            // Notify gold consumption
            ClanUtils.notifyGoldConsumption(player, Simpleclans.getConfig().clanCreationCost, "clan creation");
        } else {
            player.sendMessage(Text.literal("❌ Failed to create clan! Please try again.")
                .formatted(Formatting.RED), false);
        }
        
        return success ? 1 : 0;
    }
    
    // BEAUTIFUL /clan invite
    private static int invitePlayer(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String targetName = StringArgumentType.getString(context, "player");
        ClanDataManager dataManager = Simpleclans.getDataManager();
        
        Clan clan = dataManager.getPlayerClan(player.getUuid());
        if (clan == null) {
            player.sendMessage(Text.literal("❌ You are not in a clan!")
                .formatted(Formatting.RED), false);
            return 0;
        }
        
        // Check permissions (King or Advisor)
        if (!clan.isKing(player.getUuid()) && !clan.isAdvisor(player.getUuid())) {
            player.sendMessage(Text.literal("❌ Only ")
                .formatted(Formatting.RED)
                .append(Text.literal("Kings")
                    .formatted(Formatting.YELLOW, Formatting.BOLD))
                .append(Text.literal(" and ")
                    .formatted(Formatting.RED))
                .append(Text.literal("Advisors")
                    .formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD))
                .append(Text.literal(" can invite players!")
                    .formatted(Formatting.RED)), false);
            return 0;
        }
        
        // Check gold requirement
        if (!ClanUtils.consumeItems(player, Items.GOLD_INGOT, Simpleclans.getConfig().invitationCost)) {
            player.sendMessage(Text.literal("❌ Insufficient funds! You need ")
                .formatted(Formatting.RED)
                .append(Text.literal(Simpleclans.getConfig().invitationCost + " gold ingots")
                    .formatted(Formatting.GOLD, Formatting.BOLD))
                .append(Text.literal(" to invite a player.")
                    .formatted(Formatting.RED)), false);
            return 0;
        }
        
        // Find target player
        ServerPlayerEntity targetPlayer = dataManager.getServer().getPlayerManager().getPlayer(targetName);
        if (targetPlayer == null) {
            player.sendMessage(Text.literal("❌ Player ")
                .formatted(Formatting.RED)
                .append(Text.literal(targetName)
                    .formatted(Formatting.YELLOW, Formatting.BOLD))
                .append(Text.literal(" is not online!")
                    .formatted(Formatting.RED)), false);
            return 0;
        }
        
        // Check if target is already in a clan
        if (dataManager.isPlayerInClan(targetPlayer.getUuid())) {
            player.sendMessage(Text.literal("❌ ")
                .formatted(Formatting.RED)
                .append(Text.literal(targetName)
                    .formatted(Formatting.YELLOW, Formatting.BOLD))
                .append(Text.literal(" is already in a clan!")
                    .formatted(Formatting.RED)), false);
            return 0;
        }
        
        // Check clan size limit
        if (clan.getMembers().size() >= 4) {
            player.sendMessage(Text.literal("❌ Your clan is already at maximum capacity (4 members)!")
                .formatted(Formatting.RED), false);
            return 0;
        }
        
        // Send invitation
        dataManager.addInvitation(targetPlayer.getUuid(), clan.getName());
        boolean success = true; // Since addInvitation doesn't return boolean
        if (success) {
            // Beautiful success messages
            player.sendMessage(Text.literal("📜 ")
                .formatted(Formatting.GREEN)
                .append(Text.literal("Invitation sent to ")
                    .formatted(Formatting.GREEN))
                .append(Text.literal(targetName)
                    .formatted(Formatting.YELLOW, Formatting.BOLD))
                .append(Text.literal("!")
                    .formatted(Formatting.GREEN)), false);
            
            targetPlayer.sendMessage(Text.literal("📜 ")
                .formatted(Formatting.GOLD)
                .append(Text.literal("You have been invited to join ")
                    .formatted(Formatting.GREEN))
                .append(Text.literal(clan.getOriginalName())
                    .formatted(Formatting.GOLD, Formatting.BOLD))
                .append(Text.literal("!")
                    .formatted(Formatting.GREEN))
                .append(Text.literal("\nUse ")
                    .formatted(Formatting.GRAY))
                .append(Text.literal("/clan join " + clan.getName())
                    .formatted(Formatting.YELLOW))
                .append(Text.literal(" to accept (expires in 5 minutes)")
                    .formatted(Formatting.GRAY)), false);
            
            // Notify gold consumption
            ClanUtils.notifyGoldConsumption(player, Simpleclans.getConfig().invitationCost, "clan invitation");
        } else {
            player.sendMessage(Text.literal("❌ Failed to send invitation!")
                .formatted(Formatting.RED), false);
        }
        
        return success ? 1 : 0;
    }
    
    // BEAUTIFUL /clan join
    private static int joinClan(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String clanName = StringArgumentType.getString(context, "clanName");
        ClanDataManager dataManager = Simpleclans.getDataManager();
        
        // Check if player is already in a clan
        if (dataManager.isPlayerInClan(player.getUuid())) {
            player.sendMessage(Text.literal("❌ You are already in a clan! Use ")
                .formatted(Formatting.RED)
                .append(Text.literal("/clan leave")
                    .formatted(Formatting.YELLOW))
                .append(Text.literal(" first.")
                    .formatted(Formatting.RED)), false);
            return 0;
        }
        
        // Check if invitation exists
        if (!dataManager.hasValidInvitation(player.getUuid(), clanName)) {
            player.sendMessage(Text.literal("❌ You have not been invited to ")
                .formatted(Formatting.RED)
                .append(Text.literal(clanName)
                    .formatted(Formatting.YELLOW, Formatting.BOLD))
                .append(Text.literal("!")
                    .formatted(Formatting.RED)), false);
            return 0;
        }
        
        // Check gold requirement
        if (!ClanUtils.consumeItems(player, Items.GOLD_INGOT, Simpleclans.getConfig().joinCost)) {
            player.sendMessage(Text.literal("❌ Insufficient funds! You need ")
                .formatted(Formatting.RED)
                .append(Text.literal(Simpleclans.getConfig().joinCost + " gold ingots")
                    .formatted(Formatting.GOLD, Formatting.BOLD))
                .append(Text.literal(" to join a clan.")
                    .formatted(Formatting.RED)), false);
            return 0;
        }
        
        // Join clan
        boolean success = dataManager.addPlayerToClan(player.getUuid(), clanName);
        if (success) {
            Clan clan = dataManager.getClan(clanName);
            if (clan != null) {
                clan.storeMemberName(player.getUuid(), player.getName().getString());
            }
            
            // Beautiful success message to new member
            player.sendMessage(Text.literal("🎉 ")
                .formatted(Formatting.GREEN, Formatting.BOLD)
                .append(Text.literal("Welcome to ")
                    .formatted(Formatting.GREEN))
                .append(Text.literal(clan.getOriginalName())
                    .formatted(Formatting.GOLD, Formatting.BOLD))
                .append(Text.literal("!")
                    .formatted(Formatting.GREEN)), false);
            
            // Notify existing clan members
            Text joinNotification = Text.literal("👥 ")
                .formatted(Formatting.YELLOW)
                .append(Text.literal(player.getName().getString())
                    .formatted(Formatting.GREEN, Formatting.BOLD))
                .append(Text.literal(" has joined your clan!")
                    .formatted(Formatting.YELLOW));
            
            for (UUID memberUuid : clan.getMembers()) {
                if (!memberUuid.equals(player.getUuid())) {
                    ServerPlayerEntity member = dataManager.getServer().getPlayerManager().getPlayer(memberUuid);
                    if (member != null) {
                        member.sendMessage(joinNotification, false);
                    }
                }
            }
            
            // Notify gold consumption
            ClanUtils.notifyGoldConsumption(player, Simpleclans.getConfig().joinCost, "joining clan");
        } else {
            player.sendMessage(Text.literal("❌ Failed to join clan!")
                .formatted(Formatting.RED), false);
        }
        
        return success ? 1 : 0;
    }
    
    private static int listClans(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        ClanDataManager dataManager = Simpleclans.getDataManager();
        
        List<Clan> allClans = new ArrayList<>(dataManager.getAllClans());
        if (allClans.isEmpty()) {
            player.sendMessage(Text.literal("📋 ")
                .formatted(Formatting.YELLOW)
                .append(Text.literal("No clans exist yet!")
                    .formatted(Formatting.GRAY))
                .append(Text.literal("\nUse ")
                    .formatted(Formatting.GRAY))
                .append(Text.literal("/clan create <name>")
                    .formatted(Formatting.YELLOW))
                .append(Text.literal(" to create the first one!")
                    .formatted(Formatting.GRAY)), false);
            return 0;
        }
        
        // Beautiful clan list
        MutableText listMessage = Text.literal("═══════════════════════════")
            .formatted(Formatting.GOLD, Formatting.BOLD)
            .append(Text.literal("\n🏰 ")
                .formatted(Formatting.YELLOW))
            .append(Text.literal("SERVER CLANS (" + allClans.size() + ")")
                .formatted(Formatting.GOLD, Formatting.BOLD))
            .append(Text.literal("\n═══════════════════════════")
                .formatted(Formatting.GOLD, Formatting.BOLD));
        
        for (Clan clan : allClans) {
            listMessage = listMessage.append(Text.literal("\n⚔️ ")
                    .formatted(Formatting.YELLOW))
                .append(Text.literal(clan.getOriginalName())
                    .formatted(Formatting.GOLD, Formatting.BOLD))
                .append(Text.literal(" - ")
                    .formatted(Formatting.GRAY))
                .append(Text.literal(clan.getMembers().size() + "/4 members")
                    .formatted(Formatting.WHITE))
                .append(Text.literal(" | Power: ")
                    .formatted(Formatting.GRAY))
                .append(Text.literal(String.valueOf(clan.calculatePower()))
                    .formatted(Formatting.GREEN, Formatting.BOLD));
        }
        
        listMessage = listMessage.append(Text.literal("\n═══════════════════════════")
            .formatted(Formatting.GOLD, Formatting.BOLD));
        
        player.sendMessage(listMessage, false);
        return 1;
    }
    
    private static int transferKing(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String targetName = StringArgumentType.getString(context, "player");
        ClanDataManager dataManager = Simpleclans.getDataManager();
        
        Clan clan = dataManager.getPlayerClan(player.getUuid());
        if (clan == null) {
            player.sendMessage(Text.literal("You are not in a clan!")
                .formatted(Formatting.RED), false);
            return 0;
        }
        
        if (!clan.isKing(player.getUuid())) {
            player.sendMessage(Text.literal("Only the King can transfer leadership!")
                .formatted(Formatting.RED), false);
            return 0;
        }
        
        ServerPlayerEntity target = context.getSource().getServer().getPlayerManager().getPlayer(targetName);
        if (target == null) {
            player.sendMessage(Text.literal("Player not found!")
                .formatted(Formatting.RED), false);
            return 0;
        }
        
        if (!clan.isMember(target.getUuid())) {
            player.sendMessage(Text.literal("That player is not in your clan!")
                .formatted(Formatting.RED), false);
            return 0;
        }
        
        // Transfer king role
        if (clan.transferKing(target.getUuid())) {
            player.sendMessage(Text.literal("Successfully transferred leadership to " + targetName)
                .formatted(Formatting.GREEN), false);
            target.sendMessage(Text.literal("You are now the King of " + clan.getOriginalName() + "!")
                .formatted(Formatting.GOLD), false);
            
            // Notify clan
            ClanUtils.broadcastToClan(clan, Text.literal(targetName + " is now the King of " + clan.getOriginalName() + "!")
                .formatted(Formatting.GOLD), dataManager.getServer());
            
            dataManager.save();
            return 1;
        } else {
            player.sendMessage(Text.literal("Failed to transfer leadership!")
                .formatted(Formatting.RED), false);
            return 0;
        }
    }
    
    private static int showRoles(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        ClanDataManager dataManager = Simpleclans.getDataManager();
        
        Clan clan = dataManager.getPlayerClan(player.getUuid());
        if (clan == null) {
            player.sendMessage(Text.literal("You are not in a clan!")
                .formatted(Formatting.RED), false);
            return 0;
        }
        
        player.sendMessage(Text.literal("======================")
            .formatted(Formatting.GOLD), false);
        player.sendMessage(Text.literal(clan.getOriginalName())
            .formatted(Formatting.GOLD, Formatting.BOLD), false);
        player.sendMessage(Text.literal("======================")
            .formatted(Formatting.GOLD), false);
        
        // Show King
        ServerPlayerEntity king = dataManager.getServer().getPlayerManager().getPlayer(clan.getKing());
        String kingName = king != null ? king.getName().getString() : "Unknown";
        player.sendMessage(Text.literal("King").formatted(Formatting.YELLOW, Formatting.BOLD)
            .append(Text.literal(" - ").formatted(Formatting.WHITE))
            .append(Text.literal(kingName).formatted(Formatting.WHITE)), false);
        
        // Show Advisors
        StringBuilder advisorNames = new StringBuilder();
        for (UUID advisorId : clan.getAdvisors()) {
            ServerPlayerEntity advisor = dataManager.getServer().getPlayerManager().getPlayer(advisorId);
            if (advisor != null) {
                if (advisorNames.length() > 0) advisorNames.append(", ");
                advisorNames.append(advisor.getName().getString());
            }
        }
        
        if (advisorNames.length() > 0) {
            player.sendMessage(Text.literal("Advisor").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD)
                .append(Text.literal(" - ").formatted(Formatting.WHITE))
                .append(Text.literal(advisorNames.toString()).formatted(Formatting.WHITE)), false);
        }
        
        // Show Peasants
        StringBuilder peasantNames = new StringBuilder();
        for (UUID memberId : clan.getMembers()) {
            if (!clan.isKing(memberId) && !clan.isAdvisor(memberId)) {
                ServerPlayerEntity peasant = dataManager.getServer().getPlayerManager().getPlayer(memberId);
                if (peasant != null) {
                    if (peasantNames.length() > 0) peasantNames.append(", ");
                    peasantNames.append(peasant.getName().getString());
                }
            }
        }
        
        if (peasantNames.length() > 0) {
            player.sendMessage(Text.literal("Peasant").formatted(Formatting.GRAY, Formatting.BOLD)
                .append(Text.literal(" - ").formatted(Formatting.WHITE))
                .append(Text.literal(peasantNames.toString()).formatted(Formatting.WHITE)), false);
        }
        
        return 1;
    }
    
    private static int kickPlayer(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String targetName = StringArgumentType.getString(context, "player");
        ClanDataManager dataManager = Simpleclans.getDataManager();
        
        Clan clan = dataManager.getPlayerClan(player.getUuid());
        if (clan == null) {
            player.sendMessage(Text.literal("❌ You are not in a clan!").formatted(Formatting.RED), false);
            return 0;
        }
        
        // Check if player has kick permissions (King or Advisor)
        if (!clan.isKing(player.getUuid()) && !clan.isAdvisor(player.getUuid())) {
            player.sendMessage(Text.literal("❌ Only Kings and Advisors can kick members!").formatted(Formatting.RED), false);
            return 0;
        }
        
        // FIXED: Find target player UUID
        ServerPlayerEntity targetPlayer = dataManager.getServer().getPlayerManager().getPlayer(targetName);
        UUID targetId = null;

        if (targetPlayer != null) {
            // Player is online - get their UUID
            targetId = targetPlayer.getUuid();
        } else {
            // Player is offline - search through clan member names
            for (Clan searchClan : dataManager.getAllClans()) {
                for (UUID memberId : searchClan.getMembers()) {
                    String memberName = searchClan.getMemberName(memberId);
                    if (memberName.equalsIgnoreCase(targetName)) {
                        targetId = memberId;
                        break;
                    }
                }
                if (targetId != null) break;
            }
        }

        if (targetId == null) {
            player.sendMessage(Text.literal("❌ Player '" + targetName + "' not found!").formatted(Formatting.RED), false);
            return 0;
        }
        
        if (!clan.isMember(targetId)) {
            player.sendMessage(Text.literal("❌ That player is not in your clan!").formatted(Formatting.RED), false);
            return 0;
        }
        
        // IMPORTANT: Advisors cannot kick Kings
        if (clan.isAdvisor(player.getUuid()) && clan.isKing(targetId)) {
            player.sendMessage(Text.literal("❌ Advisors cannot kick the King!").formatted(Formatting.RED), false);
            return 0;
        }
        
        if (targetId.equals(player.getUuid())) {
            player.sendMessage(Text.literal("❌ You cannot kick yourself! Use /clan leave instead.").formatted(Formatting.RED), false);
            return 0;
        }
        
        // Remove the player from clan
        clan.removeMember(targetId);
        dataManager.removePlayerFromClan(targetId);
        
        // Success message
        player.sendMessage(Text.literal("✅ ")
            .formatted(Formatting.GREEN)
            .append(Text.literal(targetName)
                .formatted(Formatting.YELLOW, Formatting.BOLD))
            .append(Text.literal(" has been kicked from the clan!")
                .formatted(Formatting.GREEN)), false);
        
        // Notify kicked player if online
        if (targetPlayer != null) {
            targetPlayer.sendMessage(Text.literal("❌ You have been kicked from ")
                .formatted(Formatting.RED)
                .append(Text.literal(clan.getOriginalName())
                    .formatted(Formatting.YELLOW, Formatting.BOLD))
                .append(Text.literal("!")
                    .formatted(Formatting.RED)), false);
        }
        
        // Notify other clan members
        Text kickNotification = Text.literal("👢 ")
            .formatted(Formatting.YELLOW)
            .append(Text.literal(targetName)
                .formatted(Formatting.RED, Formatting.BOLD))
            .append(Text.literal(" has been kicked from the clan by ")
                .formatted(Formatting.GRAY))
            .append(Text.literal(player.getName().getString())
                .formatted(Formatting.YELLOW, Formatting.BOLD));
        
        for (UUID memberUuid : clan.getMembers()) {
            if (!memberUuid.equals(player.getUuid()) && !memberUuid.equals(targetId)) {
                ServerPlayerEntity member = dataManager.getServer().getPlayerManager().getPlayer(memberUuid);
                if (member != null) {
                    member.sendMessage(kickNotification, false);
                }
            }
        }
        
        dataManager.save();
        return 1;
    }
    
    // BEAUTIFUL /clan promote
    private static int promotePlayer(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String targetName = StringArgumentType.getString(context, "player");
        ClanDataManager dataManager = Simpleclans.getDataManager();
        
        Clan clan = dataManager.getPlayerClan(player.getUuid());
        if (clan == null) {
            player.sendMessage(Text.literal("❌ You are not in a clan!").formatted(Formatting.RED), false);
            return 0;
        }
        
        if (!clan.isKing(player.getUuid())) {
            player.sendMessage(Text.literal("❌ Only the King can promote members!").formatted(Formatting.RED), false);
            return 0;
        }
        
        // Find target player UUID
        ServerPlayerEntity targetPlayer = dataManager.getServer().getPlayerManager().getPlayer(targetName);
        UUID targetId = null;

        if (targetPlayer != null) {
            targetId = targetPlayer.getUuid();
        } else {
            for (Clan searchClan : dataManager.getAllClans()) {
                for (UUID memberId : searchClan.getMembers()) {
                    String memberName = searchClan.getMemberName(memberId);
                    if (memberName.equalsIgnoreCase(targetName)) {
                        targetId = memberId;
                        break;
                    }
                }
                if (targetId != null) break;
            }
        }

        if (targetId == null) {
            player.sendMessage(Text.literal("❌ Player '" + targetName + "' not found!").formatted(Formatting.RED), false);
            return 0;
        }
        
        if (!clan.isMember(targetId)) {
            player.sendMessage(Text.literal("❌ That player is not in your clan!").formatted(Formatting.RED), false);
            return 0;
        }
        
        if (clan.isKing(targetId)) {
            player.sendMessage(Text.literal("❌ You cannot promote the King!").formatted(Formatting.RED), false);
            return 0;
        }
        
        if (clan.isAdvisor(targetId)) {
            player.sendMessage(Text.literal("❌ That player is already an Advisor! Use ")
                .formatted(Formatting.RED)
                .append(Text.literal("/clan transfer")
                    .formatted(Formatting.YELLOW))
                .append(Text.literal(" to make them King.")
                    .formatted(Formatting.RED)), false);
            return 0;
        }
        
        // Check advisor limit
        if (clan.getAdvisors().size() >= 2) {
            player.sendMessage(Text.literal("❌ Maximum of 2 Advisors allowed per clan!")
                .formatted(Formatting.RED), false);
            return 0;
        }
        
        // Promote to advisor
        clan.getAdvisors().add(targetId);
        
        // Success messages
        player.sendMessage(Text.literal("💼 ")
            .formatted(Formatting.LIGHT_PURPLE)
            .append(Text.literal(targetName)
                .formatted(Formatting.YELLOW, Formatting.BOLD))
            .append(Text.literal(" promoted to Advisor!")
                .formatted(Formatting.LIGHT_PURPLE)), false);
        
        // Notify promoted player
        if (targetPlayer != null) {
            targetPlayer.sendMessage(Text.literal("💼 You have been promoted to Advisor in ")
                .formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD)
                .append(Text.literal(clan.getOriginalName())
                    .formatted(Formatting.GOLD, Formatting.BOLD))
                .append(Text.literal("!")
                    .formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD)), false);
        }
        
        // Notify clan members
        Text promoteNotification = Text.literal("💼 ")
            .formatted(Formatting.LIGHT_PURPLE)
            .append(Text.literal(targetName)
                .formatted(Formatting.YELLOW, Formatting.BOLD))
            .append(Text.literal(" has been promoted to Advisor!")
                .formatted(Formatting.LIGHT_PURPLE));
        
        for (UUID memberUuid : clan.getMembers()) {
            if (!memberUuid.equals(player.getUuid()) && !memberUuid.equals(targetId)) {
                ServerPlayerEntity member = dataManager.getServer().getPlayerManager().getPlayer(memberUuid);
                if (member != null) {
                    member.sendMessage(promoteNotification, false);
                }
            }
        }
        
        dataManager.save();
        return 1;
    }
    
    private static int sendClanMessage(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String message = StringArgumentType.getString(context, "message");
        
        Simpleclans.getChatSystem().sendClanMessage(player, message);
        return 1;
    }
    
    // Enhanced vault commands
    private static int vaultStore(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        int count = IntegerArgumentType.getInteger(context, "count");
        ClanDataManager dataManager = Simpleclans.getDataManager();
        
        Clan clan = dataManager.getPlayerClan(player.getUuid());
        if (clan == null) {
            player.sendMessage(Text.literal("❌ You are not in a clan!").formatted(Formatting.RED), false);
            return 0;
        }
        
        ClanRole role = clan.getPlayerRole(player.getUuid());
        if (role == null || !role.canAccessVault()) {
            player.sendMessage(Text.literal("❌ Only ")
                .formatted(Formatting.RED)
                .append(Text.literal("Kings")
                    .formatted(Formatting.GOLD, Formatting.BOLD))
                .append(Text.literal(" and ")
                    .formatted(Formatting.RED))
                .append(Text.literal("Advisors")
                    .formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD))
                .append(Text.literal(" can access the vault!")
                    .formatted(Formatting.RED)), false);
            return 0;
        }
        
        // FIXED: Check daily operation limits from Section 1
        if (!ClanUtils.canPerformDailyOperation(player.getUuid(), "deposit")) {
            int remaining = ClanUtils.getRemainingDailyOperations(player.getUuid(), "deposit");
            player.sendMessage(Text.literal("❌ Daily deposit limit reached! You have ")
                .formatted(Formatting.RED)
                .append(Text.literal(remaining + " deposits")
                    .formatted(Formatting.YELLOW, Formatting.BOLD))
                .append(Text.literal(" remaining today.")
                    .formatted(Formatting.RED)), false);
            return 0;
        }
        
        // FIXED: Check vault size limits from Section 1
        int currentVault = clan.getNetheriteVault();
        if (currentVault + count > Simpleclans.getConfig().maxVaultSize) {
            int maxDeposit = Simpleclans.getConfig().maxVaultSize - currentVault;
            player.sendMessage(Text.literal("❌ Vault size limit! You can only deposit ")
                .formatted(Formatting.RED)
                .append(Text.literal(maxDeposit + " more netherite")
                    .formatted(Formatting.AQUA, Formatting.BOLD))
                .append(Text.literal(" (max: " + Simpleclans.getConfig().maxVaultSize + ").")
                    .formatted(Formatting.RED)), false);
            return 0;
        }
        
        // Check if player has enough netherite
        if (!ClanUtils.hasItems(player, Items.NETHERITE_INGOT, count)) {
            player.sendMessage(Text.literal("❌ You don't have enough netherite ingots!")
                .formatted(Formatting.RED), false);
            return 0;
        }
        
        // Consume netherite and add to vault
        if (ClanUtils.consumeItems(player, Items.NETHERITE_INGOT, count)) {
            clan.setNetheriteVault(currentVault + count);
            ClanUtils.incrementDailyOperation(player.getUuid(), "deposit");
            
            // Beautiful success message
            player.sendMessage(Text.literal("💎 ")
                .formatted(Formatting.AQUA, Formatting.BOLD)
                .append(Text.literal("Deposited ")
                    .formatted(Formatting.GREEN))
                .append(Text.literal(count + " netherite")
                    .formatted(Formatting.AQUA, Formatting.BOLD))
                .append(Text.literal(" to clan vault! Total: ")
                    .formatted(Formatting.GREEN))
                .append(Text.literal((currentVault + count) + "/" + Simpleclans.getConfig().maxVaultSize)
                    .formatted(Formatting.WHITE, Formatting.BOLD)), false);
            
            // Show remaining daily operations
            int remainingDeposits = ClanUtils.getRemainingDailyOperations(player.getUuid(), "deposit");
            if (remainingDeposits > 0) {
                player.sendMessage(Text.literal("📊 You have ")
                    .formatted(Formatting.GRAY)
                    .append(Text.literal(remainingDeposits + " deposits")
                        .formatted(Formatting.YELLOW))
                    .append(Text.literal(" remaining today.")
                        .formatted(Formatting.GRAY)), false);
            }
            
            dataManager.save();
            return 1;
        }
        
        return 0;
    }
    
    private static int vaultRemove(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        int count = IntegerArgumentType.getInteger(context, "count");
        ClanDataManager dataManager = Simpleclans.getDataManager();
        
        Clan clan = dataManager.getPlayerClan(player.getUuid());
        if (clan == null) {
            player.sendMessage(Text.literal("❌ You are not in a clan!").formatted(Formatting.RED), false);
            return 0;
        }
        
        ClanRole role = clan.getPlayerRole(player.getUuid());
        if (role == null || !role.canRemoveFromVault()) {
            player.sendMessage(Text.literal("❌ Only the ")
                .formatted(Formatting.RED)
                .append(Text.literal("King")
                    .formatted(Formatting.GOLD, Formatting.BOLD))
                .append(Text.literal(" can remove items from the vault!")
                    .formatted(Formatting.RED)), false);
            return 0;
        }
        
        // FIXED: Check daily operation limits from Section 1
        if (!ClanUtils.canPerformDailyOperation(player.getUuid(), "withdraw")) {
            int remaining = ClanUtils.getRemainingDailyOperations(player.getUuid(), "withdraw");
            player.sendMessage(Text.literal("❌ Daily withdrawal limit reached! You have ")
                .formatted(Formatting.RED)
                .append(Text.literal(remaining + " withdrawals")
                    .formatted(Formatting.YELLOW, Formatting.BOLD))
                .append(Text.literal(" remaining today.")
                    .formatted(Formatting.RED)), false);
            return 0;
        }
        
        // Check if vault has enough netherite
        int currentVault = clan.getNetheriteVault();
        if (currentVault < count) {
            player.sendMessage(Text.literal("❌ Insufficient netherite in vault! Available: ")
                .formatted(Formatting.RED)
                .append(Text.literal(currentVault + " netherite")
                    .formatted(Formatting.AQUA, Formatting.BOLD)), false);
            return 0;
        }
        
        // Give netherite to player and remove from vault
        ItemStack netheriteStack = new ItemStack(Items.NETHERITE_INGOT, count);
        if (player.getInventory().insertStack(netheriteStack)) {
            clan.setNetheriteVault(currentVault - count);
            ClanUtils.incrementDailyOperation(player.getUuid(), "withdraw");
            
            // Beautiful success message
            player.sendMessage(Text.literal("�� ")
                .formatted(Formatting.AQUA, Formatting.BOLD)
                .append(Text.literal("Withdrew ")
                    .formatted(Formatting.GREEN))
                .append(Text.literal(count + " netherite")
                    .formatted(Formatting.AQUA, Formatting.BOLD))
                .append(Text.literal(" from clan vault! Remaining: ")
                    .formatted(Formatting.GREEN))
                .append(Text.literal((currentVault - count) + "/" + Simpleclans.getConfig().maxVaultSize)
                    .formatted(Formatting.WHITE, Formatting.BOLD)), false);
            
            // Show remaining daily operations  
            int remainingWithdrawals = ClanUtils.getRemainingDailyOperations(player.getUuid(), "withdraw");
            if (remainingWithdrawals > 0) {
                player.sendMessage(Text.literal("📊 You have ")
                    .formatted(Formatting.GRAY)
                    .append(Text.literal(remainingWithdrawals + " withdrawals")
                        .formatted(Formatting.YELLOW))
                    .append(Text.literal(" remaining today.")
                        .formatted(Formatting.GRAY)), false);
            }
            
            dataManager.save();
            return 1;
        } else {
            player.sendMessage(Text.literal("❌ Your inventory is full!")
                .formatted(Formatting.RED), false);
            return 0;
        }
    }
    
    private static int vaultList(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        ClanDataManager dataManager = Simpleclans.getDataManager();
        
        Clan clan = dataManager.getPlayerClan(player.getUuid());
        if (clan == null) {
            player.sendMessage(Text.literal("❌ You are not in a clan!").formatted(Formatting.RED), false);
            return 0;
        }
        
        ClanRole role = clan.getPlayerRole(player.getUuid());
        if (role == null || !role.canAccessVault()) {
            player.sendMessage(Text.literal("❌ Only ")
                .formatted(Formatting.RED)
                .append(Text.literal("Kings")
                    .formatted(Formatting.GOLD, Formatting.BOLD))
                .append(Text.literal(" and ")
                    .formatted(Formatting.RED))
                .append(Text.literal("Advisors")
                    .formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD))
                .append(Text.literal(" can view the vault!")
                    .formatted(Formatting.RED)), false);
            return 0;
        }
        
        int currentVault = clan.getNetheriteVault();
        int maxVault = Simpleclans.getConfig().maxVaultSize;
        
        // Calculate protection time
        long protectionHours = currentVault * Simpleclans.getConfig().netheriteVaultHours;
        
        // Beautiful vault display
        MutableText vaultMessage = Text.literal("═══════════════════════════")
            .formatted(Formatting.AQUA, Formatting.BOLD)
            .append(Text.literal("\n💎 ")
                .formatted(Formatting.AQUA))
            .append(Text.literal("CLAN VAULT")
                .formatted(Formatting.AQUA, Formatting.BOLD))
            .append(Text.literal("\n═══════════════════════════")
                .formatted(Formatting.AQUA, Formatting.BOLD))
            .append(Text.literal("\n📦 Contents: ")
                .formatted(Formatting.YELLOW))
            .append(Text.literal(currentVault + "/" + maxVault + " netherite")
                .formatted(Formatting.WHITE, Formatting.BOLD))
            .append(Text.literal("\n🛡️ Protection: ")
                .formatted(Formatting.GREEN))
            .append(Text.literal(protectionHours + " hours")
                .formatted(Formatting.WHITE, Formatting.BOLD));
        
        // FIXED: Show daily operation limits from Section 1
        int remainingDeposits = ClanUtils.getRemainingDailyOperations(player.getUuid(), "deposit");
        int remainingWithdrawals = ClanUtils.getRemainingDailyOperations(player.getUuid(), "withdraw");
        int maxDaily = Simpleclans.getConfig().dailyVaultOperations;
        
        vaultMessage = vaultMessage.append(Text.literal("\n📊 Daily Limits:")
                .formatted(Formatting.GRAY))
            .append(Text.literal("\n  • Deposits: ")
                .formatted(Formatting.GRAY))
            .append(Text.literal(remainingDeposits + "/" + maxDaily)
                .formatted(Formatting.YELLOW))
            .append(Text.literal("\n  • Withdrawals: ")
                .formatted(Formatting.GRAY))
            .append(Text.literal(remainingWithdrawals + "/" + maxDaily)
                .formatted(Formatting.YELLOW))
            .append(Text.literal("\n═══════════════════════════")
                .formatted(Formatting.AQUA, Formatting.BOLD));
        
        player.sendMessage(vaultMessage, false);
        return 1;
    }
    
    // Enhanced info commands
    private static int clanInfoSelf(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        ClanDataManager dataManager = Simpleclans.getDataManager();
        Clan clan = dataManager.getPlayerClan(player.getUuid());
        
        if (clan == null) {
            player.sendMessage(Text.literal("❌ You are not in a clan!").formatted(Formatting.RED), false);
            return 0;
        }
        
        // Beautiful enhanced clan info display
        MutableText infoMessage = Text.literal("════════════════════════════")
            .formatted(Formatting.GOLD, Formatting.BOLD)
            .append(Text.literal("\n🏰 ")
                .formatted(Formatting.YELLOW))
            .append(Text.literal(clan.getOriginalName())
                .formatted(Formatting.GOLD, Formatting.BOLD))
            .append(Text.literal("\n════════════════════════════")
                .formatted(Formatting.GOLD, Formatting.BOLD))
            
            // Member list with proper offline handling
            .append(Text.literal("\n👑 ")
                .formatted(Formatting.YELLOW, Formatting.BOLD))
            .append(Text.literal("King")
                .formatted(Formatting.YELLOW, Formatting.BOLD))
            .append(Text.literal(" - ")
                .formatted(Formatting.WHITE))
            .append(getPlayerDisplayName(clan.getKing(), dataManager));
        
        // Advisors section
        infoMessage = infoMessage.append(Text.literal("\n💼 ")
                .formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD))
            .append(Text.literal("Advisors")
                .formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD))
            .append(Text.literal(" - ")
                .formatted(Formatting.WHITE));
        
        if (clan.getAdvisors().isEmpty()) {
            infoMessage = infoMessage.append(Text.literal("None")
                .formatted(Formatting.GRAY));
        } else {
            boolean first = true;
            for (UUID advisorId : clan.getAdvisors()) {
                if (!first) infoMessage = infoMessage.append(Text.literal(", ").formatted(Formatting.GRAY));
                infoMessage = infoMessage.append(getPlayerDisplayName(advisorId, dataManager));
                first = false;
            }
        }
        
        // Peasants section  
        infoMessage = infoMessage.append(Text.literal("\n👤 ")
                .formatted(Formatting.GRAY, Formatting.BOLD))
            .append(Text.literal("Peasants")
                .formatted(Formatting.GRAY, Formatting.BOLD))
                .append(Text.literal(" - ")
                    .formatted(Formatting.WHITE));
        
        Set<UUID> peasants = new HashSet<>(clan.getMembers());
        peasants.remove(clan.getKing());
        peasants.removeAll(clan.getAdvisors());
        
        if (peasants.isEmpty()) {
            infoMessage = infoMessage.append(Text.literal("None")
                .formatted(Formatting.GRAY));
        } else {
            boolean first = true;
            for (UUID peasantId : peasants) {
                if (!first) infoMessage = infoMessage.append(Text.literal(", ").formatted(Formatting.GRAY));
                infoMessage = infoMessage.append(getPlayerDisplayName(peasantId, dataManager));
                first = false;
            }
        }
        
        // FIXED: Power section with single emoji
        infoMessage = infoMessage.append(Text.literal("\n⚔️ Power: ")
                .formatted(Formatting.RED, Formatting.BOLD))
            .append(Text.literal(String.valueOf(clan.calculatePower()))
                .formatted(Formatting.WHITE, Formatting.BOLD))
            .append(Text.literal(" (")
                .formatted(Formatting.GRAY))
            .append(Text.literal(clan.getKills() + " kills, " + clan.getDeaths() + " deaths")
                .formatted(Formatting.WHITE))
            .append(Text.literal(")")
                .formatted(Formatting.GRAY))
            
            .append(Text.literal("\n💎 Vault: ")
                .formatted(Formatting.AQUA, Formatting.BOLD))
            .append(Text.literal(clan.getNetheriteVault() + " netherite")
                .formatted(Formatting.WHITE))
            .append(Text.literal(" (" + (clan.getNetheriteVault() * 12) + "h protection)")
                .formatted(Formatting.GRAY));
        
        // NEW: Combined Relationships section
        infoMessage = infoMessage.append(Text.literal("\n════════════════════════════")
            .formatted(Formatting.GOLD, Formatting.BOLD))
            .append(Text.literal("\n═══════")
                .formatted(Formatting.GOLD, Formatting.BOLD))
            .append(Text.literal(" | ")
                .formatted(Formatting.WHITE, Formatting.BOLD))
            .append(Text.literal("Relationships")
                .formatted(Formatting.YELLOW, Formatting.BOLD))
            .append(Text.literal(" | ")
                .formatted(Formatting.WHITE, Formatting.BOLD))
            .append(Text.literal("═══════")
                .formatted(Formatting.GOLD, Formatting.BOLD))
            .append(Text.literal("\n════════════════════════════")
                .formatted(Formatting.GOLD, Formatting.BOLD));
        
        // Allies
        infoMessage = infoMessage.append(Text.literal("\n🤝 Allies: ")
            .formatted(Formatting.GREEN, Formatting.BOLD));
        
        if (clan.getAllies().isEmpty()) {
            infoMessage = infoMessage.append(Text.literal("None").formatted(Formatting.GRAY));
        } else {
            boolean first = true;
            for (String ally : clan.getAllies()) {
                if (!first) infoMessage = infoMessage.append(Text.literal(", ").formatted(Formatting.GRAY));
                infoMessage = infoMessage.append(Text.literal(ally).formatted(Formatting.GREEN));
                first = false;
            }
        }
        
        // Enemies
        infoMessage = infoMessage.append(Text.literal("\n⚔️ Enemies: ")
            .formatted(Formatting.RED, Formatting.BOLD));
        
        if (clan.getEnemies().isEmpty()) {
            infoMessage = infoMessage.append(Text.literal("None").formatted(Formatting.GRAY));
        } else {
            boolean first = true;
            for (String enemy : clan.getEnemies()) {
                if (!first) infoMessage = infoMessage.append(Text.literal(", ").formatted(Formatting.GRAY));
                infoMessage = infoMessage.append(Text.literal(enemy).formatted(Formatting.RED));
                first = false;
            }
        }
        
        // Neutral
        infoMessage = infoMessage.append(Text.literal("\n🤷 Neutral: ")
            .formatted(Formatting.YELLOW, Formatting.BOLD));
        
        if (clan.getNeutrals().isEmpty()) {
            infoMessage = infoMessage.append(Text.literal("None").formatted(Formatting.GRAY));
        } else {
            boolean first = true;
            for (String neutral : clan.getNeutrals()) {
                if (!first) infoMessage = infoMessage.append(Text.literal(", ").formatted(Formatting.GRAY));
                infoMessage = infoMessage.append(Text.literal(neutral).formatted(Formatting.YELLOW));
                first = false;
            }
        }
        
        infoMessage = infoMessage.append(Text.literal("\n════════════════════════════")
            .formatted(Formatting.GOLD, Formatting.BOLD));
        
        player.sendMessage(infoMessage, false);
        return 1;
    }
    
    private static int clanInfoOther(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String clanName = StringArgumentType.getString(context, "clanName");
        ClanDataManager dataManager = Simpleclans.getDataManager();
        
        Clan clan = dataManager.getClan(clanName);
        if (clan == null) {
            player.sendMessage(Text.literal("❌ Clan '" + clanName + "' not found!")
                .formatted(Formatting.RED), false);
            return 0;
        }
        
        // Public clan information (limited)
        MutableText infoMessage = Text.literal("═══════════════════════════")
            .formatted(Formatting.GOLD, Formatting.BOLD)
            .append(Text.literal("\n🏰 ")
                .formatted(Formatting.YELLOW))
            .append(Text.literal(clan.getOriginalName())
                .formatted(Formatting.GOLD, Formatting.BOLD))
            .append(Text.literal(" (Public Info)")
                .formatted(Formatting.GRAY))
            .append(Text.literal("\n═══════════════════════════")
                .formatted(Formatting.GOLD, Formatting.BOLD))
            .append(Text.literal("\n👥 Members: ")
                .formatted(Formatting.YELLOW))
            .append(Text.literal(clan.getMembers().size() + "/4")
                .formatted(Formatting.WHITE, Formatting.BOLD))
            .append(Text.literal("\n⚔️ Power: ")
                .formatted(Formatting.RED))
            .append(Text.literal(String.valueOf(clan.calculatePower()))
                .formatted(Formatting.WHITE, Formatting.BOLD))
            .append(Text.literal(" (")
                .formatted(Formatting.GRAY))
            .append(Text.literal(clan.getKills() + " kills, " + clan.getDeaths() + " deaths")
                .formatted(Formatting.WHITE))
            .append(Text.literal(")")
                .formatted(Formatting.GRAY))
            .append(Text.literal("\n═══════════════════════════")
                .formatted(Formatting.GOLD, Formatting.BOLD));
        
        player.sendMessage(infoMessage, false);
        return 1;
    }
    
    // BEAUTIFUL /clan leave
    private static int leaveClan(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        ClanDataManager dataManager = Simpleclans.getDataManager();
        
        Clan clan = dataManager.getPlayerClan(player.getUuid());
        if (clan == null) {
            player.sendMessage(Text.literal("❌ You are not in a clan!")
                .formatted(Formatting.RED), false);
            return 0;
        }
        
        if (clan.isKing(player.getUuid())) {
            player.sendMessage(Text.literal("❌ Kings cannot leave their clan! Use ")
                .formatted(Formatting.RED)
                .append(Text.literal("/clan disband")
                    .formatted(Formatting.YELLOW))
                .append(Text.literal(" or ")
                    .formatted(Formatting.RED))
                .append(Text.literal("/clan transfer <player>")
                    .formatted(Formatting.YELLOW))
                .append(Text.literal(" first.")
                    .formatted(Formatting.RED)), false);
            return 0;
        }
        
        // Leave clan
        String clanName = clan.getOriginalName();
        dataManager.removePlayerFromClan(player.getUuid());
        
        // Beautiful success message
        player.sendMessage(Text.literal("👋 ")
            .formatted(Formatting.YELLOW)
            .append(Text.literal("You have left ")
                .formatted(Formatting.GRAY))
            .append(Text.literal(clanName)
                .formatted(Formatting.GOLD, Formatting.BOLD))
            .append(Text.literal("!")
                .formatted(Formatting.GRAY)), false);
        
        // Notify remaining clan members
        Text leaveNotification = Text.literal("👋 ")
            .formatted(Formatting.LIGHT_PURPLE)
            .append(Text.literal(player.getName().getString())
                .formatted(Formatting.RED, Formatting.BOLD))
            .append(Text.literal(" has left your clan!")
                .formatted(Formatting.LIGHT_PURPLE));
        
        for (UUID memberUuid : clan.getMembers()) {
            ServerPlayerEntity member = dataManager.getServer().getPlayerManager().getPlayer(memberUuid);
            if (member != null) {
                member.sendMessage(leaveNotification, false);
            }
        }
        
        return 1;
    }

    // BEAUTIFUL /clan disband
    private static int disbandClan(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        ClanDataManager dataManager = Simpleclans.getDataManager();
        
        Clan clan = dataManager.getPlayerClan(player.getUuid());
        if (clan == null) {
            player.sendMessage(Text.literal("❌ You are not in a clan!")
                .formatted(Formatting.RED), false);
            return 0;
        }
        
        if (!clan.isKing(player.getUuid())) {
            player.sendMessage(Text.literal("❌ Only the ")
                .formatted(Formatting.RED)
                .append(Text.literal("King")
                    .formatted(Formatting.YELLOW, Formatting.BOLD))
                .append(Text.literal(" can disband the clan!")
                    .formatted(Formatting.RED)), false);
            return 0;
        }
        
        // Check for confirmation
        if (!ClanUtils.hasConfirmation(player, "disband")) {
            player.sendMessage(Text.literal("⚠️ ")
                .formatted(Formatting.RED, Formatting.BOLD)
                .append(Text.literal("Are you sure you want to disband ")
                    .formatted(Formatting.YELLOW))
                .append(Text.literal(clan.getOriginalName())
                    .formatted(Formatting.GOLD, Formatting.BOLD))
                .append(Text.literal("?")
                    .formatted(Formatting.YELLOW))
                .append(Text.literal("\n🔥 This action cannot be undone!")
                    .formatted(Formatting.RED, Formatting.BOLD))
                .append(Text.literal("\nRun ")
                    .formatted(Formatting.GRAY))
                .append(Text.literal("/clan disband")
                    .formatted(Formatting.YELLOW))
                .append(Text.literal(" again to confirm.")
                    .formatted(Formatting.GRAY)), false);
            
            ClanUtils.addConfirmation(player, "disband");
            return 0;
        }
        
        // Disband clan
        String clanName = clan.getOriginalName();
        
        // Notify all members
        Text disbandNotification = Text.literal("💥 ")
            .formatted(Formatting.RED, Formatting.BOLD)
            .append(Text.literal("The clan ")
                .formatted(Formatting.WHITE))
            .append(Text.literal(clanName)
                .formatted(Formatting.GOLD, Formatting.BOLD))
            .append(Text.literal(" has been disbanded!")
                .formatted(Formatting.WHITE));
        
        for (UUID memberUuid : clan.getMembers()) {
            ServerPlayerEntity member = dataManager.getServer().getPlayerManager().getPlayer(memberUuid);
            if (member != null) {
                member.sendMessage(disbandNotification, false);
            }
        }
        
        dataManager.disbandClan(clan.getName());
        
        // 🆕 CRITICAL: Clear confirmation after successful disband
        ClanUtils.clearConfirmation(player, "disband");

        // Success message to king
        player.sendMessage(Text.literal("💥 ")
            .formatted(Formatting.RED, Formatting.BOLD)
            .append(Text.literal("Clan disbanded successfully!")
                .formatted(Formatting.WHITE)), false);
        
        return 1;
    }

    // BEAUTIFUL /clan ally
    private static int allyClan(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String targetClanName = StringArgumentType.getString(context, "clanName");
        ClanDataManager dataManager = Simpleclans.getDataManager();
        
        Clan clan = dataManager.getPlayerClan(player.getUuid());
        if (clan == null) {
            player.sendMessage(Text.literal("❌ You are not in a clan!").formatted(Formatting.RED), false);
            return 0;
        }
        
        if (!clan.isKing(player.getUuid())) {
            player.sendMessage(Text.literal("❌ Only the King can create alliances!").formatted(Formatting.RED), false);
            return 0;
        }
        
        Clan targetClan = dataManager.getClan(targetClanName);
        if (targetClan == null) {
            player.sendMessage(Text.literal("❌ Clan not found!").formatted(Formatting.RED), false);
            return 0;
        }
        
        if (targetClan.getName().equalsIgnoreCase(clan.getName())) {
            player.sendMessage(Text.literal("❌ You cannot ally with yourself!").formatted(Formatting.RED), false);
            return 0;
        }
        
        // Check if already allies
        String targetKey = targetClan.getName().toLowerCase();
        if (clan.getAllies().contains(targetKey)) {
            player.sendMessage(Text.literal("❌ You are already allied with ")
                .formatted(Formatting.RED)
                .append(Text.literal(targetClan.getOriginalName())
                    .formatted(Formatting.YELLOW, Formatting.BOLD))
                .append(Text.literal("!").formatted(Formatting.RED)), false);
            return 0;
        }
        
        // Check if there's already a pending alliance request FROM target clan
        String confirmationKey = "ally_request_from_" + targetClan.getName().toLowerCase();
        if (ClanUtils.hasConfirmation(player, confirmationKey)) {
            // Accept the alliance request
            ClanUtils.clearConfirmation(player, confirmationKey);
            
            // Check gold cost
            if (!ClanUtils.consumeItems(player, Items.GOLD_INGOT, Simpleclans.getConfig().allyCost)) {
                player.sendMessage(Text.literal("❌ Insufficient funds! You need " + 
                    Simpleclans.getConfig().allyCost + " gold ingots.").formatted(Formatting.RED), false);
                return 0;
            }
            
            // Create bidirectional alliance
            String clanKey = clan.getName().toLowerCase();
            
            clan.getEnemies().remove(targetKey);
            clan.getNeutrals().remove(targetKey);
            clan.getAllies().add(targetKey);
            
            targetClan.getEnemies().remove(clanKey);
            targetClan.getNeutrals().remove(clanKey);
            targetClan.getAllies().add(clanKey);
            
            // Success messages
            player.sendMessage(Text.literal("🤝 Alliance formed with ")
                .formatted(Formatting.GREEN)
                .append(Text.literal(targetClan.getOriginalName())
                    .formatted(Formatting.YELLOW, Formatting.BOLD))
                .append(Text.literal("!").formatted(Formatting.GREEN)), false);
            
            // Notify target clan
            Text allyNotification = Text.literal("🤝 ")
                .formatted(Formatting.GREEN)
                .append(Text.literal(clan.getOriginalName())
                    .formatted(Formatting.YELLOW, Formatting.BOLD))
                .append(Text.literal(" has accepted your alliance request!")
                    .formatted(Formatting.GREEN));
            
            for (UUID memberUuid : targetClan.getMembers()) {
                ServerPlayerEntity member = dataManager.getServer().getPlayerManager().getPlayer(memberUuid);
                if (member != null) {
                    member.sendMessage(allyNotification, false);
                }
            }
            
            ClanUtils.notifyGoldConsumption(player, Simpleclans.getConfig().allyCost, "alliance with " + targetClan.getOriginalName());
            dataManager.save();
            return 1;
        } else {
            // Send alliance request
            if (!ClanUtils.consumeItems(player, Items.GOLD_INGOT, Simpleclans.getConfig().allyCost)) {
                player.sendMessage(Text.literal("❌ Insufficient funds! You need " + 
                    Simpleclans.getConfig().allyCost + " gold ingots.").formatted(Formatting.RED), false);
                return 0;
            }
            
            // Store alliance request for 5 minutes
            ServerPlayerEntity targetKing = dataManager.getServer().getPlayerManager().getPlayer(targetClan.getKing());
            if (targetKing == null) {
                player.sendMessage(Text.literal("❌ Target clan's King is not online!").formatted(Formatting.RED), false);
                return 0;
            }
            
            String requestKey = "ally_request_from_" + clan.getName().toLowerCase();
            ClanUtils.addConfirmation(targetKing, requestKey);
            
            // Notify sender
            player.sendMessage(Text.literal("🤝 Alliance request sent to ")
                .formatted(Formatting.YELLOW)
                .append(Text.literal(targetClan.getOriginalName())
                    .formatted(Formatting.GOLD, Formatting.BOLD))
                .append(Text.literal("!")
                    .formatted(Formatting.YELLOW)), false);
            
            // Notify target with clearer instructions
            targetKing.sendMessage(Text.literal("🤝 ")
                .formatted(Formatting.GREEN)
                .append(Text.literal(clan.getOriginalName())
                    .formatted(Formatting.YELLOW, Formatting.BOLD))
                .append(Text.literal(" wants to form an alliance!")
                    .formatted(Formatting.GREEN))
                .append(Text.literal("\n💰 Cost: ")
                    .formatted(Formatting.GRAY))
                .append(Text.literal(Simpleclans.getConfig().allyCost + " gold")
                    .formatted(Formatting.GOLD))
                .append(Text.literal("\nUse ")
                    .formatted(Formatting.GRAY))
                .append(Text.literal("/clan ally " + clan.getOriginalName())
                    .formatted(Formatting.YELLOW))
                .append(Text.literal(" to accept!")
                    .formatted(Formatting.GRAY)), false);
            
            ClanUtils.notifyGoldConsumption(player, Simpleclans.getConfig().allyCost, "alliance request to " + targetClan.getOriginalName());
            dataManager.save();
            return 1;
        }
    }

    // BEAUTIFUL /clan enemy
    private static int enemyClan(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String targetClanName = StringArgumentType.getString(context, "clanName");
        ClanDataManager dataManager = Simpleclans.getDataManager();
        
        Clan clan = dataManager.getPlayerClan(player.getUuid());
        if (clan == null) {
            player.sendMessage(Text.literal("❌ You are not in a clan!").formatted(Formatting.RED), false);
            return 0;
        }
        
        if (!clan.isKing(player.getUuid())) {
            player.sendMessage(Text.literal("❌ Only the King can declare enemies!").formatted(Formatting.RED), false);
            return 0;
        }
        
        Clan targetClan = dataManager.getClan(targetClanName);
        if (targetClan == null) {
            player.sendMessage(Text.literal("❌ Clan not found!").formatted(Formatting.RED), false);
            return 0;
        }
        
        if (targetClan.getName().equalsIgnoreCase(clan.getName())) {
            player.sendMessage(Text.literal("❌ You cannot declare yourself as enemy!").formatted(Formatting.RED), false);
            return 0;
        }
        
        // Check gold cost
        if (!ClanUtils.consumeItems(player, Items.GOLD_INGOT, Simpleclans.getConfig().enemyCost)) {
            player.sendMessage(Text.literal("❌ Insufficient funds! You need " + 
                Simpleclans.getConfig().enemyCost + " gold ingots.").formatted(Formatting.RED), false);
            return 0;
        }
        
        // Create bidirectional enemy relationship
        String targetKey = targetClan.getName().toLowerCase();
        String clanKey = clan.getName().toLowerCase();
        
        clan.getAllies().remove(targetKey);
        clan.getNeutrals().remove(targetKey);
        clan.getEnemies().add(targetKey);
        
        targetClan.getAllies().remove(clanKey);
        targetClan.getNeutrals().remove(clanKey);
        targetClan.getEnemies().add(clanKey);
        
        // Success message
        player.sendMessage(Text.literal("⚔️ ")
            .formatted(Formatting.RED)
            .append(Text.literal(targetClan.getOriginalName())
                .formatted(Formatting.YELLOW, Formatting.BOLD))
            .append(Text.literal(" declared as enemy!")
                .formatted(Formatting.RED)), false);
        
        // Global server announcement
        Text globalMessage = Text.literal("⚔️ ")
            .formatted(Formatting.RED)
            .append(Text.literal(clan.getOriginalName())
                .formatted(Formatting.YELLOW, Formatting.BOLD))
            .append(Text.literal(" has declared ")
                .formatted(Formatting.WHITE))
            .append(Text.literal(targetClan.getOriginalName())
                .formatted(Formatting.RED, Formatting.BOLD))
            .append(Text.literal(" as their enemy!")
                .formatted(Formatting.WHITE));
        
        // Broadcast to entire server
        for (ServerPlayerEntity onlinePlayer : dataManager.getServer().getPlayerManager().getPlayerList()) {
            onlinePlayer.sendMessage(globalMessage, false);
        }
        
        ClanUtils.notifyGoldConsumption(player, Simpleclans.getConfig().enemyCost, "declaring " + targetClan.getOriginalName() + " as enemy");
        dataManager.save();
        return 1;
    }

    // BEAUTIFUL /clan neutral
    private static int neutralClan(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String targetClanName = StringArgumentType.getString(context, "clanName");
        ClanDataManager dataManager = Simpleclans.getDataManager();
        
        Clan clan = dataManager.getPlayerClan(player.getUuid());
        if (clan == null) {
            player.sendMessage(Text.literal("❌ You are not in a clan!").formatted(Formatting.RED), false);
            return 0;
        }
        
        if (!clan.isKing(player.getUuid())) {
            player.sendMessage(Text.literal("❌ Only the King can set neutral relations!").formatted(Formatting.RED), false);
            return 0;
        }
        
        Clan targetClan = dataManager.getClan(targetClanName);
        if (targetClan == null) {
            player.sendMessage(Text.literal("❌ Clan not found!").formatted(Formatting.RED), false);
            return 0;
        }
        
        // Check gold cost
        if (!ClanUtils.consumeItems(player, Items.GOLD_INGOT, Simpleclans.getConfig().neutralCost)) {
            player.sendMessage(Text.literal("❌ Insufficient funds! You need " + 
                Simpleclans.getConfig().neutralCost + " gold ingots.").formatted(Formatting.RED), false);
            return 0;
        }
        
        // Set neutral relationship
        String targetKey = targetClan.getName().toLowerCase();
        String clanKey = clan.getName().toLowerCase();
        
        clan.getAllies().remove(targetKey);
        clan.getEnemies().remove(targetKey);
        clan.getNeutrals().add(targetKey);
        
        targetClan.getAllies().remove(clanKey);
        targetClan.getEnemies().remove(clanKey);
        targetClan.getNeutrals().add(clanKey);
        
        // Success message
        player.sendMessage(Text.literal("🤷 Neutral relations set with ")
            .formatted(Formatting.YELLOW)
            .append(Text.literal(targetClan.getOriginalName())
                .formatted(Formatting.GOLD, Formatting.BOLD))
            .append(Text.literal("!").formatted(Formatting.YELLOW)), false);
        
        // Notify target clan
        Text neutralNotification = Text.literal("🤷 ")
            .formatted(Formatting.YELLOW)
            .append(Text.literal(clan.getOriginalName())
                .formatted(Formatting.GOLD, Formatting.BOLD))
            .append(Text.literal(" has set neutral relations with your clan!")
                .formatted(Formatting.YELLOW));
        
        for (UUID memberUuid : targetClan.getMembers()) {
            ServerPlayerEntity member = dataManager.getServer().getPlayerManager().getPlayer(memberUuid);
            if (member != null) {
                member.sendMessage(neutralNotification, false);
            }
        }
        
        ClanUtils.notifyGoldConsumption(player, Simpleclans.getConfig().neutralCost, "neutral relations with " + targetClan.getOriginalName());
        dataManager.save();
        return 1;
    }

    // BEAUTIFUL /clan declare (war)
    private static int declareWar(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String targetClanName = StringArgumentType.getString(context, "clanName");
        ClanDataManager dataManager = Simpleclans.getDataManager();
        
        if (!dataManager.areWarsEnabled()) {
            player.sendMessage(Text.literal("❌ Wars are currently disabled!")
                .formatted(Formatting.RED), false);
            return 0;
        }
        
        Clan clan = dataManager.getPlayerClan(player.getUuid());
        if (clan == null) {
            player.sendMessage(Text.literal("❌ You are not in a clan!").formatted(Formatting.RED), false);
            return 0;
        }
        
        if (!clan.isKing(player.getUuid())) {
            player.sendMessage(Text.literal("❌ Only the King can declare war!").formatted(Formatting.RED), false);
            return 0;
        }
        
        if (clan.isAtWar()) {
            player.sendMessage(Text.literal("❌ Your clan is already at war!")
                .formatted(Formatting.RED), false);
            return 0;
        }
        
        Clan targetClan = dataManager.getClan(targetClanName);
        if (targetClan == null) {
            player.sendMessage(Text.literal("❌ Clan not found!").formatted(Formatting.RED), false);
            return 0;
        }
        
        if (targetClan.getName().equalsIgnoreCase(clan.getName())) {
            player.sendMessage(Text.literal("❌ You cannot declare war on yourself!").formatted(Formatting.RED), false);
            return 0;
        }
        
        // Check if they are enemies
        String targetKey = targetClan.getName().toLowerCase();
        if (!clan.getEnemies().contains(targetKey)) {
            player.sendMessage(Text.literal("❌ You can only declare war on enemy clans! Use ")
                .formatted(Formatting.RED)
                .append(Text.literal("/clan enemy " + targetClanName)
                    .formatted(Formatting.YELLOW))
                .append(Text.literal(" first.")
                    .formatted(Formatting.RED)), false);
            return 0;
        }
        
        if (targetClan.isAtWar()) {
            player.sendMessage(Text.literal("❌ That clan is already at war!")
                .formatted(Formatting.RED), false);
            return 0;
        }
        
        // Check gold cost
        if (!ClanUtils.consumeItems(player, Items.GOLD_INGOT, Simpleclans.getConfig().warDeclarationCost)) {
            player.sendMessage(Text.literal("❌ Insufficient funds! You need " + 
                Simpleclans.getConfig().warDeclarationCost + " gold ingots to declare war.")
                .formatted(Formatting.RED), false);
            return 0;
        }
        
        // Start war
        clan.startWar(targetClan.getName().toLowerCase());
        targetClan.startWar(clan.getName().toLowerCase());
        
        // Success message
        player.sendMessage(Text.literal("⚔️ WAR DECLARED! ")
            .formatted(Formatting.RED, Formatting.BOLD)
            .append(Text.literal("Your clan is now at war with ")
                .formatted(Formatting.WHITE))
            .append(Text.literal(targetClan.getOriginalName())
                .formatted(Formatting.YELLOW, Formatting.BOLD))
            .append(Text.literal("!")
                .formatted(Formatting.WHITE)), false);
        
        // Global war announcement
        Text warAnnouncement = Text.literal("🔥 WAR DECLARED! ")
            .formatted(Formatting.RED, Formatting.BOLD)
            .append(Text.literal(clan.getOriginalName())
                .formatted(Formatting.YELLOW, Formatting.BOLD))
            .append(Text.literal(" vs ")
                .formatted(Formatting.WHITE))
            .append(Text.literal(targetClan.getOriginalName())
                .formatted(Formatting.YELLOW, Formatting.BOLD))
            .append(Text.literal(" - First to kill all enemies wins!")
                .formatted(Formatting.WHITE));
        
        // Broadcast to entire server
        for (ServerPlayerEntity onlinePlayer : dataManager.getServer().getPlayerManager().getPlayerList()) {
            onlinePlayer.sendMessage(warAnnouncement, false);
        }
        
        ClanUtils.notifyGoldConsumption(player, Simpleclans.getConfig().warDeclarationCost, "declaring war on " + targetClan.getOriginalName());
        dataManager.save();
        return 1;
    }

    private static int declareEnemy(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String targetClanName = StringArgumentType.getString(context, "clanName");
        ClanDataManager dataManager = Simpleclans.getDataManager();
        
        Clan clan = dataManager.getPlayerClan(player.getUuid());
        if (clan == null) {
            player.sendMessage(Text.literal("❌ You are not in a clan!").formatted(Formatting.RED), false);
            return 0;
        }
        
        if (!clan.isKing(player.getUuid())) {
            player.sendMessage(Text.literal("❌ Only the King can declare enemies!").formatted(Formatting.RED), false);
            return 0;
        }
        
        Clan targetClan = dataManager.getClan(targetClanName);
        if (targetClan == null) {
            player.sendMessage(Text.literal("❌ Clan not found!").formatted(Formatting.RED), false);
            return 0;
        }
        
        if (targetClan.getName().equalsIgnoreCase(clan.getName())) {
            player.sendMessage(Text.literal("❌ You cannot declare yourself as enemy!").formatted(Formatting.RED), false);
            return 0;
        }
        
        // Check gold cost
        if (!ClanUtils.consumeItems(player, Items.GOLD_INGOT, Simpleclans.getConfig().enemyCost)) {
            player.sendMessage(Text.literal("❌ Insufficient funds! You need " + 
                Simpleclans.getConfig().enemyCost + " gold ingots.").formatted(Formatting.RED), false);
            return 0;
        }
        
        // BIDIRECTIONAL: Both clans become enemies
        String targetKey = targetClan.getName().toLowerCase();
        String clanKey = clan.getName().toLowerCase();
        
        // Remove from allies/neutrals and add to enemies
        clan.getAllies().remove(targetKey);
        clan.getNeutrals().remove(targetKey);
        clan.getEnemies().add(targetKey);
        
        targetClan.getAllies().remove(clanKey);
        targetClan.getNeutrals().remove(clanKey);
        targetClan.getEnemies().add(clanKey);
        
        // Notify gold consumption
        ClanUtils.notifyGoldConsumption(player, Simpleclans.getConfig().enemyCost, "declaring " + targetClan.getOriginalName() + " as enemy");
        
        // Success message to declarer
        player.sendMessage(Text.literal("⚔️ ")
            .formatted(Formatting.RED, Formatting.BOLD)
            .append(Text.literal(targetClan.getOriginalName())
                .formatted(Formatting.YELLOW, Formatting.BOLD))
            .append(Text.literal(" declared as enemy!")
                .formatted(Formatting.RED)), false);
        
        // NEW: Global server announcement
        Text globalMessage = Text.literal("⚔️ ")
            .formatted(Formatting.RED)
            .append(Text.literal(clan.getOriginalName())
                .formatted(Formatting.YELLOW, Formatting.BOLD))
            .append(Text.literal(" has declared ")
                .formatted(Formatting.WHITE))
            .append(Text.literal(targetClan.getOriginalName())
                .formatted(Formatting.RED, Formatting.BOLD))
            .append(Text.literal(" as their enemy!")
                .formatted(Formatting.WHITE));
        
        // Broadcast to entire server
        for (ServerPlayerEntity onlinePlayer : dataManager.getServer().getPlayerManager().getPlayerList()) {
            onlinePlayer.sendMessage(globalMessage, false);
        }
        
        dataManager.save();
        return 1;
    }

    private static int clanBoards(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        ClanDataManager dataManager = Simpleclans.getDataManager();
        
        List<Clan> allClans = new ArrayList<>(dataManager.getAllClans());
        
        if (allClans.isEmpty()) {
            player.sendMessage(Text.literal("📊 No clans exist yet to display rankings!")
                .formatted(Formatting.YELLOW), false);
            return 0;
        }
        
        // Sort clans by power (descending)
        allClans.sort((a, b) -> Integer.compare(b.calculatePower(), a.calculatePower()));
        
        // Beautiful leaderboard
        MutableText boardMessage = Text.literal("═══════════════════════════")
            .formatted(Formatting.GOLD, Formatting.BOLD)
            .append(Text.literal("\n🏆 ")
                .formatted(Formatting.YELLOW))
            .append(Text.literal("CLAN LEADERBOARDS")
                .formatted(Formatting.GOLD, Formatting.BOLD))
            .append(Text.literal("\n═══════════════════════════")
                .formatted(Formatting.GOLD, Formatting.BOLD));
        
        // Show top clans (limit to top 10)
        int displayCount = Math.min(allClans.size(), 10);
        for (int i = 0; i < displayCount; i++) {
            Clan clan = allClans.get(i);
            String rank = String.valueOf(i + 1);
            Formatting rankColor = i == 0 ? Formatting.GOLD : i == 1 ? Formatting.GRAY : i == 2 ? Formatting.YELLOW : Formatting.WHITE;
            
            boardMessage = boardMessage.append(Text.literal("\n" + rank + ". ")
                    .formatted(rankColor, Formatting.BOLD))
                .append(Text.literal(clan.getOriginalName())
                    .formatted(Formatting.AQUA, Formatting.BOLD))
                .append(Text.literal(" - Power: ")
                    .formatted(Formatting.GRAY))
                .append(Text.literal(String.valueOf(clan.calculatePower()))
                    .formatted(Formatting.GREEN, Formatting.BOLD))
                .append(Text.literal(" (")
                    .formatted(Formatting.GRAY))
                .append(Text.literal(clan.getKills() + "K/" + clan.getDeaths() + "D")
                    .formatted(Formatting.WHITE))
                .append(Text.literal(")")
                    .formatted(Formatting.GRAY));
        }
        
        boardMessage = boardMessage.append(Text.literal("\n═══════════════════════════")
            .formatted(Formatting.GOLD, Formatting.BOLD));
        
        player.sendMessage(boardMessage, false);
        return 1;
    }

    private static Text getPlayerDisplayName(UUID playerId, ClanDataManager dataManager) {
        ServerPlayerEntity onlinePlayer = dataManager.getServer().getPlayerManager().getPlayer(playerId);
        
        if (onlinePlayer != null) {
            // Online player - green name
            return Text.literal(onlinePlayer.getName().getString())
                .formatted(Formatting.GREEN);
        } else {
            // Offline player - get stored name, gray color
            String storedName = "Unknown";
            for (Clan clan : dataManager.getAllClans()) {
                String name = clan.getMemberName(playerId);
                if (!name.equals("Unknown")) {
                    storedName = name;
                    break;
                }
            }
            return Text.literal(storedName + " (offline)")
                .formatted(Formatting.GRAY);
        }
    }
}
