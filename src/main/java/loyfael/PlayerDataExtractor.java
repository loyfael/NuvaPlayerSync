package loyfael;

import org.bukkit.entity.Player;

/**
 * Responsable de l'extraction des données d'un joueur
 * Optimisé pour : XP, Enderchest, Inventory, Health, Hunger
 */
@SuppressWarnings("unused") // Utilisé par DatabaseManager
public class PlayerDataExtractor {
    private final Main plugin;

    public PlayerDataExtractor(Main plugin) {
        this.plugin = plugin;
    }

    /**
     * Extrait les données configurées d'un joueur (sans coordonnées/gamemode)
     */
    public PlayerDataCache extract(Player player) {
        PlayerDataCache data = new PlayerDataCache(player.getUniqueId().toString());

        // Données du joueur (seulement ce qui est synchronisé)
        if (plugin.isSyncXp()) {
            data.xp = player.getTotalExperience();      // XP total
            data.expLevel = player.getLevel();          // Niveau actuel
            data.expProgress = player.getExp();         // Progression vers niveau suivant (0.0-1.0)
        } else {
            data.xp = 0;
            data.expLevel = 0;
            data.expProgress = 0.0f;
        }
        data.health = plugin.isSyncHealth() ? player.getHealth() : 20.0;
        data.hunger = plugin.isSyncHunger() ? player.getFoodLevel() : 20;
        data.saturation = plugin.isSyncHunger() ? player.getSaturation() : 5.0f;

        // Inventaires (traitement asynchrone)
        extractInventoryData(player, data);

        return data;
    }

    private void extractInventoryData(Player player, PlayerDataCache data) {
        if (plugin.isSyncEnderchest() || plugin.isSyncInventory()) {
            try {
                data.enderchest = plugin.isSyncEnderchest() ?
                    InventoryUtils.itemStackArrayToBase64(player.getEnderChest().getContents()) : null;
                data.inventory = plugin.isSyncInventory() ?
                    InventoryUtils.itemStackArrayToBase64(player.getInventory().getContents()) : null;
            } catch (Exception e) {
                plugin.getLogger().warning("Erreur sérialisation inventaire " + player.getName() + ": " + e.getMessage());
                data.enderchest = null;
                data.inventory = null;
            }
        }
    }
}
