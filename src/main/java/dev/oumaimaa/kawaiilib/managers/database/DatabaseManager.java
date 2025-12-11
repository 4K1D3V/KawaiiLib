package dev.oumaimaa.kawaiilib.managers.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.oumaimaa.kawaiilib.Bootstrap;
import dev.oumaimaa.kawaiilib.annotations.Database;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

public final class DatabaseManager {

    private final Bootstrap plugin;
    private final HikariDataSource dataSource;
    private final ExecutorService executor;

    public DatabaseManager(Bootstrap plugin, @NotNull Database config) {
        this.plugin = plugin;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();

        HikariConfig hikariConfig = new HikariConfig();

        switch (config.type().toUpperCase()) {
            case "MYSQL" -> {
                hikariConfig.setJdbcUrl(config.url());
                hikariConfig.setUsername(config.user());
                hikariConfig.setPassword(config.password());
                hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");

                // MySQL optimizations
                hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
                hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
                hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
                hikariConfig.addDataSourceProperty("useLocalSessionState", "true");
                hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");
                hikariConfig.addDataSourceProperty("cacheResultSetMetadata", "true");
                hikariConfig.addDataSourceProperty("cacheServerConfiguration", "true");
                hikariConfig.addDataSourceProperty("elideSetAutoCommits", "true");
                hikariConfig.addDataSourceProperty("maintainTimeStats", "false");
            }
            case "SQLITE" -> {
                hikariConfig.setJdbcUrl("jdbc:sqlite:" + plugin.getDataFolder() + "/data.db");
                hikariConfig.setDriverClassName("org.sqlite.JDBC");
                hikariConfig.setMaximumPoolSize(1); // SQLite only supports one writer
            }
            case "H2" -> {
                hikariConfig.setJdbcUrl("jdbc:h2:" + plugin.getDataFolder() + "/data");
                hikariConfig.setDriverClassName("org.h2.Driver");
            }
            default -> throw new IllegalArgumentException("Unsupported database type: " + config.type());
        }

        // Common HikariCP settings
        hikariConfig.setPoolName("KawaiiLib-Pool");
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setMaxLifetime(1800000); // 30 minutes
        hikariConfig.setConnectionTimeout(5000);
        hikariConfig.setLeakDetectionThreshold(60000);

        this.dataSource = new HikariDataSource(hikariConfig);

        plugin.getLogger().info("Database initialized: " + config.type());
    }

    /**
     * Get a connection from the pool
     */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Execute an async query
     */
    @Contract("_, _ -> new")
    public @NotNull CompletableFuture<Void> executeAsync(@NotNull String sql, Object... params) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                setParameters(stmt, params);
                stmt.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().severe("Error executing query: " + e.getMessage());
                e.printStackTrace();
            }
        }, executor);
    }

    /**
     * Execute an async query with result processing
     */
    @Contract("_, _, _ -> new")
    public <T> @NotNull CompletableFuture<T> queryAsync(@NotNull String sql,
                                                        @NotNull Function<ResultSet, T> processor,
                                                        Object... params) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                setParameters(stmt, params);

                try (ResultSet rs = stmt.executeQuery()) {
                    return processor.apply(rs);
                }

            } catch (SQLException e) {
                plugin.getLogger().severe("Error executing query: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        }, executor);
    }

    /**
     * Execute a batch update
     */
    @Contract("_, _ -> new")
    public @NotNull CompletableFuture<int[]> executeBatchAsync(@NotNull String sql,
                                                               @NotNull Iterable<Object[]> paramsList) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                conn.setAutoCommit(false);

                for (Object[] params : paramsList) {
                    setParameters(stmt, params);
                    stmt.addBatch();
                }

                int[] results = stmt.executeBatch();
                conn.commit();

                return results;

            } catch (SQLException e) {
                plugin.getLogger().severe("Error executing batch: " + e.getMessage());
                e.printStackTrace();
                return new int[0];
            }
        }, executor);
    }

    /**
     * Create a table if it doesn't exist
     */
    public @NotNull CompletableFuture<Void> createTableAsync(@NotNull String tableName, @NotNull String schema) {
        String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" + schema + ")";
        return executeAsync(sql);
    }

    /**
     * Check if a table exists
     */
    @Contract("_ -> new")
    public @NotNull CompletableFuture<Boolean> tableExists(@NotNull String tableName) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 ResultSet rs = conn.getMetaData().getTables(null, null, tableName, null)) {
                return rs.next();
            } catch (SQLException e) {
                plugin.getLogger().severe("Error checking table existence: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    private void setParameters(@NotNull PreparedStatement stmt, Object @NotNull ... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            stmt.setObject(i + 1, params[i]);
        }
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Database connection closed");
        }

        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}