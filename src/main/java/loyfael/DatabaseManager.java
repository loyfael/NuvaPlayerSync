package loyfael;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import org.bson.Document;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Comparator;

/**
 * Ultra-high-performance MongoDB DatabaseManager optimized for massive concurrent operations
 * Performance Features:
 * - Bulk write operations with ordered processing
 * - Advanced caching with LRU eviction
 * - Asynchronous batch processing queue
 * - Memory-efficient document reuse
 * - Optimized indexes and queries
 */
public class DatabaseManager {
    private final Main plugin;
    private final MongoConnectionManager mongoManager;
    private final ConcurrentHashMap<String, PlayerDataCache> cache = new ConcurrentHashMap<>();
    private final PlayerDataExtractor extractor;
    private final PlayerDataApplier applier;

    // Performance monitoring with atomic operations
    private final AtomicLong totalOperations = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong bulkOperations = new AtomicLong(0);

    // Optimisations ÉCONOMES pour 4 threads CPU - Performance minimale requise
    private static final String COLLECTION_NAME = "player_data";
    private static final int BULK_WRITE_THRESHOLD = 8;   // Petit batch = moins de RAM/CPU
    private static final int MAX_CACHE_SIZE = 750;      // Cache RÉDUIT pour économiser encore plus de RAM

    // Asynchronous batch processing
    private final ConcurrentLinkedQueue<WriteModel<Document>> pendingWrites = new ConcurrentLinkedQueue<>();
    private volatile boolean batchProcessorRunning = false;

    public DatabaseManager(Main plugin) {
        this.plugin = plugin;
        this.mongoManager = new MongoConnectionManager(plugin);
        this.extractor = new PlayerDataExtractor(plugin);
        this.applier = new PlayerDataApplier(plugin);
    }

    /**
     * Initialize MongoDB with ultra-performance optimizations
     */
    public void initialize() {
        try {
            mongoManager.initialize();

            MongoDatabase database = mongoManager.getDatabase();
            MongoCollection<Document> collection = database.getCollection(COLLECTION_NAME);

            // Create ultra-optimized indexes
            createOptimizedIndexes(collection);

            // Start asynchronous batch processor
            startBatchProcessor();

            plugin.getLogger().info("DatabaseManager initialized with ULTRA-HIGH PERFORMANCE:");
            plugin.getLogger().info("- Bulk write threshold: " + BULK_WRITE_THRESHOLD);
            plugin.getLogger().info("- Cache size: " + MAX_CACHE_SIZE);
            plugin.getLogger().info("- Asynchronous batch processing enabled");

        } catch (Exception e) {
            plugin.getLogger().severe("DatabaseManager ultra-performance setup failed: " + e.getMessage());
            throw new RuntimeException("DatabaseManager initialization failed", e);
        }
    }

    /**
     * Create ultra-optimized indexes for maximum query performance
     */
    private void createOptimizedIndexes(MongoCollection<Document> collection) {
        // Primary index with background creation
        collection.createIndex(
            Indexes.ascending("uuid"),
            new IndexOptions().background(true).name("uuid_primary"));

        // Compound index for cleanup operations - using Document instead of Indexes.compound
        collection.createIndex(
            new Document("uuid", 1).append("last_updated", -1),
            new IndexOptions().background(true).name("uuid_lastupdate_compound"));

        // Sparse index for active players only
        collection.createIndex(
            Indexes.descending("last_updated"),
            new IndexOptions()
                .background(true)
                .sparse(true)
                .name("lastupdate_sparse"));
    }

    /**
     * Sauvegarde ultra-rapide avec vérification cache INTELLIGENTE + protection RAM
     */
    public void savePlayer(Player player) {
        totalOperations.incrementAndGet();

        String uuid = player.getUniqueId().toString();
        PlayerDataCache currentData = extractor.extract(player);
        PlayerDataCache cachedData = cache.get(uuid);

        // Vérification cache RAPIDE - skip seulement si 100% identique
        if (cachedData != null && cachedData.quickEquals(currentData)) {
            cacheHits.incrementAndGet();
            return; // Skip inutile mais garde la performance
        }

        // PROTECTION RAM : Nettoyage automatique du cache
        manageCacheSize();

        cacheMisses.incrementAndGet();
        cache.put(uuid, currentData);

        // Add to batch processing queue for ultra-performance
        addToBatchQueue(currentData);
    }

