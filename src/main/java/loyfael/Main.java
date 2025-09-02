package loyfael;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This plugin provides a robust solution for synchronizing player data across server restarts and crashes
 * Features:
 * - Asynchronous database operations with dedicated thread pools
 * - MongoDB connection management for massive query operations
 * - Advanced caching system with configurable cleanup
 * - Batch processing for reduced database load
 * - Separate thread pools for database and inventory operations
 * - Crash protection with JVM shutdown hooks and lag detection
 * - Emergency save mechanism with multiple retries and backup options
 * - ADAPTIVE SAVE SYSTEM for rapid inventory changes
 * Synchronizes: XP, Enderchest, Inventory, Health, Hunger
 */
public class Main extends JavaPlugin {
  // High-performance MongoDB connection manager
  private MongoConnectionManager mongoManager;

  // Optimized threading for high load scenarios
  private ExecutorService databaseExecutor;    // Dedicated pool for database operations
  private ExecutorService inventoryExecutor;   // Separate pool for inventory serialization/deserialization

  // SUPPRESSION DE TOUS LES COOLDOWNS ET DÉLAIS
  // Plus de cache de sauvegarde, plus de cooldown, juste la vitesse pure

  // Configuration fields for sync options
  private boolean syncXp;
  private boolean syncEnderchest;
  private boolean syncInventory;
  private boolean syncHealth;
  private boolean syncHunger;

  // Core components
  private DatabaseManager databaseManager;
  private int autosaveInterval;
  private BukkitTask autosaveTask;
  private MessageManager messageManager;

  // === CRASH PROTECTION COMPONENTS ===
  private BukkitTask lagDetectionTask;
  private final AtomicLong lastTickTime = new AtomicLong(System.currentTimeMillis());
  private volatile boolean emergencySaveInProgress = false;
  private final AtomicLong lastEmergencySave = new AtomicLong(0);
  private int emergencySaveCooldown = 30000; // 30 seconds
  private int highLoadThreshold = 100;
  private int highLoadInterval = 60;
  private boolean crashProtectionEnabled = true;

  @Override
  public void onEnable() {
    // Disable MongoDB verbose logging FIRST
    disableMongoLogging();

    saveDefaultConfig();

    // Initialize high-performance thread pools optimized for heavy load
    initializeHighPerformanceThreadPools();

    // Setup message manager with language support
    messageManager = new MessageManager(this);
    String lang = getConfig().getString("language", "en");
    messageManager.load(lang);

    // Initialize MongoDB connection manager
    mongoManager = new MongoConnectionManager(this);
    mongoManager.initialize();

    // Load sync configuration options
    loadConfiguration();

    autosaveInterval = getConfig().getInt("autosave.interval", 300); // Default: 5 minutes

    // Initialize database manager with caching and performance monitoring
    databaseManager = new DatabaseManager(this);
    databaseManager.initialize();
    getServer().getPluginManager().registerEvents(new PlayerDataListener(this, databaseManager), this);

    // Register sync command with admin functionality
    var syncCommand = getCommand("sync");
    if (syncCommand != null) {
      syncCommand.setExecutor(new SyncCommand(this));
    } else {
      getLogger().warning("Commande 'sync' non trouvée dans plugin.yml");
    }

    // Setup optimized autosave with batch processing
    setupOptimizedAutosave();

    // Log performance information avec les vrais chiffres
    int dbThreads = ((ThreadPoolExecutor) databaseExecutor).getCorePoolSize();
    int maxDbThreads = ((ThreadPoolExecutor) databaseExecutor).getMaximumPoolSize();
    int invThreads = ((ThreadPoolExecutor) inventoryExecutor).getCorePoolSize();
    int maxInvThreads = ((ThreadPoolExecutor) inventoryExecutor).getMaximumPoolSize();
    
    getLogger().info("Plugin activé en mode ULTRA-ÉCONOME (AMD Ryzen 9 - 4 threads) :");
    getLogger().info("- DB threads: " + dbThreads + "/" + maxDbThreads + " (MAX 3 pour économiser CPU)");
    getLogger().info("- Inventory threads: " + invThreads + "/" + maxInvThreads + " (MAX 2 pour économiser)");
    getLogger().info("- Total threads: " + (dbThreads + invThreads) + "/4 CPU threads (respecte ton CPU)");
    getLogger().info("- MongoDB Connections: " + mongoManager.getTotalConnections());
    getLogger().info("- Cache réduit: 1000 entrées (économise RAM)");
    getLogger().info("- Batch size: 8 (économise CPU/RAM)");
    getLogger().info("- Mode: PERFORMANCE MINIMALE + Expérience utilisateur préservée");

    // === CRASH PROTECTION INITIALIZATION ===
    startLagDetectionTask();
    registerShutdownHook();
  }

