package com.clans.systems;

import com.clans.Simpleclans;
import com.clans.data.ClanDataManager;
import com.clans.model.Clan;
import com.clans.util.ClanUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ClanTimerSystem {
    private ScheduledExecutorService scheduler;
    private MinecraftServer server;
    
    public void initialize(MinecraftServer server) {
        this.server = server;
        this.scheduler = Executors.newScheduledThreadPool(2);
        
        // Schedule netherite consumption check every hour
        scheduler.scheduleAtFixedRate(this::checkNetheriteConsumption, 1, 1, TimeUnit.HOURS);
        
        // Schedule cleanup tasks every 5 minutes
        scheduler.scheduleAtFixedRate(this::cleanupTasks, 5, 5, TimeUnit.MINUTES);
        
        Simpleclans.LOGGER.info("Clan timer system initialized");
    }
    
    public void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
        }
    }
    
    private void checkNetheriteConsumption() {
        try {
            ClanDataManager dataManager = Simpleclans.getDataManager();
            long currentTime = System.currentTimeMillis();
            long consumptionInterval = Simpleclans.getConfig().netheriteConsumptionHours * 60 * 60 * 1000;
            
            for (Clan clan : dataManager.getAllClans()) {
                long timeSinceConsumption = currentTime - clan.getLastNetheriteConsumption();
                
                if (timeSinceConsumption >= consumptionInterval) {
                    // Time to consume netherite
                    if (clan.getNetheriteVault() > 0) {
                        clan.setNetheriteVault(clan.getNetheriteVault() - 1);
                        clan.setLastNetheriteConsumption(currentTime);
                        
                        // Warn if vault is getting low
                        if (clan.getNetheriteVault() <= 3) {
                            Text warning = Text.literal("Warning: Clan vault is running low! " + 
                                clan.getNetheriteVault() + " netherite remaining.")
                                .formatted(Formatting.YELLOW);
                            ClanUtils.broadcastToClan(clan, warning, server);
                        }
                    } else if (clan.getNetheriteVault() == 0) {
                        // Start grace period
                        long gracePeriodEnd = clan.getLastNetheriteConsumption() + (2 * consumptionInterval);
                        
                        if (currentTime < gracePeriodEnd) {
                            // Still in grace period - warn players
                            long hoursLeft = (gracePeriodEnd - currentTime) / (60 * 60 * 1000);
                            Text warning = Text.literal("Clan needs netherite in vault, " + hoursLeft + " hours left")
                                .formatted(Formatting.RED);
                            
                            ClanUtils.broadcastToClan(clan, warning, server);
                            
                            // Mark players for notification when they log in
                            for (java.util.UUID memberUuid : clan.getMembers()) {
                                dataManager.addPlayerToNotify(memberUuid);
                            }
                        } else {
                            // Grace period over - disband clan
                            Text disbandMessage = Text.literal("Clan vault had insufficient funds, disbanded the clan.")
                                .formatted(Formatting.RED);
                            
                            ClanUtils.broadcastToClan(clan, disbandMessage, server);
                            
                            // Mark offline players for notification
                            for (java.util.UUID memberUuid : clan.getMembers()) {
                                dataManager.addPlayerToNotify(memberUuid);
                            }
                            
                            dataManager.disbandClan(clan.getName());
                            Simpleclans.LOGGER.info("Disbanded clan {} due to insufficient netherite", clan.getName());
                        }
                    }
                }
            }
            
            dataManager.save();
        } catch (Exception e) {
            Simpleclans.LOGGER.error("Error in netherite consumption check", e);
        }
    }
    
    private void cleanupTasks() {
        try {
            // Clean up expired confirmations
            ClanUtils.cleanupConfirmations();
            
            // Clean up expired invitations is handled in the data manager
        } catch (Exception e) {
            Simpleclans.LOGGER.error("Error in cleanup tasks", e);
        }
    }
}



