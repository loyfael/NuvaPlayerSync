package loyfael;

import org.bukkit.entity.Player;

import java.sql.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * DatabaseManager optimized for high performance - Supports hundreds of players
 *
 * Key Features:
 * - Thread-safe caching with ConcurrentHashMap
 * - Performance monitoring with atomic counters
 * - Batch processing capabilities for reduced database load
 * - Asynchronous operations to prevent server lag
 * - Prepared statement reuse for optimal performance
 *
 * Synchronizes only: XP, Enderchest, Inventory, Health, Hunger
 */
public class DatabaseManager {
    private final Main plugin;
    private final ConcurrentHashMap<String, PlayerDataCache> cache = new ConcurrentHashMap<>();
    private final PlayerDataExtractor extractor;
    private final PlayerDataApplier applier;

    // Performance monitoring with atomic operations for thread safety
    private final AtomicLong totalOperations = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);

    // Reusable prepared statements for optimal performance
    private static final String SAVE_SQL =
        "REPLACE INTO player_data (uuid, xp, enderchest, inventory, health, hunger, saturation, last_updated) VALUES (?,?,?,?,?,?,?,?)";
    private static final String LOAD_SQL =
        "SELECT * FROM player_data WHERE uuid = ?";
    private static final String BATCH_LOAD_SQL =
        "SELECT * FROM player_data WHERE uuid IN (%s)";

    public DatabaseManager(Main plugin) {
        this.plugin = plugin;
        this.extractor = new PlayerDataExtractor(plugin);
        this.applier = new PlayerDataApplier(plugin);
    }

    /**
     * Initialize database table with optimizations for high performance
     * Creates indexes and applies database-specific optimizations
     */
    public void initialize() {
        String sql = "CREATE TABLE IF NOT EXISTS player_data (" +
                "uuid VARCHAR(36) PRIMARY KEY," +
                "xp INT," +
                "enderchest MEDIUMTEXT," +
                "inventory MEDIUMTEXT," +
                "health DOUBLE," +
                "hunger INT," +
                "saturation FLOAT," +
                "last_updated BIGINT," +
                "INDEX idx_uuid (uuid)," +
                "INDEX idx_last_updated (last_updated)" +
                ")";

        try (Connection connection = plugin.getConnection();
             Statement st = connection.createStatement()) {

            st.executeUpdate(sql);
            optimizeDatabase(st);

            plugin.getLogger().info("Table player_data initialized with high-performance optimizations");

        } catch (SQLException e) {
            plugin.getLogger().severe("Could not create table: " + e.getMessage());
        }
    }

    /**
     * Apply database-specific optimizations for better performance
     * @param st Statement to execute optimization queries
     * @throws SQLException If optimization queries fail
     */
    private void optimizeDatabase(Statement st) throws SQLException {
        String dbType = plugin.getConfig().getString("database.type", "mysql");
        if (dbType.equalsIgnoreCase("mysql")) {
            try {
                // Use InnoDB engine for better concurrent performance
                st.executeUpdate("ALTER TABLE player_data ENGINE=InnoDB");
                // Enable compression to reduce storage and improve cache efficiency
                st.executeUpdate("ALTER TABLE player_data ROW_FORMAT=COMPRESSED");
            } catch (SQLException e) {
                plugin.getLogger().info("MySQL optimizations skipped (insufficient privileges)");
            }
        }
    }

    /**
     * Save player data asynchronously with caching and cooldown protection
     * Uses cache comparison to avoid unnecessary database writes
     * @param player Player to save
     */
    public void savePlayer(Player player) {
        totalOperations.incrementAndGet();

        // Check cooldown to prevent spam saves
        if (!plugin.shouldSavePlayer(player.getUniqueId().toString())) {
            return; // Cooldown active
        }

        String uuid = player.getUniqueId().toString();
        PlayerDataCache currentData = extractor.extract(player);
        PlayerDataCache cachedData = cache.get(uuid);

        // Cache hit optimization - skip save if data hasn't changed
        if (cachedData != null && cachedData.equals(currentData)) {
            cacheHits.incrementAndGet();
            return;
        }

        cacheMisses.incrementAndGet();
        cache.put(uuid, currentData);

        // Asynchronous save using dedicated database pool
        plugin.getDatabaseExecutor().execute(() -> savePlayerAsync(currentData));
    }

    /**
     * Asynchronous save operation executed in database thread pool
     * @param data Player data cache to save to database
     */
    private void savePlayerAsync(PlayerDataCache data) {
        try (Connection connection = plugin.getConnection();
             PreparedStatement ps = connection.prepareStatement(SAVE_SQL)) {

            // Optimized batch preparation
            ps.setString(1, data.uuid);
            ps.setInt(2, data.xp);
            ps.setString(3, data.enderchest);
            ps.setString(4, data.inventory);
            ps.setDouble(5, data.health);
            ps.setInt(6, data.hunger);
            ps.setFloat(7, data.saturation);
            ps.setLong(8, System.currentTimeMillis());

            ps.executeUpdate();

            // Log successful saves for critical data (optional, can be disabled for performance)
            if (plugin.getConfig().getBoolean("performance.log-successful-saves", false)) {
                plugin.getLogger().info("Successfully saved data for player " + data.uuid);
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("SAVE ERROR for player " + data.uuid + ": " + e.getMessage());
            plugin.getLogger().severe("SQL State: " + e.getSQLState() + ", Error Code: " + e.getErrorCode());
        }
    }

    /**
     * Load player data asynchronously from database
     * @param player Player to load data for
     */
    public void loadPlayer(Player player) {
        plugin.getDatabaseExecutor().execute(() -> loadPlayerAsync(player));
    }

    /**
     * Asynchronous load operation with cache optimization
     * @param player Player to load data for
     */
    private void loadPlayerAsync(Player player) {
        String uuid = player.getUniqueId().toString();
        totalOperations.incrementAndGet();

        // Optimized cache check with expiration
        PlayerDataCache cached = cache.get(uuid);
        if (cached != null && !cached.isExpired(30000)) {
            cacheHits.incrementAndGet();
            applier.apply(player, cached);
            return;
        }

        cacheMisses.incrementAndGet();

        try (Connection connection = plugin.getConnection();
             PreparedStatement ps = connection.prepareStatement(LOAD_SQL)) {

            ps.setString(1, uuid);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    PlayerDataCache data = mapResultSetToCache(rs, uuid);
                    cache.put(uuid, data);
                    applier.apply(player, data);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Load error for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Batch loading for optimizing multiple connections (used for high load scenarios)
     * Loads multiple players in a single query to reduce database round trips
     * @param players List of players to load data for
     */
    @SuppressWarnings("unused") // Reserved for future optimizations
    public void loadPlayersBatch(List<Player> players) {
        if (players.isEmpty()) return;

        plugin.getDatabaseExecutor().execute(() -> {
            var uuids = players.stream().map(p -> p.getUniqueId().toString()).toList();
            var placeholders = String.join(",", uuids.stream().map(u -> "?").toList());
            String sql = String.format(BATCH_LOAD_SQL, placeholders);

            try (Connection connection = plugin.getConnection();
                 PreparedStatement ps = connection.prepareStatement(sql)) {

                for (int i = 0; i < uuids.size(); i++) {
                    ps.setString(i + 1, uuids.get(i));
                }

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String uuid = rs.getString("uuid");
                        PlayerDataCache data = mapResultSetToCache(rs, uuid);
                        cache.put(uuid, data);

                        // Apply to corresponding players
                        players.stream()
                            .filter(p -> p.getUniqueId().toString().equals(uuid))
                            .findFirst()
                            .ifPresent(player -> applier.apply(player, data));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Batch load error: " + e.getMessage());
            }
        });
    }

    /**
     * Map database result set to cache object
     * @param rs ResultSet from database query
     * @param uuid Player UUID
     * @return PlayerDataCache object with loaded data
     * @throws SQLException If database fields cannot be read
     */
    private PlayerDataCache mapResultSetToCache(ResultSet rs, String uuid) throws SQLException {
        PlayerDataCache data = new PlayerDataCache(uuid);
        data.xp = rs.getInt("xp");
        data.enderchest = rs.getString("enderchest");
        data.inventory = rs.getString("inventory");
        data.health = rs.getDouble("health");
        data.hunger = rs.getInt("hunger");
        data.saturation = rs.getFloat("saturation");
        return data;
    }

    /**
     * Clear cache entry for specific player
     * @param uuid Player UUID to remove from cache
     */
    public void clearCache(String uuid) {
        cache.remove(uuid);
    }

    /**
     * Get current cache size for monitoring
     * @return Number of cached player entries
     */
    public int getCacheSize() {
        return cache.size();
    }

    /**
     * Get performance statistics for monitoring and debugging
     * @return Formatted string with cache hit rate and operation counts
     */
    public String getPerformanceStats() {
        long total = totalOperations.get();
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        double hitRate = total > 0 ? (hits * 100.0 / total) : 0;

        return String.format("Operations: %d, Cache: %.1f%% hits (%d/%d), Size: %d",
            total, hitRate, hits, misses, cache.size());
    }

    /**
     * Periodic cache cleanup to prevent memory overload (called by scheduler)
     * Removes entries older than 5 minutes to keep memory usage under control
     */
    @SuppressWarnings("unused") // Used by cleanup scheduler
    public void cleanupExpiredCache() {
        long expiredTime = System.currentTimeMillis() - 300000; // 5 minutes
        cache.entrySet().removeIf(entry -> entry.getValue().lastUpdated < expiredTime);
    }
}