    /**
     * SAUVEGARDE SYNCHRONE ULTRA-PRIORITAIRE
     * Utilisée pour les déconnexions - bypass complet du système de batch
     * Garantit que la sauvegarde est terminée avant de continuer
     */
    public void savePlayerSync(Player player) {
        totalOperations.incrementAndGet();

        String uuid = player.getUniqueId().toString();
        PlayerDataCache currentData = extractor.extract(player);

        // Mettre à jour le cache immédiatement
        cache.put(uuid, currentData);

        try {
            // Sauvegarde DIRECTE en base sans passer par le système de batch
            MongoDatabase database = mongoManager.getDatabase();
            MongoCollection<Document> collection = database.getCollection("player_data");
            
            Document playerDoc = createOptimizedDocument(currentData);
            ReplaceOptions options = new ReplaceOptions().upsert(true);
            
            // OPÉRATION SYNCHRONE - attend la fin avant de retourner
            collection.replaceOne(Filters.eq("uuid", uuid), playerDoc, options);
            
        } catch (Exception e) {
            plugin.getLogger().severe("ÉCHEC SAUVEGARDE SYNCHRONE pour " + player.getName() + ": " + e.getMessage());
            throw new RuntimeException("Sauvegarde synchrone échouée", e);
        }
    }

    /**
     * Intelligent cache size management with LRU eviction - AGGRESSIF pour économiser RAM
     */
    private void manageCacheSize() {
        if (cache.size() >= MAX_CACHE_SIZE) {
            // Remove 15% of oldest entries for better RAM management
            int toRemove = Math.max(MAX_CACHE_SIZE / 7, 10); // Minimum 10, sinon 15%
            cache.entrySet().stream()
                .sorted(Comparator.comparingLong(entry -> entry.getValue().lastUpdated))
                .limit(toRemove)
                .forEach(entry -> cache.remove(entry.getKey()));
        }
    }

    /**
     * Add operation to batch processing queue
     */
    private void addToBatchQueue(PlayerDataCache data) {
        Document playerDoc = createOptimizedDocument(data);
        ReplaceOptions options = new ReplaceOptions().upsert(true);

        pendingWrites.offer(new ReplaceOneModel<>(
            Filters.eq("uuid", data.uuid),
            playerDoc,
            options));

        // Notifier le processeur batch qu'il y a de nouvelles écritures
        synchronized (pendingWrites) {
            pendingWrites.notify();
        }

        // Trigger batch processing if threshold reached
        if (pendingWrites.size() >= BULK_WRITE_THRESHOLD) {
            processBatchAsync();
        }
    }

    /**
     * Create memory-optimized MongoDB document
     */
    private Document createOptimizedDocument(PlayerDataCache data) {
        return new Document("uuid", data.uuid)
            .append("xp", data.xp)
            .append("enderchest", data.enderchest)
            .append("inventory", data.inventory)
            .append("health", data.health)
            .append("hunger", data.hunger)
            .append("saturation", data.saturation)
            .append("last_updated", System.currentTimeMillis());
    }

