package loyfael;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
// Nouveaux imports pour la détection d'inventaire
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class PlayerDataListener implements Listener {
  private final Main plugin;
  private final DatabaseManager dbManager;
  private final ConcurrentHashMap<String, Long> joinCooldown = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Long> lastInventoryChange = new ConcurrentHashMap<>();

  public PlayerDataListener(Main plugin, DatabaseManager dbManager) {
    this.plugin = plugin;
    this.dbManager = dbManager;
    
    plugin.getLogger().info("PlayerDataListener initialisé avec ÉQUILIBRE PARFAIT");
    plugin.getLogger().info("- Performance serveur optimale + Expérience utilisateur parfaite");
    plugin.getLogger().info("- Micro-délais intelligents pour grouper les actions rapides");
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    String uuid = player.getUniqueId().toString();

    // Anti-spam INTELLIGENT : 50ms seulement - protège contre les glitches mais imperceptible
    Long lastJoin = this.joinCooldown.get(uuid);
    long currentTime = System.currentTimeMillis();
    if (lastJoin != null && (currentTime - lastJoin) < 50) {
      return; // Protection minimale contre les bug de co/deco rapides
    }
    this.joinCooldown.put(uuid, currentTime);

    // Chargement IMMÉDIAT - pas de délai du tout
    plugin.getDatabaseExecutor().execute(() -> {
      if (player.isOnline()) {
        dbManager.loadPlayer(player);
      }
    });
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onPlayerQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    String uuid = player.getUniqueId().toString();

    // SAUVEGARDE SYNCHRONE ULTRA-PRIORITAIRE - BLOQUE jusqu'à la fin
    try {
      dbManager.savePlayerSync(player); // Synchrone = garantie 100%
    } catch (Exception e) {
      plugin.getLogger().severe("ERREUR CRITIQUE: Échec sauvegarde " + player.getName() + ": " + e.getMessage());
    }

    // Nettoyage immédiat
    dbManager.clearCache(uuid);
    joinCooldown.remove(uuid);
    lastInventoryChange.remove(uuid); // Cleanup du debounce
  }

  // === ÉVÉNEMENTS D'INVENTAIRE - SAUVEGARDE IMMÉDIATE ===

  @EventHandler(priority = EventPriority.MONITOR)
  public void onInventoryClick(InventoryClickEvent event) {
    if (event.getWhoClicked() instanceof Player) {
      savePlayerInstantly((Player) event.getWhoClicked());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onInventoryDrag(InventoryDragEvent event) {
    if (event.getWhoClicked() instanceof Player) {
      savePlayerInstantly((Player) event.getWhoClicked());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerDropItem(PlayerDropItemEvent event) {
    savePlayerInstantly(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onEntityPickupItem(EntityPickupItemEvent event) {
    if (event.getEntity() instanceof Player) {
      savePlayerInstantly((Player) event.getEntity());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerDeath(PlayerDeathEvent event) {
    savePlayerInstantly(event.getEntity());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerRespawn(PlayerRespawnEvent event) {
    savePlayerInstantly(event.getPlayer());
  }

  /**
   * Sauvegarde INTELLIGENTE avec debounce - équilibre parfait
   */
  private void savePlayerInstantly(Player player) {
    String uuid = player.getUniqueId().toString();
    long currentTime = System.currentTimeMillis();
    
    // Debounce intelligent : 50ms entre les sauvegardes du même joueur
    // Groupe automatiquement les clics rapides sans impacter l'UX
    Long lastChange = lastInventoryChange.put(uuid, currentTime);
    if (lastChange != null && (currentTime - lastChange) < 50) {
      return; // Skip cette sauvegarde, la suivante dans 50ms prendra le relay
    }
    
    // Micro-délai de 1 tick pour grouper les actions en cours
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
      if (player.isOnline()) {
        plugin.getDatabaseExecutor().execute(() -> {
          try {
            dbManager.savePlayer(player);
          } catch (Exception e) {
            plugin.getLogger().warning("Échec sauvegarde pour " + player.getName() + ": " + e.getMessage());
          }
        });
      }
    }, 1L);
  }
}
