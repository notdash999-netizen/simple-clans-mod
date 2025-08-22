package com.clans.model;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatToggleState {
    private static final Map<UUID, Boolean> playerChatModes = new ConcurrentHashMap<>();
    
    public static boolean isInClanChatMode(UUID playerId) {
        return playerChatModes.getOrDefault(playerId, false);
    }
    
    public static void toggleClanChatMode(UUID playerId) {
        playerChatModes.put(playerId, !isInClanChatMode(playerId));
    }
    
    public static void setClanChatMode(UUID playerId, boolean enabled) {
        if (enabled) {
            playerChatModes.put(playerId, true);
        } else {
            playerChatModes.remove(playerId);
        }
    }
    
    public static void clearPlayerState(UUID playerId) {
        playerChatModes.remove(playerId);
    }
}
