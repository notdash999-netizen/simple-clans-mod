package com.clans.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.clans.Simpleclans;
import com.clans.model.Clan;
import com.clans.model.ClanInvitation;
import com.clans.model.ClanRole;
import com.clans.util.ClanUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClanDataManager {
    private static final String DATA_DIR = "world/simpleclans/";
    private static final String CLANS_FILE = "clans.json";
    private static final String PLAYERS_FILE = "players.json";
    private static final String TIMERS_FILE = "timers.json";
    
    private MinecraftServer server;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    // In-memory data structures for performance
    private final Map<String, Clan> clans = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerToClan = new ConcurrentHashMap<>();
    private final Map<UUID, ClanInvitation> pendingInvitations = new ConcurrentHashMap<>();
    private final Map<String, Long> clanTimers = new ConcurrentHashMap<>();
    private final Set<UUID> playersToNotify = ConcurrentHashMap.newKeySet();
    
    // War system state
    private boolean warsEnabled = false;
    
    // FIXED: Alliance request tracking to prevent spam
    private final Map<String, Map<String, Long>> allianceRequests = new ConcurrentHashMap<>();
    
    public void initialize(MinecraftServer server) {
        this.server = server;
        createDataDirectory();
        loadAllData();
        Simpleclans.LOGGER.info("Clan data manager initialized successfully");
    }
    
    private void createDataDirectory() {
        try {
            Path dataDir = Paths.get(DATA_DIR);
            if (!Files.exists(dataDir)) {
                Files.createDirectories(dataDir);
                Simpleclans.LOGGER.info("Created clan data directory: " + DATA_DIR);
            }
        } catch (Exception e) {
            Simpleclans.LOGGER.error("Failed to create clan data directory", e);
        }
    }
    
    private void loadAllData() {
        loadClans();
        loadPlayerMappings();
        loadTimers();
        Simpleclans.LOGGER.info("Loaded all clan data successfully");
    }
    
    @SuppressWarnings("unchecked")
    private void loadClans() {
        try {
            File file = new File(DATA_DIR + CLANS_FILE);
            if (file.exists()) {
                try (FileReader reader = new FileReader(file)) {
                    Type mapType = new TypeToken<Map<String, Clan>>(){}.getType();
                    Map<String, Clan> loadedClans = gson.fromJson(reader, mapType);
                    if (loadedClans != null) {
                        clans.putAll(loadedClans);
                        Simpleclans.LOGGER.info("Loaded {} clans", clans.size());
                    }
                }
            }
        } catch (Exception e) {
            Simpleclans.LOGGER.error("Failed to load clans", e);
        }
    }
    
    @SuppressWarnings("unchecked")
    private void loadPlayerMappings() {
        try {
            File file = new File(DATA_DIR + PLAYERS_FILE);
            if (file.exists()) {
                try (FileReader reader = new FileReader(file)) {
                    Type mapType = new TypeToken<Map<String, String>>(){}.getType();
                    Map<String, String> stringMappings = gson.fromJson(reader, mapType);
                    if (stringMappings != null) {
                        for (Map.Entry<String, String> entry : stringMappings.entrySet()) {
                            try {
                                UUID playerId = UUID.fromString(entry.getKey());
                                playerToClan.put(playerId, entry.getValue());
                            } catch (IllegalArgumentException e) {
                                Simpleclans.LOGGER.warn("Invalid UUID in player mappings: " + entry.getKey());
                            }
                        }
                        Simpleclans.LOGGER.info("Loaded {} player mappings", playerToClan.size());
                    }
                }
            }
        } catch (Exception e) {
            Simpleclans.LOGGER.error("Failed to load player mappings", e);
        }
    }
    
    @SuppressWarnings("unchecked")
    private void loadTimers() {
        try {
            File file = new File(DATA_DIR + TIMERS_FILE);
            if (file.exists()) {
                try (FileReader reader = new FileReader(file)) {
                    Type mapType = new TypeToken<Map<String, Long>>(){}.getType();
                    Map<String, Long> loadedTimers = gson.fromJson(reader, mapType);
                    if (loadedTimers != null) {
                        clanTimers.putAll(loadedTimers);
                        Simpleclans.LOGGER.info("Loaded {} clan timers", clanTimers.size());
                    }
                }
            }
        } catch (Exception e) {
            Simpleclans.LOGGER.error("Failed to load clan timers", e);
        }
    }
    
    public void save() {
        saveClans();
        savePlayerMappings();
        saveTimers();
    }
    
    private void saveClans() {
        try {
            File file = new File(DATA_DIR + CLANS_FILE);
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(clans, writer);
            }
        } catch (Exception e) {
            Simpleclans.LOGGER.error("Failed to save clans", e);
        }
    }
    
    private void savePlayerMappings() {
        try {
            File file = new File(DATA_DIR + PLAYERS_FILE);
            Map<String, String> stringMappings = new HashMap<>();
            for (Map.Entry<UUID, String> entry : playerToClan.entrySet()) {
                stringMappings.put(entry.getKey().toString(), entry.getValue());
            }
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(stringMappings, writer);
            }
        } catch (Exception e) {
            Simpleclans.LOGGER.error("Failed to save player mappings", e);
        }
    }
    
    private void saveTimers() {
        try {
            File file = new File(DATA_DIR + TIMERS_FILE);
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(clanTimers, writer);
            }
        } catch (Exception e) {
            Simpleclans.LOGGER.error("Failed to save clan timers", e);
        }
    }
    
    // Enhanced clan operations
    public boolean createClan(String name, UUID kingId) {
        // Enhanced validation
        String validationError = ClanUtils.validateClanName(name);
        if (validationError != null) return false;
        
        // Case-insensitive duplicate check
        if (clanExistsIgnoreCase(name)) return false;
        if (playerToClan.containsKey(kingId)) return false;
        
        Clan clan = new Clan(name, kingId);
        clans.put(clan.getName(), clan);
        playerToClan.put(kingId, clan.getName());
        clanTimers.put(clan.getName(), System.currentTimeMillis());
        
        save();
        return true;
    }
    
    public boolean clanExistsIgnoreCase(String name) {
        String lowerName = name.toLowerCase();
        return clans.containsKey(lowerName);
    }
    
    public boolean disbandClan(String clanName) {
        String key = clanName.toLowerCase();
        Clan clan = clans.get(key);
        if (clan == null) return false;
        
        // Remove all members from clan
        for (UUID member : clan.getMembers()) {
            playerToClan.remove(member);
        }
        
        // Clean up bidirectional relationships
        for (Clan otherClan : clans.values()) {
            otherClan.getAllies().remove(key);
            otherClan.getEnemies().remove(key);
            otherClan.getNeutrals().remove(key);
        }
        
        clans.remove(key);
        clanTimers.remove(key);
        
        save();
        return true;
    }
    
    public boolean joinClan(UUID playerId, String clanName) {
        String key = clanName.toLowerCase();
        Clan clan = clans.get(key);
        if (clan == null) return false;
        if (playerToClan.containsKey(playerId)) return false;
        
        // FIXED: Proper clan size enforcement - this was the critical missing piece
        if (clan.getMembers().size() >= Simpleclans.getConfig().maxClanSize) {
            return false;
        }
        
        if (!clan.addMember(playerId, getPlayerName(playerId))) return false;
        
        playerToClan.put(playerId, key);
        pendingInvitations.remove(playerId);
        
        save();
        return true;
    }
    
    public boolean leaveClan(UUID playerId) {
        String clanKey = playerToClan.get(playerId);
        if (clanKey == null) return false;
        
        Clan clan = clans.get(clanKey);
        if (clan == null) return false;
        if (clan.isKing(playerId)) return false;
        
        clan.removeMember(playerId);
        playerToClan.remove(playerId);
        
        save();
        return true;
    }
    
    // NEW: Enhanced diplomacy with bidirectional updates
    public void setDiplomacy(String clan1Name, String clan2Name, String relationType) {
        Clan clan1 = getClan(clan1Name);
        Clan clan2 = getClan(clan2Name);
        if (clan1 == null || clan2 == null) return;
        
        String clan1Key = clan1.getName();
        String clan2Key = clan2.getName();
        
        // Clean up existing relationships for both clans
        clan1.getAllies().remove(clan2Key);
        clan1.getEnemies().remove(clan2Key);
        clan1.getNeutrals().remove(clan2Key);
        
        clan2.getAllies().remove(clan1Key);
        clan2.getEnemies().remove(clan1Key);
        clan2.getNeutrals().remove(clan1Key);
        
        // Set new relationship for both clans
        switch (relationType.toLowerCase()) {
            case "ally":
                clan1.getAllies().add(clan2Key);
                clan2.getAllies().add(clan1Key);
                break;
            case "enemy":
                clan1.getEnemies().add(clan2Key);
                clan2.getEnemies().add(clan1Key);
                
                // Notify target clan
                Text enemyNotification = Text.literal(clan1.getOriginalName() + " has declared you as an enemy!")
                    .formatted(Formatting.RED);
                ClanUtils.broadcastToClan(clan2, enemyNotification, server);
                break;
            case "neutral":
                clan1.getNeutrals().add(clan2Key);
                clan2.getNeutrals().add(clan1Key);
                
                // Notify target clan
                Text neutralNotification = Text.literal(clan1.getOriginalName() + " has set you as neutral.")
                    .formatted(Formatting.YELLOW);
                ClanUtils.broadcastToClan(clan2, neutralNotification, server);
                break;
        }
        
        save();
    }
    
    // NEW: Check if already allied to prevent duplicate alliances
    public boolean areAllied(String clan1Name, String clan2Name) {
        Clan clan1 = getClan(clan1Name);
        if (clan1 == null) return false;
        return clan1.getAllies().contains(clan2Name.toLowerCase());
    }
    
    // Invitation system
    public void addInvitation(UUID playerId, String clanName) {
        ClanInvitation invitation = new ClanInvitation(clanName, playerId);
        pendingInvitations.put(playerId, invitation);
    }
    
    public ClanInvitation getInvitation(UUID playerId) {
        ClanInvitation invitation = pendingInvitations.get(playerId);
        if (invitation != null && invitation.isExpired()) {
            pendingInvitations.remove(playerId);
            return null;
        }
        return invitation;
    }
    
    public void removeInvitation(UUID playerId) {
        pendingInvitations.remove(playerId);
    }

    public boolean hasValidInvitation(UUID playerId, String clanName) {
        ClanInvitation invitation = pendingInvitations.get(playerId);
        if (invitation == null) return false;
        
        return invitation.getClanName().equalsIgnoreCase(clanName) && 
               !invitation.isExpired();
    }
    
    // Player connection events
    public void onPlayerJoin(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        ClanMemberStatus.setOnline(playerId);
        
        // Check for notifications
        if (playersToNotify.contains(playerId)) {
            String clanKey = playerToClan.get(playerId);
            if (clanKey == null) {
                // Player was in a clan that got disbanded
                player.sendMessage(Text.literal("Clan vault had insufficient funds, disbanded the clan.")
                    .formatted(Formatting.RED), false);
                playersToNotify.remove(playerId);
            } else {
                Clan clan = clans.get(clanKey);
                if (clan != null && clan.getNetheriteVault() <= 0) {
                    long hoursLeft = 24 - ((System.currentTimeMillis() - clan.getLastNetheriteConsumption()) / (1000 * 60 * 60));
                    if (hoursLeft > 0) {
                        player.sendMessage(Text.literal("Clan needs netherite in vault, " + hoursLeft + " hours left")
                            .formatted(Formatting.RED), false);
                    }
                }
            }
        }
    }
    
    public void onPlayerDisconnect(ServerPlayerEntity player) {
        ClanMemberStatus.setOffline(player.getUuid());
    }
    
    // Getters
    public Clan getClan(String name) {
        return clans.get(name.toLowerCase());
    }
    
    public Clan getPlayerClan(UUID playerId) {
        String clanKey = playerToClan.get(playerId);
        return clanKey != null ? clans.get(clanKey) : null;
    }
    
    public String getPlayerClanName(UUID playerId) {
        return playerToClan.get(playerId);
    }
    
    public ClanRole getPlayerRole(UUID playerId) {
        Clan clan = getPlayerClan(playerId);
        if (clan == null) return null;
        return clan.getPlayerRole(playerId);
    }
    
    public Collection<Clan> getAllClans() {
        return clans.values();
    }
    
    public List<Clan> getTopClans(int limit) {
        return clans.values().stream()
            .sorted((a, b) -> Integer.compare(b.calculatePower(), a.calculatePower()))
            .limit(limit)
            .toList();
    }
    
    // War system
    public boolean isWarsEnabled() { return warsEnabled; }
    public void setWarsEnabled(boolean enabled) { this.warsEnabled = enabled; }

    public boolean areWarsEnabled() {
        return warsEnabled;
    }
    
    public void addPlayerToNotify(UUID playerId) {
        playersToNotify.add(playerId);
    }
    
    public void removePlayerFromNotify(UUID playerId) {
        playersToNotify.remove(playerId);
    }
    
    public void removePlayerFromClan(UUID playerId) {
        String clanKey = playerToClan.get(playerId);
        if (clanKey != null) {
            Clan clan = clans.get(clanKey);
            if (clan != null) {
                // 🆕 Remove from clan's member set (was missing!)
                clan.removeMember(playerId);
                
                // 🆕 Auto-disband if clan becomes empty
                if (clan.getMembers().isEmpty()) {
                    Simpleclans.LOGGER.info("Auto-disbanding empty clan: {}", clan.getOriginalName());
                    disbandClan(clanKey);
                }
            }
            // Remove from global mapping
            playerToClan.remove(playerId);
            save();
        }
    }
    
    public Map<UUID, String> getPlayerToClan() {
        return playerToClan;
    }
    
    // Utility methods
    public boolean clanExists(String name) {
        return clans.containsKey(name.toLowerCase());
    }
    
    public boolean isPlayerInClan(UUID playerId) {
        return playerToClan.containsKey(playerId);
    }
    
    public MinecraftServer getServer() {
        return server;
    }

    private String getPlayerName(UUID playerId) {
        if (server != null) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player != null) {
                return player.getName().getString();
            }
        }
        return "Unknown";
    }

    public void load() {
        loadClans();
        loadPlayerMappings();
        loadTimers();
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    public boolean addPlayerToClan(UUID playerId, String clanName) {
        return joinClan(playerId, clanName);
    }

    // FIXED: Add alliance request tracking (minimal implementation)
    public void addAllianceRequest(String fromClan, String toClan) {
        String fromKey = fromClan.toLowerCase();
        String toKey = toClan.toLowerCase();
        
        allianceRequests.computeIfAbsent(fromKey, k -> new ConcurrentHashMap<>())
            .put(toKey, System.currentTimeMillis() + (Simpleclans.getConfig().allianceRequestExpirationMinutes * 60 * 1000L));
    }

    public boolean hasAllianceRequest(String fromClan, String toClan) {
        String fromKey = fromClan.toLowerCase();
        String toKey = toClan.toLowerCase();
        
        Map<String, Long> requests = allianceRequests.get(fromKey);
        if (requests == null) return false;
        
        Long expirationTime = requests.get(toKey);
        if (expirationTime == null) return false;
        
        // Clean up expired request
        if (System.currentTimeMillis() > expirationTime) {
            requests.remove(toKey);
            if (requests.isEmpty()) {
                allianceRequests.remove(fromKey);
            }
            return false;
        }
        
        return true;
    }

    public boolean processMutualAlliance(String clan1Name, String clan2Name) {
        // Check if both clans have sent alliance requests to each other
        if (hasAllianceRequest(clan1Name, clan2Name) && hasAllianceRequest(clan2Name, clan1Name)) {
            // Remove the requests and set alliance
            removeAllianceRequest(clan1Name, clan2Name);
            removeAllianceRequest(clan2Name, clan1Name);
            setDiplomacy(clan1Name, clan2Name, "ally");
            return true;
        }
        return false;
    }
    
    private void removeAllianceRequest(String fromClan, String toClan) {
        String fromKey = fromClan.toLowerCase();
        String toKey = toClan.toLowerCase();
        
        Map<String, Long> requests = allianceRequests.get(fromKey);
        if (requests != null) {
            requests.remove(toKey);
            if (requests.isEmpty()) {
                allianceRequests.remove(fromKey);
            }
        }
    }
    
    // FIXED: Diplomacy status checking to prevent command spam
    public String getCurrentDiplomacyStatus(String clan1Name, String clan2Name) {
        Clan clan1 = getClan(clan1Name);
        if (clan1 == null) return "neutral";
        
        String clan2Key = clan2Name.toLowerCase();
        
        if (clan1.getAllies().contains(clan2Key)) return "ally";
        if (clan1.getEnemies().contains(clan2Key)) return "enemy";
        if (clan1.getNeutrals().contains(clan2Key)) return "neutral";
        
        return "neutral"; // Default relationship
    }
    
    public boolean isAlreadyInDiplomacyState(String clan1Name, String clan2Name, String desiredState) {
        return getCurrentDiplomacyStatus(clan1Name, clan2Name).equals(desiredState.toLowerCase());
    }
    
    // FIXED: Simple cleanup method for alliance requests (called manually when needed)
    public void cleanupExpiredAllianceRequests() {
        long now = System.currentTimeMillis();
        allianceRequests.entrySet().removeIf(entry -> {
            entry.getValue().entrySet().removeIf(request -> now > request.getValue());
            return entry.getValue().isEmpty();
        });
    }
}
