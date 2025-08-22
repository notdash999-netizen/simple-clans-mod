package com.clans.data;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClanMemberStatus {
    private static final Map<UUID, Boolean> onlineStatus = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastSeen = new ConcurrentHashMap<>();
    
    public static void setOnline(UUID playerId) {
        onlineStatus.put(playerId, true);
        lastSeen.put(playerId, System.currentTimeMillis());
    }
    
    public static void setOffline(UUID playerId) {
        onlineStatus.put(playerId, false);
        lastSeen.put(playerId, System.currentTimeMillis());
    }
    
    public static boolean isOnline(UUID playerId) {
        return onlineStatus.getOrDefault(playerId, false);
    }
    
    public static long getLastSeen(UUID playerId) {
        return lastSeen.getOrDefault(playerId, 0L);
    }
    
    public static void removePlayer(UUID playerId) {
        onlineStatus.remove(playerId);
        lastSeen.remove(playerId);
    }
}