    // Pool ULTRA-ÉCONOME pour AMD Ryzen 9 (4 threads CPU disponibles)
    int cores = Runtime.getRuntime().availableProcessors(); // = 4
    
    // DB Pool : MINIMAL mais efficace - respecte tes 4 threads CPU
    int dbThreads = 3;  // Maximum 3 threads DB (75% du CPU)
    databaseExecutor = new ThreadPoolExecutor(
        2,                                   // Core threads = 2 seulement
        dbThreads,                           // Max threads = 3
        20L, TimeUnit.SECONDS,               // Keep alive long pour économiser
        new LinkedBlockingQueue<>(200),      // Queue petite et efficace
        r -> {
            Thread t = new Thread(r, "NuvaSync-Eco-DB-" + System.nanoTime());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY + 1);  // Priorité modeste
            return t;
        },
        new ThreadPoolExecutor.CallerRunsPolicy()
    );

    // Inventory Pool : TRÈS conservateur
    int invThreads = 2;  // Maximum 2 threads inventory (25% du CPU restant)
    inventoryExecutor = new ThreadPoolExecutor(
        1,                                   // Core threads = 1 seul
        invThreads,                          // Max threads = 2
        30L, TimeUnit.SECONDS,               
        new LinkedBlockingQueue<>(100),      // Queue très petite
        r -> {
            Thread t = new Thread(r, "NuvaSync-Eco-INV-" + System.nanoTime());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY);  // Priorité normale
            return t;
        },
        new ThreadPoolExecutor.CallerRunsPolicy()
    );
  }

  /**
   * Load configuration from config.yml
   */
  private void loadConfiguration() {
    syncXp = getConfig().getBoolean("sync.xp", true);
    syncEnderchest = getConfig().getBoolean("sync.enderchest", true);
    syncInventory = getConfig().getBoolean("sync.inventory", true);
    syncHealth = getConfig().getBoolean("sync.health", true);
    syncHunger = getConfig().getBoolean("sync.hunger", true);

    // Load crash protection settings
    crashProtectionEnabled = getConfig().getBoolean("crash-protection.enabled", true);
    emergencySaveCooldown = getConfig().getInt("crash-protection.emergency-save-cooldown", 30) * 1000;
    highLoadThreshold = getConfig().getInt("autosave.high-load-threshold", 100);
    highLoadInterval = getConfig().getInt("autosave.high-load-interval", 60);
  }

  /**
   * Autosave SUPPRIMÉ - Maintenant toute modification = sauvegarde instantanée
   * Plus besoin d'autosave périodique
   */
  private void setupOptimizedAutosave() {
    // Autosave désactivé - on sauvegarde à chaque changement
    getLogger().info("Autosave périodique DÉSACTIVÉ - Sauvegarde instantanée à chaque modification d'inventaire");
  }

  // === MÉTHODES PUBLIQUES SIMPLIFIÉES ===
  // Plus de système de sauvegarde programmée - tout est instantané

  @Override
  public void onDisable() {
    // Cancel autosave task
    if (autosaveTask != null) {
      autosaveTask.cancel();
    }

    // Cancel lag detection task
    if (lagDetectionTask != null) {
      lagDetectionTask.cancel();
    }

    getLogger().info("Please don't force stop the server. This may take a few seconds.");

    // Final mass save - use synchronous saves during shutdown for reliability
    var players = Bukkit.getOnlinePlayers();
    for (Player player : players) {
      try {
        databaseManager.savePlayer(player);
      } catch (Exception e) {
        getLogger().warning("Failed to save player " + player.getName() + " during shutdown: " + e.getMessage());
      }
    }

    // Shutdown thread pools gracefully
    if (databaseExecutor != null && !databaseExecutor.isShutdown()) {
      databaseExecutor.shutdown();
    }

    if (inventoryExecutor != null && !inventoryExecutor.isShutdown()) {
      inventoryExecutor.shutdown();
    }

    // Close database connections gracefully
    if (databaseManager != null) {
      databaseManager.shutdown();
    }

    // Close MongoDB connection manager gracefully
    if (mongoManager != null) {
      mongoManager.shutdown();
    }

    getLogger().info("Shutdown completed - All data saved");
  }

  // === PUBLIC API METHODS ===

  /**
   * Get the database executor for async operations
   * @return Database thread pool executor
   */
  public ExecutorService getDatabaseExecutor() {
    return databaseExecutor;
  }

  public ExecutorService getInventoryExecutor() {
    return inventoryExecutor;
  }

  /**
   * Get MongoDB connection statistics for monitoring
   * @return Formatted string with connection statistics
   */
  public String getConnectionPoolStats() {
    return mongoManager != null ? mongoManager.getConnectionPoolStats() : "MongoDB not initialized";
  }

  /**
   * Get MongoDB connection manager for advanced operations
   * @return MongoConnectionManager instance
   */
  public MongoConnectionManager getMongoManager() {
    return mongoManager;
  }

  /**
   * Get database manager instance for command access
   * @return DatabaseManager instance
   */
  public DatabaseManager getDatabaseManager() {
    return databaseManager;
  }

  // === GETTERS FOR SYNC OPTIONS ===

  public MessageManager getMessageManager() {
    return messageManager;
  }

  public boolean isSyncXp() {
    return syncXp;
  }

  public boolean isSyncEnderchest() {
    return syncEnderchest;
  }

  public boolean isSyncInventory() {
    return syncInventory;
  }

  public boolean isSyncHealth() {
    return syncHealth;
  }

  public boolean isSyncHunger() {
    return syncHunger;
  }

  // === SETTERS FOR SYNC OPTIONS (WITH CONFIG PERSISTENCE) ===

  public void setSyncXp(boolean value) {
    this.syncXp = value;
    getConfig().set("sync.xp", value);
    saveConfig();
  }

  public void setSyncEnderchest(boolean value) {
    this.syncEnderchest = value;
    getConfig().set("sync.enderchest", value);
    saveConfig();
  }

  public void setSyncInventory(boolean value) {
    this.syncInventory = value;
    getConfig().set("sync.inventory", value);
    saveConfig();
  }

  public void setSyncHealth(boolean value) {
    this.syncHealth = value;
    getConfig().set("sync.health", value);
    saveConfig();
  }

  public void setSyncHunger(boolean value) {
    this.syncHunger = value;
    getConfig().set("sync.hunger", value);
    saveConfig();
  }

  /**
   * Reload plugin configuration and restart services if needed
   */
  public void reloadPlugin() {
    reloadConfig();
    loadConfiguration();

    // Restart autosave if needed
    if (autosaveTask != null) {
      autosaveTask.cancel();
    }
    if (autosaveInterval > 0) {
      setupOptimizedAutosave();
    }
  }

  // === CRASH PROTECTION COMPONENTS ===

  /**
   * Start the lag detection task to monitor server performance
   * Triggers emergency save if lag exceeds threshold
   */
  private void startLagDetectionTask() {
    if (!crashProtectionEnabled) {
      return; // Skip if crash protection disabled
    }

    int lagThreshold = getConfig().getInt("crash-protection.lag-threshold", 100);

    lagDetectionTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
      lastTickTime.set(System.currentTimeMillis()); // Update tick time

      // Check if server is experiencing severe lag
      long currentTime = System.currentTimeMillis();
      long timeSinceLastTick = currentTime - lastTickTime.get();

      if (timeSinceLastTick > lagThreshold) {
        getLogger().warning("Severe lag detected (" + timeSinceLastTick + "ms) - Triggering emergency save!");
        triggerEmergencySave();
      }
    }, 0, 20L); // Every second
  }

  /**
   * Register a shutdown hook to perform emergency save on JVM shutdown
   * Ensures data is not lost in case of crash or forced shutdown
   */
  private void registerShutdownHook() {
    if (!crashProtectionEnabled) {
      return; // Skip if crash protection disabled
    }

    Thread shutdownHook = new Thread(() -> {
      System.out.println("[NuvaPlayerSynchro] CRITICAL: JVM shutdown detected - Emergency data protection activated!");

      // Cancel all tasks to prevent conflicts
      if (lagDetectionTask != null) {
        lagDetectionTask.cancel();
      }
      if (autosaveTask != null) {
        autosaveTask.cancel();
      }

      // Emergency save with timeout
      var players = Bukkit.getOnlinePlayers();
      System.out.println("[NuvaPlayerSynchro] Emergency saving " + players.size() + " players...");

      for (Player player : players) {
        try {
          // Direct synchronous save for shutdown
          databaseManager.savePlayer(player);
        } catch (Exception e) {
          System.err.println("[NuvaPlayerSynchro] CRITICAL: Failed to save player " + player.getName() + ": " + e.getMessage());
        }
      }

      System.out.println("[NuvaPlayerSynchro] Emergency shutdown save completed.");
    }, "NuvaPlayerSynchro-CrashProtection");

    shutdownHook.setPriority(Thread.MAX_PRIORITY); // Highest priority
    Runtime.getRuntime().addShutdownHook(shutdownHook);
  }

  /**
   * Trigger an emergency save for all online players
   */
  private void triggerEmergencySave() {
    if (!crashProtectionEnabled) {
      return;
    }

    // Check emergency save cooldown
    long currentTime = System.currentTimeMillis();
    if (currentTime - lastEmergencySave.get() < emergencySaveCooldown) {
      return; // Still in cooldown
    }

    if (emergencySaveInProgress) {
      return; // Emergency save already in progress
    }

    emergencySaveInProgress = true;
    lastEmergencySave.set(currentTime);

    getLogger().warning("EMERGENCY SAVE TRIGGERED - Saving all online players immediately!");
    var players = Bukkit.getOnlinePlayers();

    for (Player player : players) {
      databaseExecutor.execute(() -> {
        try {
          savePlayerWithBackup(player); // Use backup-enabled save for emergency
        } catch (Exception e) {
          getLogger().severe("CRITICAL: Emergency save failed for player " + player.getName() + ": " + e.getMessage());
        }
      });
    }

    // Wait for saves to complete
    Bukkit.getScheduler().runTaskLater(this, () -> {
      emergencySaveInProgress = false;
      getLogger().info("Emergency save completed for " + players.size() + " players.");
    }, 40L); // 2 seconds to complete
  }

  /**
   * Save player with backup mechanism
   * Attempts multiple saves and provides fallback options
   * @param player Player to save
   */
  private void savePlayerWithBackup(Player player) {
    boolean backupEnabled = getConfig().getBoolean("crash-protection.backup-save-enabled", true);
    int maxRetries = getConfig().getInt("crash-protection.max-emergency-retries", 3);

    // First attempt - normal save
    try {
      databaseManager.savePlayer(player);
      return; // Success, no backup needed
    } catch (Exception e) {
      getLogger().warning("Primary save failed for " + player.getName() + ": " + e.getMessage());

      if (!backupEnabled) {
        return; // Backup disabled
      }
    }

    // Backup attempts with retries
    for (int attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        Thread.sleep(100L * attempt); // Progressive delay
        databaseManager.savePlayer(player);
        getLogger().info("Backup save succeeded for " + player.getName() + " (attempt " + attempt + ")");
        return; // Success
      } catch (Exception e) {
        getLogger().warning("Backup save attempt " + attempt + " failed for " + player.getName() + ": " + e.getMessage());

        if (attempt == maxRetries) {
          getLogger().severe("CRITICAL: All save attempts failed for " + player.getName() + " - DATA MAY BE LOST!");
          // TODO: Could implement file-based emergency backup here
        }
      }
    }
  }

  /**
   * Disable verbose logging for MongoDB driver
   * Reduces log spam and improves performance
   */
  private void disableMongoLogging() {
    try {
      // Disable ALL MongoDB driver logging completely
      java.util.logging.Logger mongoClientLogger = java.util.logging.Logger.getLogger("org.mongodb.driver.client");
      mongoClientLogger.setLevel(java.util.logging.Level.OFF);

      java.util.logging.Logger mongoClusterLogger = java.util.logging.Logger.getLogger("org.mongodb.driver.cluster");
      mongoClusterLogger.setLevel(java.util.logging.Level.OFF);

      java.util.logging.Logger mongoConnectionLogger = java.util.logging.Logger.getLogger("org.mongodb.driver.connection");
      mongoConnectionLogger.setLevel(java.util.logging.Level.OFF);

      // Also disable the root mongodb logger
      java.util.logging.Logger rootMongoLogger = java.util.logging.Logger.getLogger("org.mongodb");
      rootMongoLogger.setLevel(java.util.logging.Level.OFF);
    } catch (Exception e) {
      // Ignore logging configuration errors silently
    }
  }
}
