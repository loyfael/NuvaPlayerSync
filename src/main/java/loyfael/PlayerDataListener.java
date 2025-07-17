package loyfael;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataListener implements Listener {
  private final Main plugin;
  private final DatabaseManager dbManager;
  private final ConcurrentHashMap<String, Long> joinCooldown = new ConcurrentHashMap<>();

  public PlayerDataListener(Main plugin, DatabaseManager dbManager) {
    this.plugin = plugin;
    this.dbManager = dbManager;
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    String uuid = player.getUniqueId().toString();

    final long joinCooldown = 1000; // Variable locale au lieu de champ

    // Anti-spam pour les rejoins rapides
    Long lastJoin = this.joinCooldown.get(uuid);
    long currentTime = System.currentTimeMillis();
    if (lastJoin != null && (currentTime - lastJoin) < joinCooldown) {
      return;
    }
    this.joinCooldown.put(uuid, currentTime);

    // Chargement optimisé avec délai pour éviter les conflits de spawn
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
      if (player.isOnline()) {
        plugin.getDatabaseExecutor().execute(() -> dbManager.loadPlayer(player));
      }
    }, 10L); // 0.5 seconde de délai
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onPlayerQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    String uuid = player.getUniqueId().toString();

    // Sauvegarde immédiate et nettoyage du cache
    plugin.getDatabaseExecutor().execute(() -> {
      dbManager.savePlayer(player);
      // Nettoyer le cache après 30 secondes pour éviter les fuites mémoire
      Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
        dbManager.clearCache(uuid);
        joinCooldown.remove(uuid);
      }, 600L); // 30 secondes
    });
  }
}
