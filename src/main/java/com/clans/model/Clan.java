package com.clans.model;

import com.google.gson.annotations.SerializedName;
import java.util.*;

public class Clan {
    @SerializedName("name")
    private String name;
    
    @SerializedName("originalName")
    private String originalName; // Preserve case for display
    
    @SerializedName("king")
    private UUID king;
    
    @SerializedName("advisors")
    private Set<UUID> advisors = new HashSet<>();
    
    @SerializedName("members")
    private Set<UUID> members = new HashSet<>();
    
    @SerializedName("allies")
    private Set<String> allies = new HashSet<>();
    
    @SerializedName("enemies")
    private Set<String> enemies = new HashSet<>();
    
    @SerializedName("neutrals")
    private Set<String> neutrals = new HashSet<>();
    
    @SerializedName("vault")
    private int netheriteVault = 0;
    
    @SerializedName("kills")
    private int totalKills = 0;
    
    @SerializedName("deaths")
    private int totalDeaths = 0;
    
    @SerializedName("created")
    private long createdTime = System.currentTimeMillis();
    
    @SerializedName("lastNetheriteConsumption")
    private long lastNetheriteConsumption = System.currentTimeMillis();
    
    @SerializedName("atWar")
    private boolean atWar = false;
    
    @SerializedName("warTarget")
    private String warTarget = null;
    
    @SerializedName("warKills")
    private Map<UUID, Set<UUID>> warKills = new HashMap<>();
    
    @SerializedName("memberNames")
    private Map<UUID, String> memberNames = new HashMap<>();
    
    public Clan() {}
    
    public Clan(String name, UUID king) {
        this.name = name.toLowerCase(); // For lookups
        this.originalName = name; // Preserve case for display
        this.king = king;
        this.members.add(king);
    }
    
    // Enhanced getters/setters
    public String getName() { return name; }
    public String getOriginalName() { return originalName; }
    public void setName(String name) { 
        this.name = name.toLowerCase();
        this.originalName = name;
    }
    
    public UUID getKing() { return king; }
    public void setKing(UUID king) { this.king = king; }
    
    public Set<UUID> getAdvisors() { return advisors; }
    public void setAdvisors(Set<UUID> advisors) { this.advisors = advisors; }
    
    public Set<UUID> getMembers() { return members; }
    public void setMembers(Set<UUID> members) { this.members = members; }
    
    // Role management methods
    public boolean addAdvisor(UUID player) {
        if (advisors.size() >= 2) return false; // Max 2 advisors
        if (player.equals(king)) return false; // King can't be advisor
        return advisors.add(player);
    }
    
    public boolean removeAdvisor(UUID player) {
        return advisors.remove(player);
    }
    
    public boolean promoteToAdvisor(UUID player) {
        if (!members.contains(player)) return false;
        if (isKing(player)) return false;
        return addAdvisor(player);
    }
    
    public boolean transferKing(UUID newKing) {
        if (!members.contains(newKing)) return false;
        advisors.remove(newKing); // Remove from advisors if they were one
        this.king = newKing;
        return true;
    }
    
    public ClanRole getPlayerRole(UUID player) {
        if (king.equals(player)) return ClanRole.KING;
        if (advisors.contains(player)) return ClanRole.ADVISOR;
        if (members.contains(player)) return ClanRole.PEASANT;
        return null;
    }
    
    // Enhanced member management
    public boolean addMember(UUID playerId, String playerName) {
        if (members.size() >= 4) return false;
        
        boolean added = members.add(playerId);
        if (added) {
            memberNames.put(playerId, playerName);
        }
        return added;
    }
    
    public boolean removeMember(UUID playerId) {
        boolean removed = members.remove(playerId);
        advisors.remove(playerId);
        memberNames.remove(playerId);
        return removed;
    }
    
    public boolean isKing(UUID player) {
        return king.equals(player);
    }
    
    public boolean isAdvisor(UUID player) {
        return advisors.contains(player);
    }
    
    public boolean isMember(UUID player) {
        return members.contains(player);
    }
    
    public Set<String> getAllies() { return allies; }
    public void setAllies(Set<String> allies) { this.allies = allies; }
    
    public Set<String> getEnemies() { return enemies; }
    public void setEnemies(Set<String> enemies) { this.enemies = enemies; }
    
    public Set<String> getNeutrals() { return neutrals; }
    public void setNeutrals(Set<String> neutrals) { this.neutrals = neutrals; }
    
    public int getNetheriteVault() { return netheriteVault; }
    public void setNetheriteVault(int netheriteVault) { this.netheriteVault = netheriteVault; }
    
    public int getTotalKills() { return totalKills; }
    public void setTotalKills(int totalKills) { this.totalKills = totalKills; }
    
    public int getTotalDeaths() { return totalDeaths; }
    public void setTotalDeaths(int totalDeaths) { this.totalDeaths = totalDeaths; }
    
    public long getCreatedTime() { return createdTime; }
    public void setCreatedTime(long createdTime) { this.createdTime = createdTime; }
    
    public long getLastNetheriteConsumption() { return lastNetheriteConsumption; }
    public void setLastNetheriteConsumption(long lastNetheriteConsumption) { this.lastNetheriteConsumption = lastNetheriteConsumption; }
    
    public boolean isAtWar() { return atWar; }
    public void setAtWar(boolean atWar) { this.atWar = atWar; }
    
    public String getWarTarget() { return warTarget; }
    public void setWarTarget(String warTarget) { this.warTarget = warTarget; }
    
    public Map<UUID, Set<UUID>> getWarKills() { return warKills; }
    public void setWarKills(Map<UUID, Set<UUID>> warKills) { this.warKills = warKills; }
    
    public int calculatePower() {
        double killMultiplier = (atWar && warTarget != null) ? 1.0 : 0.5; // 2x during war
        double deathPenalty = (atWar && warTarget != null) ? 1.0 : 0.5;   // 2x penalty during war
        
        return (netheriteVault * 5) + 
               (int)(totalKills * killMultiplier) - 
               (int)(totalDeaths * deathPenalty);
    }
    
    public void addKill(UUID killer, UUID victim) {
        totalKills++;
        if (atWar && warTarget != null) {
            warKills.computeIfAbsent(killer, k -> new HashSet<>()).add(victim);
        }
    }
    
    public void addDeath() {
        totalDeaths++;
        // War deaths are handled by power calculation multiplier, not double-counting
    }
    
    public boolean hasKilledAllEnemies(Set<UUID> enemyMembers) {
        Set<UUID> allKilled = new HashSet<>();
        for (Set<UUID> killed : warKills.values()) {
            allKilled.addAll(killed);
        }
        return allKilled.containsAll(enemyMembers);
    }
    
    public void resetWar() {
        atWar = false;
        warTarget = null;
        warKills.clear();
    }
    
    // Add methods to store/retrieve names
    public void storeMemberName(UUID playerId, String name) {
        memberNames.put(playerId, name);
    }
    
    public String getMemberName(UUID playerId) {
        return memberNames.getOrDefault(playerId, "Unknown");
    }
    
    public Map<UUID, String> getMemberNames() {
        return new HashMap<>(memberNames);
    }
    
    public int getKills() {
        return totalKills;
    }
    
    public int getDeaths() {
        return totalDeaths;
    }

    public void startWar(String enemyClanName) {
        this.atWar = true;
        this.warTarget = enemyClanName;
        // Clear war kills for fresh start
        this.warKills.clear();
    }
}
