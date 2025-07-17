package loyfael;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Responsable de l'application des données cachées sur un joueur
 * Optimisé pour : XP, Enderchest, Inventory, Health, Hunger
 */
public class PlayerDataApplier {
    private final Main plugin;

    public PlayerDataApplier(Main plugin) {
        this.plugin = plugin;
    }

    /**
     * Applique les données cachées sur le joueur (sans coordonnées/gamemode)
     */
    public void apply(Player player, PlayerDataCache data) {
        // Application sur le thread principal Bukkit
        Bukkit.getScheduler().runTask(plugin, () -> {
            applyPlayerStats(player, data);
            applyInventories(player, data);
        });
    }

    private void applyPlayerStats(Player player, PlayerDataCache data) {
        // XP
        if (plugin.isSyncXp() && data.xp > 0) {
            player.setTotalExperience(data.xp);
        }

        // Santé
        if (plugin.isSyncHealth()) {
            applyHealth(player, data.health);
        }

        // Faim
        if (plugin.isSyncHunger()) {
            player.setFoodLevel(data.hunger);
            player.setSaturation(data.saturation);
        }
    }

    private void applyHealth(Player player, double health) {
        try {
            // Approche compatible toutes versions Bukkit
            @SuppressWarnings("deprecation") // getMaxHealth est déprécié mais nécessaire pour compatibilité
            double maxHealth = player.getMaxHealth();
            player.setHealth(Math.min(health, maxHealth));
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur application santé pour " + player.getName() + ": " + e.getMessage());
            // Fallback sécurisé
            player.setHealth(Math.min(health, 20.0));
        }
    }

    private void applyInventories(Player player, PlayerDataCache data) {
        // Chargement d'inventaire sur le pool séparé pour éviter de bloquer les autres opérations
        if (plugin.isSyncInventory() || plugin.isSyncEnderchest()) {
            plugin.getInventoryExecutor().execute(() -> loadInventoriesAsync(player, data));
        }
    }

    private void loadInventoriesAsync(Player player, PlayerDataCache data) {
        try {
            // Traitement parallèle des deux inventaires pour gagner du temps
            ItemStack[] enderItems = null;
            ItemStack[] invItems = null;

            if (plugin.isSyncEnderchest() && data.enderchest != null) {
                enderItems = InventoryUtils.itemStackArrayFromBase64(data.enderchest);
            }

            if (plugin.isSyncInventory() && data.inventory != null) {
                invItems = InventoryUtils.itemStackArrayFromBase64(data.inventory);
            }

            // Application sur le thread principal en une seule fois
            final ItemStack[] finalEnderItems = enderItems;
            final ItemStack[] finalInvItems = invItems;

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (finalEnderItems != null) {
                    player.getEnderChest().setContents(finalEnderItems);
                }
                if (finalInvItems != null) {
                    player.getInventory().setContents(finalInvItems);
                }
            });

        } catch (Exception e) {
            plugin.getLogger().warning("Erreur chargement inventaire " + player.getName() + ": " + e.getMessage());
        }
    }
}
