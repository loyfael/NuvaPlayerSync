package loyfael;

import java.util.Objects;

/**
 * Player data cache class representing cached player information
 * Optimized for: XP, Enderchest, Inventory, Health, Hunger
 *
 * This class serves as a lightweight data container for player information
 * with built-in expiration tracking and equality comparison for cache optimization
 */
public class PlayerDataCache {
    public String uuid;          // Player UUID
    public String enderchest;    // Serialized enderchest contents
    public String inventory;     // Serialized inventory contents
    public double health;        // Player health value
    public float saturation;     // Player saturation level
    public int xp, hunger;      // Player XP and hunger levels
    public long lastUpdated;    // Timestamp for cache expiration

    /**
     * Create new player data cache entry
     * @param uuid Player UUID
     */
    public PlayerDataCache(String uuid) {
        this.uuid = uuid;
        this.lastUpdated = System.currentTimeMillis();
    }

    /**
     * Check equality with another cache object for optimization
     * Used to determine if data has changed and needs to be saved
     * @param obj Object to compare with
     * @return true if data is identical, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof PlayerDataCache other)) return false;

        return xp == other.xp &&
               Math.abs(health - other.health) < 0.1 &&
               hunger == other.hunger &&
               Math.abs(saturation - other.saturation) < 0.1 &&
               Objects.equals(enderchest, other.enderchest) &&
               Objects.equals(inventory, other.inventory);
    }

    /**
     * Quick equality check ULTRA-OPTIMISÉ - pixel perfect
     * Compare intelligemment selon l'importance pour l'expérience utilisateur
     */
    public boolean quickEquals(PlayerDataCache other) {
        if (other == null) return false;
        
        // PRIORITÉ 1: Inventaire (le plus visible pour l'utilisateur)
        if (!Objects.equals(inventory, other.inventory)) return false;
        
        // PRIORITÉ 2: XP (très visible dans l'interface)
        if (xp != other.xp) return false;
        
        // PRIORITÉ 3: Faim (modérément visible)
        if (hunger != other.hunger) return false;
        
        // PRIORITÉ 4: Vie (moins critique car moins souvent modifiée)
        if (Math.abs(health - other.health) >= 0.5) return false; // Tolérance plus large
        
        return true; // Enderchest et saturation skip pour performance
    }

    /**
     * Generate hash code for efficient HashMap operations
     * @return Hash code based on key fields
     */
    @Override
    public int hashCode() {
        return Objects.hash(uuid, xp, hunger, health);
    }

    /**
     * Check if cache entry has expired
     * @param maxAge Maximum age in milliseconds
     * @return true if expired, false if still valid
     */
    @SuppressWarnings("unused") // Used by DatabaseManager.loadPlayerAsync()
    public boolean isExpired(long maxAge) {
        return (System.currentTimeMillis() - lastUpdated) > maxAge;
    }
}
