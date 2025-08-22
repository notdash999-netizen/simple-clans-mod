package com.clans.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.clans.Simpleclans;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ClanConfig {
    private static final String CONFIG_FILE = "config/simpleclans.json";
    
    // Economic Settings - Keep existing values as requested
    public int clanCreationCost = 64;
    public int invitationCost = 16;  // Fixed: Should be 16 as you originally specified
    public int joinCost = 16;        // Fixed: Should be 16 as you originally specified
    public int enemyCost = 32;
    public int allyCost = 32;        // FIXED: Should be 32, not 8
    public int neutralCost = 32;
    public int warDeclarationCost = 48;
    
    // Combat & Proximity Settings - Only add essential missing fields
    public double proximitySpeedBonus = 0.10;     // 10% per member
    public double proximityDamageBonus = 0.10;    // 10% per member  
    public double enemyDamageBonus = 0.10;        // 10% vs enemies
    public double proximityRadius = 20.0;         // ESSENTIAL: 20-block proximity range for buffs
    
    // Power Calculation
    public double netheriteWeight = 5.0;
    public double killWeight = 0.5;
    public double deathWeight = 0.5;
    
    // Timer Settings - Only add essential missing fields
    public int invitationExpirationMinutes = 5;
    public int netheriteConsumptionHours = 24;
    public int proximityCheckSeconds = 2;
    public int netheriteVaultHours = 12;
    public int warDurationHours = 24;               // ESSENTIAL: 24-hour war duration
    public int allianceRequestExpirationMinutes = 5; // ESSENTIAL: 5-minute ally request timeout
    
    // War Rewards
    public int warWinnerNetherite = 1;
    public int warWinnerGold = 32;
    public int warBuffDurationHours = 2;
    
    // Vault Limits - Only add essential limits
    public int maxClanSize = 4;                     // ESSENTIAL: Referenced in existing code
    public int maxVaultSize = 10;                   // ESSENTIAL: Vault size limit
    public int dailyVaultOperations = 3;            // ESSENTIAL: Daily deposit/withdraw limit
    
    public ClanConfig() {
        // Constructor does not call load()
    }
    
    public static ClanConfig loadOrCreate() {
        ClanConfig config = new ClanConfig();
        config.load();
        return config;
    }
    
    public void load() {
        try {
            Path configPath = Paths.get(CONFIG_FILE);
            if (Files.exists(configPath)) {
                Gson gson = new Gson();
                try (FileReader reader = new FileReader(configPath.toFile())) {
                    ClanConfig loaded = gson.fromJson(reader, ClanConfig.class);
                    if (loaded != null) {
                        copyFrom(loaded);
                    }
                }
                Simpleclans.LOGGER.info("Loaded clan configuration from " + CONFIG_FILE);
            } else {
                save();
                Simpleclans.LOGGER.info("Created default clan configuration at " + CONFIG_FILE);
            }
        } catch (IOException e) {
            Simpleclans.LOGGER.error("Failed to load clan configuration", e);
        }
    }
    
    public void save() {
        try {
            Path configDir = Paths.get("config");
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }
            
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                gson.toJson(this, writer);
            }
            Simpleclans.LOGGER.info("Saved clan configuration to " + CONFIG_FILE);
        } catch (IOException e) {
            Simpleclans.LOGGER.error("Failed to save clan configuration", e);
        }
    }
    
    private void copyFrom(ClanConfig other) {
        this.clanCreationCost = other.clanCreationCost;
        this.invitationCost = other.invitationCost;
        this.joinCost = other.joinCost;
        this.allyCost = other.allyCost;
        this.neutralCost = other.neutralCost;
        this.warDeclarationCost = other.warDeclarationCost;
        this.proximitySpeedBonus = other.proximitySpeedBonus;
        this.proximityDamageBonus = other.proximityDamageBonus;
        this.enemyDamageBonus = other.enemyDamageBonus;
        this.proximityRadius = other.proximityRadius;
        this.netheriteWeight = other.netheriteWeight;
        this.killWeight = other.killWeight;
        this.deathWeight = other.deathWeight;
        this.invitationExpirationMinutes = other.invitationExpirationMinutes;
        this.netheriteConsumptionHours = other.netheriteConsumptionHours;
        this.proximityCheckSeconds = other.proximityCheckSeconds;
        this.netheriteVaultHours = other.netheriteVaultHours;
        this.warDurationHours = other.warDurationHours;
        this.allianceRequestExpirationMinutes = other.allianceRequestExpirationMinutes;
        this.warWinnerNetherite = other.warWinnerNetherite;
        this.warWinnerGold = other.warWinnerGold;
        this.warBuffDurationHours = other.warBuffDurationHours;
        this.maxClanSize = other.maxClanSize;
        this.maxVaultSize = other.maxVaultSize;
        this.dailyVaultOperations = other.dailyVaultOperations;
    }

    public static void reloadConfig() {
        if (Simpleclans.getConfig() != null) {
            Simpleclans.getConfig().load();
            Simpleclans.LOGGER.info("🔄 Configuration reloaded at runtime!");
        }
    }

    public void forceReload() {
        load();
        Simpleclans.LOGGER.info("⚡ Force reloaded clan configuration values:");
        Simpleclans.LOGGER.info("  - Alliance Cost: {} gold", allyCost);
        Simpleclans.LOGGER.info("  - Enemy Damage Bonus: {}%", enemyDamageBonus * 100);
        Simpleclans.LOGGER.info("  - Proximity Damage Bonus: {}%", proximityDamageBonus * 100);
    }
}

