package me.theus.donutLifeSteal.database;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.theus.donutLifeSteal.DonutLifeSteal;
import me.theus.donutLifeSteal.models.HeartData;
import me.theus.donutLifeSteal.models.LifeStealUser;
import org.bson.Document;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.sql.*;
import java.util.UUID;

public class Database {

    private final DonutLifeSteal plugin;
    private HikariDataSource dataSource;
    private MongoClient mongoClient;
    private MongoDatabase mongoDatabase;
    private String databaseType;

    public Database(DonutLifeSteal plugin) {
        this.plugin = plugin;
    }

    public boolean connect() {
        FileConfiguration config = plugin.getDatabaseConfig();
        databaseType = config.getString("DATABASE", "SQLITE").toUpperCase();

        if (databaseType.equals("MONGODB")) {
            try {
                String uri = config.getString("MONGODB.URI", "");
                if (uri != null && !uri.isEmpty()) {
                    mongoClient = MongoClients.create(uri);
                } else {
                    String host = config.getString("MONGODB.ADDRESS", "127.0.0.1");
                    int port = config.getInt("MONGODB.PORT", 27017);

                    MongoClientSettings.Builder settingsBuilder = MongoClientSettings.builder()
                            .applyConnectionString(new ConnectionString("mongodb://" + host + ":" + port));

                    if (config.getBoolean("MONGODB.AUTHENTICATION.ENABLED", false)) {
                        String user = config.getString("MONGODB.AUTHENTICATION.USERNAME");
                        String password = config.getString("MONGODB.AUTHENTICATION.PASSWORD");
                        String authDb = config.getString("MONGODB.AUTHENTICATION.DATABASE");
                        MongoCredential credential = MongoCredential.createCredential(user, authDb, password.toCharArray());
                        settingsBuilder.credential(credential);
                    }
                    mongoClient = MongoClients.create(settingsBuilder.build());
                }
                mongoDatabase = mongoClient.getDatabase(config.getString("MONGODB.DATABASE", "DonutLifeSteal"));
                plugin.getLogger().info("Conectado ao MongoDB com sucesso!");
                return true;
            } catch (Exception e) {
                plugin.getLogger().severe("Erro ao conectar ao MongoDB: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }

        HikariConfig hikariConfig = new HikariConfig();

        if (databaseType.equals("MYSQL")) {
            String host = config.getString("MYSQL.HOST");
            int port = config.getInt("MYSQL.PORT", 3306);
            String database = config.getString("MYSQL.DATABASE");
            String user = config.getString("MYSQL.USER");
            String password = config.getString("MYSQL.PASSWORD");
            String params = config.getString("MYSQL.PARAMS", "useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");

            hikariConfig.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?" + params);
            hikariConfig.setUsername(user);
            hikariConfig.setPassword(password);
            hikariConfig.setMaximumPoolSize(config.getInt("MYSQL.POOL.MAX-POOL-SIZE", 10));
            hikariConfig.setMinimumIdle(config.getInt("MYSQL.POOL.MIN-IDLE", 2));
            hikariConfig.setConnectionTimeout(config.getLong("MYSQL.POOL.CONNECTION-TIMEOUT", 30000));
        } else {
            // SQLite
            String fileStr = config.getString("SQLITE.FILE", "plugins/DonutLifeSteal/donutlifesteal.db");
            File file;
            if (fileStr.startsWith("plugins/DonutLifeSteal/")) {
                file = new File(plugin.getDataFolder(), fileStr.substring("plugins/DonutLifeSteal/".length()));
            } else if (fileStr.startsWith("plugins/")) {
                file = new File(plugin.getDataFolder().getParentFile(), fileStr.substring("plugins/".length()));
            } else {
                file = new File(fileStr);
                if (!file.isAbsolute()) {
                    file = new File(plugin.getDataFolder(), file.getName());
                }
            }

            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }

            hikariConfig.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
            hikariConfig.setDriverClassName("org.sqlite.JDBC");
            hikariConfig.setMaximumPoolSize(5);
            hikariConfig.setConnectionTimeout(30000);
        }

        try {
            dataSource = new HikariDataSource(hikariConfig);
            createTables();
            plugin.getLogger().info("Conectado ao banco de dados (" + databaseType + ") com sucesso!");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Erro ao conectar ao banco de dados (" + databaseType + "): " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void createTables() throws SQLException {
        String usersSql = "CREATE TABLE IF NOT EXISTS donutlifesteal_users (" +
                "uuid VARCHAR(36) PRIMARY KEY," +
                "username VARCHAR(32)," +
                "hearts INT NOT NULL DEFAULT 4," +
                "last_ip VARCHAR(45)," +
                "last_updated BIGINT NOT NULL DEFAULT 0" +
                ");";

        String heartsSql = "CREATE TABLE IF NOT EXISTS donutlifesteal_hearts (" +
                "id VARCHAR(36) PRIMARY KEY," +
                "creator_uuid VARCHAR(36)," +
                "created_at BIGINT NOT NULL DEFAULT 0," +
                "status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'," +
                "claimed_by VARCHAR(36)," +
                "claimed_at BIGINT NOT NULL DEFAULT 0" +
                ");";

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            if ("SQLITE".equals(databaseType)) {
                try {
                    stmt.execute("PRAGMA journal_mode=WAL;");
                    stmt.execute("PRAGMA busy_timeout=5000;");
                } catch (SQLException ignored) {}
            }
            stmt.execute(usersSql);
            stmt.execute(heartsSql);
        }
    }

    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
        if (mongoClient != null) {
            mongoClient.close();
        }
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public LifeStealUser loadUser(UUID uuid, String username, int defaultHearts) {
        LifeStealUser user = new LifeStealUser(uuid, username, defaultHearts);

        if ("MONGODB".equals(databaseType)) {
            MongoCollection<Document> collection = mongoDatabase.getCollection("donutlifesteal_users");
            Document doc = collection.find(Filters.eq("uuid", uuid.toString())).first();
            if (doc != null) {
                user.setUsername(doc.getString("username"));
                user.setHearts(doc.getInteger("hearts", defaultHearts));
                user.setLastIp(doc.getString("last_ip"));
                user.setLastUpdated(doc.getLong("last_updated"));
            } else {
                saveUser(user);
            }
            return user;
        }

        String sql = "SELECT * FROM donutlifesteal_users WHERE uuid = ?";
        boolean exists = false;
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    user.setUsername(rs.getString("username"));
                    user.setHearts(rs.getInt("hearts"));
                    user.setLastIp(rs.getString("last_ip"));
                    user.setLastUpdated(rs.getLong("last_updated"));
                    exists = true;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        if (!exists) {
            saveUser(user);
        }
        return user;
    }

    public void saveUser(LifeStealUser user) {
        if (user == null || user.getUuid() == null) return;

        if ("MONGODB".equals(databaseType)) {
            MongoCollection<Document> collection = mongoDatabase.getCollection("donutlifesteal_users");
            Document doc = new Document("uuid", user.getUuid().toString())
                    .append("username", user.getUsername())
                    .append("hearts", user.getHearts())
                    .append("last_ip", user.getLastIp())
                    .append("last_updated", user.getLastUpdated());
            collection.replaceOne(Filters.eq("uuid", user.getUuid().toString()), doc, new ReplaceOptions().upsert(true));
            return;
        }

        String sql;
        if ("MYSQL".equals(databaseType)) {
            sql = "INSERT INTO donutlifesteal_users (uuid, username, hearts, last_ip, last_updated) VALUES (?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE username = VALUES(username), hearts = VALUES(hearts), last_ip = VALUES(last_ip), last_updated = VALUES(last_updated)";
        } else {
            // SQLite
            sql = "INSERT OR REPLACE INTO donutlifesteal_users (uuid, username, hearts, last_ip, last_updated) VALUES (?, ?, ?, ?, ?)";
        }

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getUuid().toString());
            ps.setString(2, user.getUsername() != null ? user.getUsername() : "");
            ps.setInt(3, user.getHearts());
            ps.setString(4, user.getLastIp() != null ? user.getLastIp() : "");
            ps.setLong(5, user.getLastUpdated());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void registerHeart(HeartData heart) {
        if (heart == null || heart.getHeartId() == null) return;

        if ("MONGODB".equals(databaseType)) {
            MongoCollection<Document> collection = mongoDatabase.getCollection("donutlifesteal_hearts");
            Document doc = new Document("id", heart.getHeartId().toString())
                    .append("creator_uuid", heart.getCreatorUuid() != null ? heart.getCreatorUuid().toString() : "")
                    .append("created_at", heart.getCreatedAt())
                    .append("status", heart.getStatus())
                    .append("claimed_by", heart.getClaimedBy() != null ? heart.getClaimedBy().toString() : "")
                    .append("claimed_at", heart.getClaimedAt());
            collection.replaceOne(Filters.eq("id", heart.getHeartId().toString()), doc, new ReplaceOptions().upsert(true));
            return;
        }

        String sql;
        if ("MYSQL".equals(databaseType)) {
            sql = "INSERT INTO donutlifesteal_hearts (id, creator_uuid, created_at, status, claimed_by, claimed_at) VALUES (?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE status = VALUES(status), claimed_by = VALUES(claimed_by), claimed_at = VALUES(claimed_at)";
        } else {
            sql = "INSERT OR REPLACE INTO donutlifesteal_hearts (id, creator_uuid, created_at, status, claimed_by, claimed_at) VALUES (?, ?, ?, ?, ?, ?)";
        }

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, heart.getHeartId().toString());
            ps.setString(2, heart.getCreatorUuid() != null ? heart.getCreatorUuid().toString() : "");
            ps.setLong(3, heart.getCreatedAt());
            ps.setString(4, heart.getStatus());
            ps.setString(5, heart.getClaimedBy() != null ? heart.getClaimedBy().toString() : "");
            ps.setLong(6, heart.getClaimedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public HeartData loadHeart(UUID heartId) {
        if (heartId == null) return null;

        if ("MONGODB".equals(databaseType)) {
            MongoCollection<Document> collection = mongoDatabase.getCollection("donutlifesteal_hearts");
            Document doc = collection.find(Filters.eq("id", heartId.toString())).first();
            if (doc != null) {
                UUID creator = doc.getString("creator_uuid") != null && !doc.getString("creator_uuid").isEmpty() ? UUID.fromString(doc.getString("creator_uuid")) : null;
                UUID claimed = doc.getString("claimed_by") != null && !doc.getString("claimed_by").isEmpty() ? UUID.fromString(doc.getString("claimed_by")) : null;
                return new HeartData(heartId, creator, doc.getLong("created_at"), doc.getString("status"), claimed, doc.getLong("claimed_at"));
            }
            return null;
        }

        String sql = "SELECT * FROM donutlifesteal_hearts WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, heartId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String creatorStr = rs.getString("creator_uuid");
                    UUID creator = creatorStr != null && !creatorStr.isEmpty() ? UUID.fromString(creatorStr) : null;
                    String claimedStr = rs.getString("claimed_by");
                    UUID claimed = claimedStr != null && !claimedStr.isEmpty() ? UUID.fromString(claimedStr) : null;
                    return new HeartData(
                            heartId,
                            creator,
                            rs.getLong("created_at"),
                            rs.getString("status"),
                            claimed,
                            rs.getLong("claimed_at")
                    );
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void updateHeartStatus(UUID heartId, String status, UUID claimedBy, long claimedAt) {
        if (heartId == null) return;

        if ("MONGODB".equals(databaseType)) {
            MongoCollection<Document> collection = mongoDatabase.getCollection("donutlifesteal_hearts");
            collection.updateOne(Filters.eq("id", heartId.toString()),
                    new Document("$set", new Document("status", status)
                            .append("claimed_by", claimedBy != null ? claimedBy.toString() : "")
                            .append("claimed_at", claimedAt)));
            return;
        }

        String sql = "UPDATE donutlifesteal_hearts SET status = ?, claimed_by = ?, claimed_at = ? WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, claimedBy != null ? claimedBy.toString() : "");
            ps.setLong(3, claimedAt);
            ps.setString(4, heartId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
