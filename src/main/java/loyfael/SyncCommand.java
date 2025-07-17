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
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!hasPerm(sender, "admin")) {
            sender.sendMessage(plugin.getMessageManager().get("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§6=== PlayerSync Commands ===");
            sender.sendMessage("§e/sync <option> [on/off] §7- Toggle sync options");
            sender.sendMessage("§e/sync reload §7- Reload configuration");
            sender.sendMessage("§e/sync stats §7- Show performance statistics");
            sender.sendMessage("§e/sync cache §7- Cache management");
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

        // Statistiques du cache et de la base de données
        sender.sendMessage("§6--- Performance Base de Données ---");
        sender.sendMessage("§e" + plugin.getDatabaseManager().getPerformanceStats());
        sender.sendMessage("§eCache joueurs: §f" + plugin.getDatabaseManager().getCacheSize() + " entrées");
        sender.sendMessage("§eCache inventaires: §f" + InventoryUtils.getCacheSize() + " entrées");

        // Calcul des TPS (approximatif)
        long currentTime = System.currentTimeMillis();
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

    private boolean hasPerm(CommandSender sender, String option) {
        return sender.hasPermission("playerdatasync.admin." + option);
    }

    private boolean getCurrentValue(String option) {
        switch (option) {
            case "xp": return plugin.isSyncXp();
            case "enderchest": return plugin.isSyncEnderchest();
            case "inventory": return plugin.isSyncInventory();
            case "health": return plugin.isSyncHealth();
            case "hunger": return plugin.isSyncHunger();
            default: return false;
        }
    }
}
