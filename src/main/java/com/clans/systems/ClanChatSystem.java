package com.clans.systems;

import com.clans.Simpleclans;
import com.clans.data.ClanDataManager;
import com.clans.model.Clan;
import com.clans.model.ChatToggleState;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

public class ClanChatSystem {
    
    public void initialize() {
        Simpleclans.LOGGER.info("Clan chat system initialized (command-based)");
    }
    
    public void sendClanMessage(ServerPlayerEntity sender, String message) {
        ClanDataManager dataManager = Simpleclans.getDataManager();
        Clan clan = dataManager.getPlayerClan(sender.getUuid());
        
        if (clan == null) {
            sender.sendMessage(Text.literal("❌ You are not in a clan!")
                .formatted(Formatting.RED), false);
            return;
        }
        
        // SIMPLIFIED: Just the clean clan chat message
        Text clanMessage = Text.literal("🏰 ")
            .formatted(Formatting.GOLD)
            .append(Text.literal(sender.getName().getString())
                .formatted(Formatting.YELLOW, Formatting.BOLD))
            .append(Text.literal(": ")
                .formatted(Formatting.GRAY))
            .append(Text.literal(message)
                .formatted(Formatting.WHITE));
        
        // Broadcast to all online clan members
        for (UUID memberUuid : clan.getMembers()) {
            ServerPlayerEntity member = dataManager.getServer().getPlayerManager().getPlayer(memberUuid);
            if (member != null) {
                member.sendMessage(clanMessage, false);
            }
        }
    }
}
