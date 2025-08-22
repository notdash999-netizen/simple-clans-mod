package com.clans.model;

public enum ClanRole {
    KING,
    ADVISOR,
    PEASANT;
    
    public boolean canInvite() {
        return this == KING || this == ADVISOR;
    }
    
    public boolean canDisband() {
        return this == KING;
    }
    
    public boolean canKick() {
        return this == KING;
    }
    
    public boolean canAlly() {
        return this == KING;
    }
    
    public boolean canDeclareEnemy() {
        return this == KING;
    }
    
    public boolean canDeclareWar() {
        return this == KING;
    }
    
    public boolean canAccessVault() {
        return this == KING || this == ADVISOR;
    }
    
    public boolean canRemoveFromVault() {
        return this == KING;
    }
    
    public boolean canTransfer() {
        return this == KING;
    }
    
    public boolean canPromote() {
        return this == KING;
    }
}
