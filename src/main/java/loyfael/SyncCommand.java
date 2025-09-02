package loyfael;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadPoolExecutor;

public class SyncCommand implements CommandExecutor {
    private final Main plugin;

    public SyncCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!hasPerm(sender)) {
            sender.sendMessage(plugin.getMessageManager().get("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§6=== PlayerSync Commands ===");
            sender.sendMessage("§e/sync <option> [on/off] §7- Toggle sync options");
            sender.sendMessage("§e/sync reload §7- Reload configuration");
            sender.sendMessage("§e/sync stats §7- Show performance statistics");
            sender.sendMessage("§e/sync cache §7- Cache management");
            sender.sendMessage("§e/sync reset <player> §7- Reset player data (troubleshooting)");
            return true;
        }

        String option = args[0].toLowerCase();

        if (option.equals("stats")) {
            showPerformanceStats(sender);
            return true;
        }

        if (option.equals("cache")) {
            if (args.length == 2 && args[1].equalsIgnoreCase("clear")) {
                InventoryUtils.clearCache();
                sender.sendMessage("§aCache d'inventaires vidé (" + InventoryUtils.getCacheSize() + " entrées)");
                return true;
            }
            sender.sendMessage("§6=== Cache Statistics ===");
            sender.sendMessage("§eInventory cache size: §f" + InventoryUtils.getCacheSize());
            sender.sendMessage("§eUse §6/sync cache clear §eto clear inventory cache");
            return true;
        }

        if (args.length == 1) {
            if (option.equals("reload")) {
                plugin.reloadPlugin();
                sender.sendMessage(plugin.getMessageManager().get("reloaded"));
                return true;
            }

            boolean current = getCurrentValue(option);
            sender.sendMessage("§e" + option + " §7is currently §" + (current ? "a" : "c") + (current ? "enabled" : "disabled"));
            return true;
        }

        if (args.length == 2) {
            // Handle reset command
            if (option.equals("reset")) {
                handleResetCommand(sender, args[1]);
                return true;
            }

            String value = args[1].toLowerCase();
            if (!value.equals("on") && !value.equals("off")) {
                sender.sendMessage("§cUse 'on' or 'off'");
                return true;
            }

            boolean enable = value.equals("on");
            switch (option) {
                case "xp":
                    plugin.setSyncXp(enable);
                    break;
                case "enderchest":
                    plugin.setSyncEnderchest(enable);
                    break;
                case "inventory":
                    plugin.setSyncInventory(enable);
                    break;
                case "health":
                    plugin.setSyncHealth(enable);
                    break;
                case "hunger":
                    plugin.setSyncHunger(enable);
                    break;
                default:
                    sender.sendMessage("§cUnknown option: " + option);
                    return true;
            }

            sender.sendMessage("§e" + option + " §7is now §" + (enable ? "a" : "c") + (enable ? "enabled" : "disabled"));
            return true;
        }

        return false;
    }

    private void showPerformanceStats(CommandSender sender) {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        long totalMemory = runtime.totalMemory() / 1024 / 1024;
        long freeMemory = runtime.freeMemory() / 1024 / 1024;
        long usedMemory = totalMemory - freeMemory;

        sender.sendMessage("§6=== STATISTIQUES HAUTE PERFORMANCE ===");
        sender.sendMessage("§eJoueurs en ligne: §f" + plugin.getServer().getOnlinePlayers().size());
        sender.sendMessage("§eUtilisation mémoire: §f" + usedMemory + "MB§7/§f" + maxMemory + "MB §7(" + (usedMemory * 100 / maxMemory) + "%)");

        // Statistiques des pools de threads
        sender.sendMessage("§6--- Pools de Threads ---");
        if (plugin.getDatabaseExecutor() instanceof ThreadPoolExecutor dbPool) {
            sender.sendMessage("§eBase de données: §f" + dbPool.getActiveCount() + "/" + dbPool.getMaximumPoolSize() +
                    " actifs, §f" + dbPool.getQueue().size() + " en attente");
        }
        if (plugin.getInventoryExecutor() instanceof ThreadPoolExecutor invPool) {
            sender.sendMessage("§eInventaires: §f" + invPool.getActiveCount() + "/" + invPool.getMaximumPoolSize() +
                    " actifs, §f" + invPool.getQueue().size() + " en attente");
        }

        // Statistiques HikariCP
        sender.sendMessage("§6--- Pool de Connexions ---");
        sender.sendMessage("§e" + plugin.getConnectionPoolStats());

        // Statistiques MongoDB et base de données
        sender.sendMessage("§6--- MongoDB & Database ---");
        sender.sendMessage("§e" + plugin.getDatabaseManager().getPerformanceStats());
        sender.sendMessage("§e" + plugin.getDatabaseManager().getConnectionPoolStats());
        sender.sendMessage("§eCache size: §f" + plugin.getDatabaseManager().getCacheSize() + " entries");

        // === INFORMATIONS MODE LATENCE MINIMALE ===
        sender.sendMessage("§6--- Mode Latence Minimale ---");
        sender.sendMessage("§aToute modification d'inventaire = sauvegarde instantanée");
        sender.sendMessage("§aAucun délai, aucun cooldown, aucune détection");
        sender.sendMessage("§aOptimisé pour changements de serveur ultra-rapides");

        // Statistiques détaillées MongoDB
        if (plugin.getConfig().getBoolean("debug.show-detailed-stats", false)) {
            var mongoManager = getMongoManagerFromPlugin();
            if (mongoManager != null) {
                sender.sendMessage("§6--- MongoDB Detailed Stats ---");
                sender.sendMessage("§e" + mongoManager.getPerformanceStats());
                sender.sendMessage("§eActive connections: §f" + mongoManager.getActiveConnections());
                sender.sendMessage("§eIdle connections: §f" + mongoManager.getIdleConnections());
            }
        }

        // Recommandations basées sur le nombre de joueurs
        sender.sendMessage("§6--- Recommandations ---");
        int playerCount = plugin.getServer().getOnlinePlayers().size();
        if (playerCount > 200) {
            sender.sendMessage("§c⚠ Plus de 200 joueurs - Surveillez les performances");
        } else if (playerCount > 100) {
            sender.sendMessage("§e⚠ Plus de 100 joueurs - Performances optimales");
        } else {
            sender.sendMessage("§a✓ Charge normale - Excellentes performances");
        }
    }

    /**
     * Get MongoDB manager from plugin for detailed statistics
     */
    private MongoConnectionManager getMongoManagerFromPlugin() {
        try {
            // Accès via réflection ou méthode publique si disponible
            return plugin.getMongoManager();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean hasPerm(CommandSender sender) {
        return sender.hasPermission("playerdatasync.admin");
    }

    private boolean getCurrentValue(String option) {
        return switch (option) {
            case "xp" -> plugin.isSyncXp();
            case "enderchest" -> plugin.isSyncEnderchest();
            case "inventory" -> plugin.isSyncInventory();
            case "health" -> plugin.isSyncHealth();
            case "hunger" -> plugin.isSyncHunger();
            default -> false;
        };
    }

    private void handleResetCommand(CommandSender sender, String playerName) {
        sender.sendMessage("§eRecherche du joueur §f" + playerName + "§e...");

        // Try to get player UUID from online players first
        var onlinePlayer = plugin.getServer().getPlayer(playerName);
        String uuid = null;
        String finalPlayerName = playerName;

        if (onlinePlayer != null) {
            // Player is online
            uuid = onlinePlayer.getUniqueId().toString();
            finalPlayerName = onlinePlayer.getName();
            sender.sendMessage("§aJoueur trouvé en ligne: §f" + finalPlayerName);
        } else {
            // Check if it's already a UUID format
            if (playerName.length() == 36 && playerName.contains("-")) {
                // It's already a UUID
                uuid = playerName;
                finalPlayerName = "UUID: " + playerName;
                sender.sendMessage("§eUUID fourni directement: §f" + playerName);
            } else {
                // Try to get UUID from offline player
                try {
                    var offlinePlayer = plugin.getServer().getOfflinePlayer(playerName);
                    if (offlinePlayer.hasPlayedBefore()) {
                        uuid = offlinePlayer.getUniqueId().toString();
                        finalPlayerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : playerName;
                        sender.sendMessage("§eJoueur hors ligne trouvé: §f" + finalPlayerName);
                    } else {
                        sender.sendMessage("§cJoueur §f" + playerName + " §cn'a jamais joué sur ce serveur.");
                        sender.sendMessage("§eVous pouvez aussi utiliser l'UUID directement: §f/sync reset <UUID>");
                        return;
                    }
                } catch (Exception e) {
                    sender.sendMessage("§cErreur lors de la recherche du joueur §f" + playerName);
                    sender.sendMessage("§eUtilisez l'UUID du joueur: §f/sync reset <UUID>");
                    return;
                }
            }
        }

        final String finalUuid = uuid;
        final String displayName = finalPlayerName;

        sender.sendMessage("§eVérification de l'existence des données pour §f" + displayName + "§e...");

        // Check if player data exists first
        plugin.getDatabaseManager().playerDataExists(finalUuid).thenAccept(exists -> {
            if (!exists) {
                sender.sendMessage("§cAucune donnée trouvée pour le joueur §f" + displayName);
                return;
            }

            sender.sendMessage("§eRéinitialisation des données du joueur §f" + displayName + "§e...");

            // Reset player data
            plugin.getDatabaseManager().resetPlayerData(finalUuid).thenAccept(success -> {
                if (success) {
                    sender.sendMessage("§a✓ Données du joueur §f" + displayName + " §aréinitialisées avec succès !");

                    // Clear cache for this player
                    plugin.getDatabaseManager().clearCache(finalUuid);

                    // If player is online, notify them
                    if (onlinePlayer != null && onlinePlayer.isOnline()) {
                        onlinePlayer.sendMessage("§c⚠ Vos données ont été réinitialisées par un administrateur.");
                        onlinePlayer.sendMessage("§eDéconnectez-vous et reconnectez-vous pour appliquer les changements.");
                    }

                    // Log the action
                    plugin.getLogger().info("Player data reset by " + sender.getName() + " for: " + displayName + " (UUID: " + finalUuid + ")");
                } else {
                    sender.sendMessage("§c✗ Échec de la réinitialisation des données pour §f" + displayName);
                    sender.sendMessage("§cVérifiez les logs du serveur pour plus de détails.");
                }
            }).exceptionally(throwable -> {
                sender.sendMessage("§c✗ Erreur lors de la réinitialisation: " + throwable.getMessage());
                plugin.getLogger().severe("Reset command error: " + throwable.getMessage());
                return null;
            });
        }).exceptionally(throwable -> {
            sender.sendMessage("§c✗ Erreur lors de la vérification des données: " + throwable.getMessage());
            plugin.getLogger().severe("Reset command check error: " + throwable.getMessage());
            return null;
        });
    }
}