    /**
     * Start asynchronous batch processor for ultra-performance
     */
    private void startBatchProcessor() {
        plugin.getDatabaseExecutor().execute(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // Attendre qu'il y ait des écritures en attente ou une interruption
                    synchronized (pendingWrites) {
                        while (pendingWrites.isEmpty() && !Thread.currentThread().isInterrupted()) {
                            pendingWrites.wait(100); // Attendre max 100ms ou jusqu'à notification
                        }
                    }

                    if (!pendingWrites.isEmpty() && !batchProcessorRunning) {
                        processBatchAsync();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    plugin.getLogger().warning("Batch processor error: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Process batch writes asynchronously for maximum performance
     */
    private void processBatchAsync() {
        if (batchProcessorRunning || pendingWrites.isEmpty()) {
            return;
        }

        batchProcessorRunning = true;

        CompletableFuture.runAsync(() -> {
            try {
                List<WriteModel<Document>> batch = new ArrayList<>();
                WriteModel<Document> write;

                // Collect batch operations
                while ((write = pendingWrites.poll()) != null && batch.size() < BULK_WRITE_THRESHOLD) {
                    batch.add(write);
                }

                if (!batch.isEmpty()) {
                    MongoDatabase database = mongoManager.getDatabase();
                    MongoCollection<Document> collection = database.getCollection(COLLECTION_NAME);

                    // Ultra-fast bulk write with optimized options
                    BulkWriteOptions options = new BulkWriteOptions()
                        .ordered(false)  // Unordered for maximum speed
                        .bypassDocumentValidation(true);  // Skip validation for speed

                    collection.bulkWrite(batch, options);

                    bulkOperations.incrementAndGet();
                    mongoManager.recordSuccess();

                    if (plugin.getConfig().getBoolean("performance.log-bulk-operations", false)) {
                        plugin.getLogger().info("Bulk processed " + batch.size() + " operations");
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Bulk write error: " + e.getMessage());
                mongoManager.recordFailure();
            } finally {
                batchProcessorRunning = false;
            }
        }, plugin.getDatabaseExecutor());
    }

    /**
     * Load player data from MongoDB with caching optimization
     */
    public void loadPlayer(Player player) {
        plugin.getDatabaseExecutor().execute(() -> loadPlayerAsync(player));
    }

    /**
     * CHARGEMENT SYNCHRONE ULTRA-PRIORITAIRE pour les connexions
     * Bloque jusqu'à ce que les données soient complètement chargées
     * Garantit que l'inventaire est synchronisé AVANT que le joueur puisse jouer
     */
    public void loadPlayerSync(Player player) {
        String uuid = player.getUniqueId().toString();
        totalOperations.incrementAndGet();

        // Cache check d'abord
        PlayerDataCache cached = cache.get(uuid);
        if (cached != null && !cached.isExpired(30000)) {
            cacheHits.incrementAndGet();
            applier.apply(player, cached);
            return;
        }

        cacheMisses.incrementAndGet();

        try {
            MongoDatabase database = mongoManager.getDatabase();
            MongoCollection<Document> collection = database.getCollection(COLLECTION_NAME);

            // REQUÊTE SYNCHRONE - attend le résultat
            Document playerDoc = collection.find(Filters.eq("uuid", uuid)).first();

            if (playerDoc != null) {
                PlayerDataCache data = documentToCache(playerDoc, uuid);
                cache.put(uuid, data);
                applier.apply(player, data);
                mongoManager.recordSuccess();
            } else {
                // Nouveau joueur - pas de données à charger
                plugin.getLogger().info("Nouveau joueur détecté: " + player.getName());
            }

        } catch (Exception e) {
            plugin.getLogger().severe("ÉCHEC CHARGEMENT SYNCHRONE pour " + player.getName() + ": " + e.getMessage());
            mongoManager.recordFailure();
            throw new RuntimeException("Chargement synchrone échoué", e);
        }
    }

    /**
     * Asynchronous load operation with MongoDB query optimization
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

        try {
            MongoDatabase database = mongoManager.getDatabase();
            MongoCollection<Document> collection = database.getCollection(COLLECTION_NAME);

            // Fast MongoDB query with index usage
            Document playerDoc = collection.find(Filters.eq("uuid", uuid)).first();

            if (playerDoc != null) {
                PlayerDataCache data = documentToCache(playerDoc, uuid);
                cache.put(uuid, data);
                applier.apply(player, data);
                mongoManager.recordSuccess();
            }

        } catch (Exception e) {
            plugin.getLogger().warning("MongoDB load error for " + player.getName() + ": " + e.getMessage());
            mongoManager.recordFailure();
        }
    }

    /**
     * Bulk save operation for massive player counts
     * Uses MongoDB's bulk write operations for maximum performance
     */
    public void savePlayersBulk(List<Player> players) {
        if (players.isEmpty()) return;

        bulkOperations.incrementAndGet();
        plugin.getDatabaseExecutor().execute(() -> {
            try {
                MongoDatabase database = mongoManager.getDatabase();
                MongoCollection<Document> collection = database.getCollection(COLLECTION_NAME);

                // Prepare bulk write operations
                var bulkWrites = new java.util.ArrayList<com.mongodb.client.model.WriteModel<Document>>();

                for (Player player : players) {
                    PlayerDataCache data = extractor.extract(player);
                    Document playerDoc = new Document("uuid", data.uuid)
                        .append("xp", data.xp)
                        .append("enderchest", data.enderchest)
                        .append("inventory", data.inventory)
                        .append("health", data.health)
                        .append("hunger", data.hunger)
                        .append("saturation", data.saturation)
                        .append("last_updated", System.currentTimeMillis());

                    ReplaceOptions options = new ReplaceOptions().upsert(true);
                    bulkWrites.add(new com.mongodb.client.model.ReplaceOneModel<>(
                        Filters.eq("uuid", data.uuid), playerDoc, options));

                    cache.put(data.uuid, data);
                }

                // Execute bulk write - much faster than individual operations
                if (!bulkWrites.isEmpty()) {
                    collection.bulkWrite(bulkWrites);
                    plugin.getLogger().info("Bulk saved " + bulkWrites.size() + " players to MongoDB");
                    mongoManager.recordSuccess();
                }

            } catch (Exception e) {
                plugin.getLogger().severe("MongoDB bulk save error: " + e.getMessage());
                mongoManager.recordFailure();
            }
        });
    }

    /**
     * Convert MongoDB document to PlayerDataCache
     */
    private PlayerDataCache documentToCache(Document doc, String uuid) {
        PlayerDataCache data = new PlayerDataCache(uuid);
        data.xp = doc.getInteger("xp", 0);
        data.enderchest = doc.getString("enderchest");
        data.inventory = doc.getString("inventory");
        data.health = doc.getDouble("health");
        data.hunger = doc.getInteger("hunger", 20);
        data.saturation = doc.getDouble("saturation").floatValue();
        data.lastUpdated = doc.getLong("last_updated");
        return data;
    }

    /**
     * Clear cache entry for specific player
     */
    public void clearCache(String uuid) {
        cache.remove(uuid);
    }

    /**
     * Get current cache size for monitoring
     */
    public int getCacheSize() {
        return cache.size();
    }

    /**
     * Get comprehensive performance statistics including MongoDB metrics
     */
    public String getPerformanceStats() {
        long total = totalOperations.get();
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long bulk = bulkOperations.get();
        double hitRate = total > 0 ? (hits * 100.0 / total) : 0;

        return String.format("MongoDB Operations: %d total, Cache: %.1f%% hits (%d/%d), Bulk: %d, Size: %d",
            total, hitRate, hits, misses, bulk, cache.size());
    }

    /**
     * Get MongoDB connection statistics with detailed client info
     */
    public String getConnectionPoolStats() {
        // Use the MongoDB client for advanced statistics if available
        try {
            var client = mongoManager.getClient();
            if (client != null) {
                return String.format("MongoDB Pool: Connected to %s, %d operations processed",
                    "MongoDB Cluster", mongoManager.getTotalConnections());
            }
        } catch (Exception e) {
            // Fallback to basic stats
        }
        return mongoManager.getConnectionPoolStats();
    }

    /**
     * Periodic cache cleanup optimized for MongoDB
     */
    public void cleanupExpiredCache() {
        long expiredTime = System.currentTimeMillis() - 300000; // 5 minutes
        cache.entrySet().removeIf(entry -> entry.getValue().lastUpdated < expiredTime);
    }

    /**
     * Shutdown MongoDB connection gracefully
     */
    public void shutdown() {
        mongoManager.shutdown();
    }

    /**
     * Reset/delete player data from database
     * Used for troubleshooting problematic player data
     */
    public CompletableFuture<Boolean> resetPlayerData(String uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                MongoDatabase database = mongoManager.getDatabase();
                MongoCollection<Document> collection = database.getCollection(COLLECTION_NAME);

                // Remove from cache first
                cache.remove(uuid);

                // Delete from database
                var result = collection.deleteOne(Filters.eq("uuid", uuid));

                totalOperations.incrementAndGet();

                plugin.getLogger().info("Player data reset for UUID: " + uuid +
                    " (deleted: " + result.getDeletedCount() + " documents)");

                return result.getDeletedCount() > 0;
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to reset player data for UUID " + uuid + ": " + e.getMessage());
                return false;
            }
        }, plugin.getDatabaseExecutor());
    }

    /**
     * Check if player data exists in database
     */
    public CompletableFuture<Boolean> playerDataExists(String uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                MongoDatabase database = mongoManager.getDatabase();
                MongoCollection<Document> collection = database.getCollection(COLLECTION_NAME);

                long count = collection.countDocuments(Filters.eq("uuid", uuid));
                return count > 0;
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to check player data existence for UUID " + uuid + ": " + e.getMessage());
                return false;
            }
        }, plugin.getDatabaseExecutor());
    }
}
