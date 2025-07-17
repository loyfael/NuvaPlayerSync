package loyfael;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Pool de connexions haute performance avec HikariCP
 * Optimisé pour supporter plusieurs centaines de joueurs simultanés
 */
public class HighPerformanceConnectionPool {
    private final Main plugin;
    private HikariDataSource dataSource;

    public HighPerformanceConnectionPool(Main plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        HikariConfig config = new HikariConfig();

        String dbType = plugin.getConfig().getString("database.type", "mysql");

        if (dbType.equalsIgnoreCase("mysql")) {
            configureMySQLPool(config);
        } else {
            configureSQLitePool(config);
        }

        // Optimisations globales haute performance
        configureHighPerformanceSettings(config);

        this.dataSource = new HikariDataSource(config);
        plugin.getLogger().info("Pool de connexions HikariCP initialisé avec " +
                               config.getMaximumPoolSize() + " connexions max");
    }

    private void configureMySQLPool(HikariConfig config) {
        String host = plugin.getConfig().getString("database.mysql.host", "localhost");
        int port = plugin.getConfig().getInt("database.mysql.port", 3306);
        String database = plugin.getConfig().getString("database.mysql.database", "minecraft");
        String user = plugin.getConfig().getString("database.mysql.user", "root");
        String password = plugin.getConfig().getString("database.mysql.password", "");

        String url = String.format("jdbc:mysql://%s:%d/%s", host, port, database);

        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");

        // Optimisations MySQL spécifiques
        config.addDataSourceProperty("useServerPrepStmts", true);
        config.addDataSourceProperty("prepStmtCacheSize", 250);
        config.addDataSourceProperty("prepStmtCacheSqlLimit", 2048);
        config.addDataSourceProperty("cachePrepStmts", true);
        config.addDataSourceProperty("useLocalSessionState", true);
        config.addDataSourceProperty("rewriteBatchedStatements", true);
        config.addDataSourceProperty("cacheResultSetMetadata", true);
        config.addDataSourceProperty("cacheServerConfiguration", true);
        config.addDataSourceProperty("elideSetAutoCommits", true);
        config.addDataSourceProperty("maintainTimeStats", false);
        config.addDataSourceProperty("useSSL", false);
        config.addDataSourceProperty("allowPublicKeyRetrieval", true);
    }

    private void configureSQLitePool(HikariConfig config) {
        String dbPath = plugin.getDataFolder() + "/data.db";
        config.setJdbcUrl("jdbc:sqlite:" + dbPath);
        config.setDriverClassName("org.sqlite.JDBC");

        // SQLite optimisations
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "NORMAL");
        config.addDataSourceProperty("cache_size", "10000");
        config.addDataSourceProperty("temp_store", "memory");
    }

    private void configureHighPerformanceSettings(HikariConfig config) {
        // Pool sizing pour haute charge (centaines de joueurs)
        int cores = Runtime.getRuntime().availableProcessors();
        int maxPoolSize = Math.max(20, cores * 4); // Minimum 20, optimal cores*4
        int minIdle = Math.max(5, cores); // Minimum 5 connexions idle

        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(minIdle);

        // Timeouts optimisés pour haute performance
        config.setConnectionTimeout(5000);        // 5s max pour obtenir une connexion
        config.setIdleTimeout(300000);           // 5min idle timeout
        config.setMaxLifetime(1800000);          // 30min max lifetime
        config.setLeakDetectionThreshold(10000); // 10s leak detection

        // Pool name pour monitoring
        config.setPoolName("PlayerSyncPool");

        // Validation query rapide
        config.setConnectionTestQuery("SELECT 1");

        // Thread factory optimisé
        config.setThreadFactory(r -> {
            Thread thread = new Thread(r, "PlayerSync-DB-" + System.currentTimeMillis());
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY + 1); // Priorité légèrement élevée
            return thread;
        });
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Connection pool is not initialized or closed");
        }
        return dataSource.getConnection();
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            plugin.getLogger().info("Fermeture du pool de connexions...");
            dataSource.close();
        }
    }

    public int getActiveConnections() {
        return dataSource != null ? dataSource.getHikariPoolMXBean().getActiveConnections() : 0;
    }

    public int getIdleConnections() {
        return dataSource != null ? dataSource.getHikariPoolMXBean().getIdleConnections() : 0;
    }

    public int getTotalConnections() {
        return dataSource != null ? dataSource.getHikariPoolMXBean().getTotalConnections() : 0;
    }
}
