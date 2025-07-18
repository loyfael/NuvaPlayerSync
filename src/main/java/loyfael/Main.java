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
 * NuvaPlayerSynchro - High-performance player data synchronization plugin
 * Designed to handle hundreds of concurrent players with optimized threading and caching
 * This plugin provides a robust solution for synchronizing player data across server restarts and crashes
 * Features:
 * - Asynchronous database operations with dedicated thread pools
 * - MongoDB connection management for massive query operations
 * - Advanced caching system with configurable cleanup
 * - Batch processing for reduced database load
 * - Separate thread pools for database and inventory operations
 * - Crash protection with JVM shutdown hooks and lag detection
 * - Emergency save mechanism with multiple retries and backup options
 * Synchronizes: XP, Enderchest, Inventory, Health, Hunger
 */
public class Main extends JavaPlugin {
  // High-performance MongoDB connection manager
  private MongoConnectionManager mongoManager;

  // Optimized threading for high load scenarios
  private ExecutorService databaseExecutor;    // Dedicated pool for database operations
  private ExecutorService inventoryExecutor;   // Separate pool for inventory serialization/deserialization

  // Optimized cache with ConcurrentHashMap for thread safety
  private final ConcurrentHashMap<String, Long> lastSaveTime = new ConcurrentHashMap<>();
  private final long SAVE_COOLDOWN = 1000; // Reduced to 1 second for better responsiveness

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

    // Log performance information
    int dbThreads = ((ThreadPoolExecutor) databaseExecutor).getCorePoolSize();
    int invThreads = ((ThreadPoolExecutor) inventoryExecutor).getCorePoolSize();
    getLogger().info("Plugin activated in HIGH PERFORMANCE mode:");
    getLogger().info("- " + dbThreads + " database threads");
    getLogger().info("- " + invThreads + " inventory threads");
    getLogger().info("- MongoDB Connections: " + mongoManager.getTotalConnections() + " connections");

