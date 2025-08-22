package com.clans.model;

import java.util.UUID;

public class ClanInvitation {
    private final String clanName;
    private final UUID invitedPlayer;
    private final long expirationTime;
    
    public ClanInvitation(String clanName, UUID invitedPlayer) {
        this.clanName = clanName;
        this.invitedPlayer = invitedPlayer;
        this.expirationTime = System.currentTimeMillis() + (5 * 60 * 1000); // 5 minutes
    }
    
    public String getClanName() { return clanName; }
    public UUID getInvitedPlayer() { return invitedPlayer; }
    public long getExpirationTime() { return expirationTime; }
    
    public boolean isExpired() {
        return System.currentTimeMillis() > expirationTime;
    }
}
