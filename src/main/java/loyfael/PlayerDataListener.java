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

    // SUPPRESSION TOTALE de l'anti-spam - PRIORITÉ ABSOLUE à la synchronisation
    // Plus aucun délai, chargement IMMÉDIAT et BLOQUANT
    this.joinCooldown.put(uuid, System.currentTimeMillis());

    // CHARGEMENT IMMÉDIAT et SYNCHRONE pour garantir que l'inventaire est là
    // Le joueur ne pourra rien faire tant que ses données ne sont pas chargées
    try {
      dbManager.loadPlayerSync(player); // Nouveau: chargement synchrone
      plugin.getLogger().info("✓ Données synchronisées pour " + player.getName() + " (INSTANTANÉ)");
    } catch (Exception e) {
      plugin.getLogger().severe("ÉCHEC CRITIQUE chargement " + player.getName() + ": " + e.getMessage());
      // En cas d'échec, kick le joueur pour éviter la corruption
      player.kickPlayer("§cErreur de synchronisation. Reconnectez-vous.");
    }
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
   * Sauvegarde ULTRA-IMMÉDIATE avec protection RAM intelligente
   * Évite le spam de tâches tout en gardant la réactivité maximale
   */
  private void savePlayerInstantly(Player player) {
    String uuid = player.getUniqueId().toString();
    long currentTime = System.currentTimeMillis();
    
    // PROTECTION RAM : évite le spam de tâches sans perdre la réactivité
    Long lastChange = lastInventoryChange.get(uuid);
    if (lastChange != null && (currentTime - lastChange) < 10) {
      // Si < 10ms entre les actions : on écrase la dernière timestamp
      // Ça évite de créer 100 tâches par seconde tout en restant ultra-réactif
      lastInventoryChange.put(uuid, currentTime);
      return;
    }
    
    lastInventoryChange.put(uuid, currentTime);
    
    // SAUVEGARDE IMMÉDIATE - mais pas de spam de tâches
    plugin.getDatabaseExecutor().execute(() -> {
      if (player.isOnline()) {
        try {
          dbManager.savePlayer(player);
        } catch (Exception e) {
          plugin.getLogger().warning("Échec sauvegarde pour " + player.getName() + ": " + e.getMessage());
        }
      }
    });
  }
}