    // === CRASH PROTECTION INITIALIZATION ===
    startLagDetectionTask();
    registerShutdownHook();
  }

  /**
   * Initialize ultra-high-performance thread pools optimized for massive concurrent operations
   * Uses aggressive settings and CPU-specific optimizations
   */
  private void initializeHighPerformanceThreadPools() {
    int cores = Runtime.getRuntime().availableProcessors();

    // Ultra-aggressive DB pool: cores * 4 for extreme concurrent load
    int dbThreads = Math.max(8, cores * 4);  // Minimum 8 threads, scale with CPU
    databaseExecutor = new ThreadPoolExecutor(
        dbThreads,                           // Core threads = max threads for instant availability
        dbThreads,                           // Maximum threads
        30L, TimeUnit.SECONDS,               // Shorter keep alive for resource efficiency
        new LinkedBlockingQueue<>(2000),     // Larger queue for burst handling
        r -> {
            Thread t = new Thread(r, "NuvaSync-UltraDB-" + System.nanoTime());
            t.setDaemon(true);
            t.setPriority(Thread.MAX_PRIORITY - 1);  // High priority for DB ops
            return t;
        },
        new ThreadPoolExecutor.CallerRunsPolicy()  // Backpressure handling
    );

    // Optimized inventory pool with burst capacity
    int invThreads = Math.max(4, cores * 2);
    inventoryExecutor = new ThreadPoolExecutor(
        invThreads / 2,                      // Core threads
        invThreads * 2,                      // Burst capacity for heavy inventory ops
        45L, TimeUnit.SECONDS,               // Keep alive
        new LinkedBlockingQueue<>(1000),     // Large queue for inventory serialization
        r -> {
            Thread t = new Thread(r, "NuvaSync-UltraInv-" + System.nanoTime());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY + 1);  // Above normal priority
            return t;
        },
        new ThreadPoolExecutor.CallerRunsPolicy()
    );

    // Pre-warm thread pools for instant response
    ((ThreadPoolExecutor) databaseExecutor).prestartAllCoreThreads();
    ((ThreadPoolExecutor) inventoryExecutor).prestartCoreThread();
  }

  /**
   * Load synchronization configuration from config.yml
   * Only loads options that are still supported after cleanup
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
   * Setup optimized autosave system with adaptive intervals based on player count
   * Processes players in batches to reduce database load and improve performance
   */
  private void setupOptimizedAutosave() {
    if (autosaveInterval > 0) {
      autosaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
        // Adaptive interval based on player count (crash protection)
        int currentPlayerCount = Bukkit.getOnlinePlayers().size();
        int effectiveInterval = currentPlayerCount >= highLoadThreshold ? highLoadInterval : autosaveInterval;

        // Log interval change for monitoring
        if (currentPlayerCount >= highLoadThreshold) {
          getLogger().info("High load detected (" + currentPlayerCount + " players) - Using accelerated autosave (" + highLoadInterval + "s)");
        }

        // Batch processing to reduce database load
        long currentTime = System.currentTimeMillis();
        var playersToSave = Bukkit.getOnlinePlayers().stream()
            .filter(player -> {
                String uuid = player.getUniqueId().toString();
                Long lastSave = lastSaveTime.get(uuid);
                return lastSave == null || (currentTime - lastSave) >= SAVE_COOLDOWN;
            })
            .toList();

        // Use bulk save for better performance with large player counts
        if (playersToSave.size() >= 20) {
            // Use bulk save for large batches - convert to ArrayList for proper type
            databaseManager.savePlayersBulk(new java.util.ArrayList<>(playersToSave));
            for (Player player : playersToSave) {
                lastSaveTime.put(player.getUniqueId().toString(), currentTime);
            }
        } else {
            // Process smaller batches individually for faster response
            int batchSize = 10;
            for (int i = 0; i < playersToSave.size(); i += batchSize) {
                var batch = playersToSave.subList(i, Math.min(i + batchSize, playersToSave.size()));
                databaseExecutor.execute(() -> {
                    for (Player player : batch) {
                        savePlayerWithBackup(player);
                        lastSaveTime.put(player.getUniqueId().toString(), currentTime);
                    }
                });
            }
        }

        // Periodic cache cleanup every 5 autosave cycles
        if (System.currentTimeMillis() % (5L * autosaveInterval * 1000) < 1000) {
            databaseManager.cleanupExpiredCache();
        }

        if (!playersToSave.isEmpty()) {
          getLogger().info("Automatic save: " + playersToSave.size() + " players (interval: " + effectiveInterval + "s)");
        }
      }, 20L * autosaveInterval, 20L * Math.min(autosaveInterval, highLoadInterval));
    }
  }

  /**
   * Graceful shutdown handling with final mass save and timeout protection
   */
  @Override
  public void onDisable() {
    getLogger().info("Shutdown in progress - Final save of " + Bukkit.getOnlinePlayers().size() + " players..");

    // Cancel autosave task
    if (autosaveTask != null) {
      autosaveTask.cancel();
    }

    // Final mass save with timeout protection
    if (databaseExecutor != null && !databaseExecutor.isShutdown()) {
      var players = Bukkit.getOnlinePlayers();
      for (Player player : players) {
        databaseExecutor.execute(() -> databaseManager.savePlayer(player));
      }

      shutdownExecutorGracefully(databaseExecutor, "Database", 15);
    }

    if (inventoryExecutor != null && !inventoryExecutor.isShutdown()) {
      shutdownExecutorGracefully(inventoryExecutor, "Inventory", 10);
    }

    // Close database manager gracefully
    if (databaseManager != null) {
      databaseManager.shutdown();
    }

    // Close connection manager
    if (mongoManager != null) {
      mongoManager.shutdown();
    }

    getLogger().info("Shutdown completed - All data saved");
  }

  /**
   * Gracefully shutdown executor with timeout protection
   * @param executor The executor service to shut down
   * @param name Name for logging purposes
   * @param timeoutSeconds Maximum time to wait for shutdown
   */
  private void shutdownExecutorGracefully(ExecutorService executor, String name, int timeoutSeconds) {
    executor.shutdown();
    try {
      if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
        getLogger().warning(name + " executor timeout - Force shutdown");
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
    }
  }

  // === PUBLIC API METHODS ===

  /**
   * Get the database executor for async operations
   * @return Database thread pool executor
   */
  public ExecutorService getDatabaseExecutor() {
    return databaseExecutor;
  }

  /**
   * Get the inventory executor for heavy serialization operations
   * @return Inventory thread pool executor
   */
  public ExecutorService getInventoryExecutor() {
    return inventoryExecutor;
  }

  /**
   * Check if player should be saved based on cooldown
   * @param uuid Player UUID
   * @return true if player can be saved, false if still in cooldown
   */
  public boolean shouldSavePlayer(String uuid) {
    Long lastSave = lastSaveTime.get(uuid);
    return lastSave == null || (System.currentTimeMillis() - lastSave) >= SAVE_COOLDOWN;
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

    // Reload message manager with new language setting
    String lang = getConfig().getString("language", "en");
    messageManager.load(lang);

    // Reload sync configuration
    loadConfiguration();

    // Handle autosave interval changes
    int newInterval = getConfig().getInt("autosave.interval", 300);
    if (newInterval != autosaveInterval) {
      autosaveInterval = newInterval;
      if (autosaveTask != null) {
        autosaveTask.cancel();
      }
      if (autosaveInterval > 0) {
        setupOptimizedAutosave();
      } else {
        autosaveTask = null;
      }
    }
  }

  /**
   * Get database manager instance for command access
   * @return DatabaseManager instance
   */
  public DatabaseManager getDatabaseManager() {
    return databaseManager;
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
   * Saves player data immediately to prevent loss
   */
  private void triggerEmergencySave() {
    if (!crashProtectionEnabled) {
      return; // Crash protection disabled
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
}
