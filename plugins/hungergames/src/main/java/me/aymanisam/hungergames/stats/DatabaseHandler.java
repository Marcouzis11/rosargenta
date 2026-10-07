package me.aymanisam.hungergames.stats;

import me.aymanisam.hungergames.HungerGames;
import me.aymanisam.hungergames.handlers.ConfigHandler;
import org.bukkit.entity.Player;


import java.sql.*;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.UUID;
import java.util.Properties;
import java.util.logging.Level;

import static me.aymanisam.hungergames.HungerGames.leaderboards;

public class DatabaseHandler {
    private final HungerGames plugin;
    private final ConfigHandler configHandler;

    private Connection connection;
    private String ensuredMonth;

    public DatabaseHandler (HungerGames plugin) {
        this.plugin = plugin;
        this.configHandler = plugin.getConfigHandler();
    }

    public synchronized Connection getConnection() throws SQLException {
        if (connection != null && !connection.isClosed() && connection.isValid(2)) {
            return connection;
        }

        String url = configHandler.getPluginSettings().getString("database.url");
        String user = configHandler.getPluginSettings().getString("database.user");
        String password = configHandler.getPluginSettings().getString("database.password");

        if (url == null || url.isBlank()) throw new SQLException("database.url must be configured");
        // Bukkit gives each plugin its own class loader; explicitly load our bundled JDBC driver.
        try {
            Class.forName("com.mysql.cj.jdbc.Driver", true, getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC driver is missing from the HungerGames JAR", e);
        }
        if (connection != null) connection.close();
        Properties properties = new Properties();
        if (user != null) properties.setProperty("user", user);
        if (password != null) properties.setProperty("password", password);
        properties.setProperty("connectTimeout", "5000");
        properties.setProperty("socketTimeout", "10000");
        this.connection = DriverManager.getConnection(url, properties);
        ensuredMonth = null;

        plugin.getLogger().log(Level.CONFIG, "Connected to HungerGames Database");

        return this.connection;
    }

    public synchronized void initializeDatabase() throws SQLException {
        try (Statement statement = getConnection().createStatement()) {
            String sql = "CREATE TABLE IF NOT EXISTS player_stats(uuid char(36) primary key, username varchar(16), deaths int, kills int, killAssists int, soloGamesStarted int, soloGamesPlayed int, soloGamesWon int, teamGamesStarted int, teamGamesPlayed int, teamGamesWon int, chestsOpened int, supplyDropsOpened int, environmentDeaths int, borderDeaths int, playerDeaths int, arrowsShot int, arrowsLanded int, fireworksShot int, fireworksLanded int, attacksBlocked int, potionsUsed int, foodConsumed int, totemsPopped int, damageDealt double, projectileDamageDealt double, damageTaken double, projectileDamageTaken double, healthRegenerated double, soloPercentile double, teamPercentile double, lastLogin DATE, lastLogout DATE, secondsPlayed int)";
            statement.execute(sql);

            sql = "CREATE TABLE IF NOT EXISTS player_monthly_playtime(uuid char(36) primary key, username varchar(16))";
            statement.execute(sql);

        }
        ensureCurrentMonth();

        plugin.getLogger().log(Level.CONFIG, "Created the stats table in the database.");
    }

    public synchronized PlayerStatsHandler getPlayerStatsFromDatabase(Player player) throws SQLException {
        return getPlayerStatsFromDatabase(player.getUniqueId().toString(), player.getName());
    }

    public synchronized PlayerStatsHandler getPlayerStatsFromDatabase(String uuid, String name) throws SQLException {
        PlayerStatsHandler stats = findPlayerStatsByUUID(uuid);

        if (stats == null) {
            stats = new PlayerStatsHandler(uuid, name, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,0,0, 0.0, 0.0, 0.0, 0.0, 50.0, 50.0, new java.util.Date(), new java.util.Date(), 0L, 0L);
            createPlayerStats(stats);
        }

        return stats;
    }

    public synchronized PlayerStatsHandler findPlayerStatsByUUID(String uuid) throws SQLException {
        ensureCurrentMonth();
        String monthYear = currentMonth();
        try (PreparedStatement statement = getConnection().prepareStatement("SELECT * FROM player_stats WHERE uuid = ?");
        PreparedStatement monthStatement = getConnection().prepareStatement(
        "SELECT " + monthYear + " FROM player_monthly_playtime WHERE uuid = ?")) {
            statement.setString(1, uuid);
            monthStatement.setString(1, uuid);
            try (ResultSet results = statement.executeQuery(); ResultSet monthResults = monthStatement.executeQuery()) {
                if (results.next()) {
                    String username = results.getString("username");
                    int deaths = results.getInt("deaths");
                    int kills = results.getInt("kills");
                    int killAssists = results.getInt("killAssists");
                    int soloGamesStarted = results.getInt("soloGamesStarted");
                    int soloGamesPlayed = results.getInt("soloGamesPlayed");
                    int soloGamesWon = results.getInt("soloGamesWon");
                    int teamGamesStarted = results.getInt("teamGamesStarted");
                    int teamGamesPlayed = results.getInt("teamGamesPlayed");
                    int teamGamesWon = results.getInt("teamGamesWon");
                    int chestsOpened = results.getInt("chestsOpened");
                    int supplyDropsOpened = results.getInt("supplyDropsOpened");
                    int environmentDeaths = results.getInt("environmentDeaths");
                    int borderDeaths = results.getInt("borderDeaths");
                    int playerDeaths = results.getInt("playerDeaths");
                    int arrowsShot= results.getInt("arrowsShot");
                    int arrowsLanded = results.getInt("arrowsLanded");
                    int fireworksShot = results.getInt("fireworksShot");
                    int fireworksLanded = results.getInt("fireworksLanded");
                    int attacksBlocked = results.getInt("attacksBlocked");
                    int potionsUsed = results.getInt("potionsUsed");
                    int foodConsumed = results.getInt("foodConsumed");
                    int totemsPopped = results.getInt("totemsPopped");
                    double damageDealt = results.getDouble("damageDealt");
                    double projectileDamageDealt = results.getDouble("projectileDamageDealt");
                    double damageTaken = results.getDouble("damageTaken");
                    double projectileDamageTaken = results.getDouble("projectileDamageTaken");
                    double healthRegenerated = results.getDouble("healthRegenerated");
                    double soloPercentile = results.getDouble("soloPercentile");
                    double teamPercentile = results.getDouble("teamPercentile");
                    Date lastLogin = results.getDate("lastLogin");
                    Date lastLogout = results.getDate("lastLogout");
                    long secondsPlayed = results.getLong("secondsPlayed");
                    long secondsPlayedMonth = 0L;
                    if (monthResults.next()) {
                        secondsPlayedMonth = monthResults.getLong(monthYear);
                    }

                    PlayerStatsHandler playerStats = new PlayerStatsHandler(uuid, username, deaths, kills, killAssists, soloGamesStarted, soloGamesPlayed, soloGamesWon, teamGamesStarted, teamGamesPlayed, teamGamesWon, chestsOpened, supplyDropsOpened, environmentDeaths, borderDeaths, playerDeaths, arrowsShot, arrowsLanded, fireworksShot, fireworksLanded, attacksBlocked, potionsUsed, foodConsumed, totemsPopped, damageDealt, projectileDamageDealt, damageTaken, projectileDamageTaken, healthRegenerated, soloPercentile, teamPercentile, lastLogin, lastLogout, secondsPlayed, secondsPlayedMonth);

                    return playerStats;
                } else {
                    return null;
                }
            }
        }
    }

    public synchronized void createPlayerStats(PlayerStatsHandler stats) throws SQLException {
        ensureCurrentMonth();
        try (PreparedStatement statement = getConnection().prepareStatement("INSERT INTO player_stats(uuid, username, deaths, kills, killAssists, soloGamesStarted, soloGamesPlayed, soloGamesWon, teamGamesStarted, teamGamesPlayed, teamGamesWon, chestsOpened, supplyDropsOpened, environmentDeaths, borderDeaths, playerDeaths, arrowsShot, arrowsLanded, fireworksShot, fireworksLanded, attacksBlocked, potionsUsed, foodConsumed, totemsPopped, damageDealt, projectileDamageDealt, damageTaken, projectileDamageTaken, healthRegenerated, soloPercentile, teamPercentile, lastLogin, lastLogout, secondsPlayed) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, stats.getUuid());
            statement.setString(2, stats.getUsername());
            statement.setInt(3, stats.getDeaths());
            statement.setInt(4, stats.getKills());
            statement.setInt(5, stats.getKillAssists());
            statement.setInt(6, stats.getSoloGamesStarted());
            statement.setInt(7, stats.getSoloGamesPlayed());
            statement.setInt(8, stats.getSoloGamesWon());
            statement.setInt(9, stats.getTeamGamesStarted());
            statement.setInt(10, stats.getTeamGamesPlayed());
            statement.setInt(11, stats.getTeamGamesWon());
            statement.setInt(12, stats.getChestsOpened());
            statement.setInt(13, stats.getSupplyDropsOpened());
            statement.setInt(14, stats.getEnvironmentDeaths());
            statement.setInt(15, stats.getBorderDeaths());
            statement.setInt(16, stats.getPlayerDeaths());
            statement.setInt(17, stats.getArrowsShot());
            statement.setInt(18, stats.getArrowsLanded());
            statement.setInt(19, stats.getFireworksShot());
            statement.setInt(20, stats.getFireworksLanded());
            statement.setInt(21, stats.getAttacksBlocked());
            statement.setInt(22, stats.getPotionsUsed());
            statement.setInt(23, stats.getFoodConsumed());
            statement.setInt(24, stats.getTotemsPopped());
            statement.setDouble(25, stats.getDamageDealt());
            statement.setDouble(26, stats.getProjectileDamageDealt());
            statement.setDouble(27, stats.getDamageTaken());
            statement.setDouble(28, stats.getProjectileDamageTaken());
            statement.setDouble(29, stats.getHealthRegenerated());
            statement.setDouble(30, stats.getSoloPercentile());
            statement.setDouble(31, stats.getTeamPercentile());
            statement.setDate(32, new Date(stats.getLastLogin().getTime()));
            statement.setDate(33, new Date(stats.getLastLogout().getTime()));
            statement.setLong(34, stats.getSecondsPlayed());

            statement.executeUpdate();
        }

        String monthYear = currentMonth();
        try (PreparedStatement monthStatement = getConnection().prepareStatement("INSERT INTO player_monthly_playtime (uuid, username, " + monthYear + ") VALUES (?, ?, ?)")) {
            monthStatement.setString(1, stats.getUuid());
            monthStatement.setString(2, stats.getUsername());
            monthStatement.setLong(3, stats.getSecondsPlayedMonth());

            monthStatement.executeUpdate();
        }
    }

    public synchronized void updatePlayerStats(PlayerStatsHandler stats) throws SQLException {
        ensureCurrentMonth();
        try (PreparedStatement statement = getConnection().prepareStatement("UPDATE player_stats SET username = ?, deaths = ?, kills = ?, killAssists = ?, soloGamesStarted = ?, soloGamesPlayed = ?, soloGamesWon = ?, teamGamesStarted = ?, teamGamesPlayed = ?, teamGamesWon = ?, chestsOpened = ?, supplyDropsOpened = ?, environmentDeaths = ?, borderDeaths = ?, playerDeaths = ?, arrowsShot = ?, arrowsLanded = ?, fireworksShot = ?, fireworksLanded = ?, attacksBlocked = ?, potionsUsed = ?, foodConsumed = ?, totemsPopped = ?, damageDealt = ?, projectileDamageDealt = ?, damageTaken = ?, projectileDamageTaken = ?, healthRegenerated = ?, soloPercentile = ?, teamPercentile = ?, lastLogin = ?, lastLogout = ?, secondsPlayed = ? WHERE uuid = ?")) {
            statement.setString(1, stats.getUsername());
            statement.setInt(2, stats.getDeaths());
            statement.setInt(3, stats.getKills());
            statement.setInt(4, stats.getKillAssists());
            statement.setInt(5, stats.getSoloGamesStarted());
            statement.setInt(6, stats.getSoloGamesPlayed());
            statement.setInt(7, stats.getSoloGamesWon());
            statement.setInt(8, stats.getTeamGamesStarted());
            statement.setInt(9, stats.getTeamGamesPlayed());
            statement.setInt(10, stats.getTeamGamesWon());
            statement.setInt(11, stats.getChestsOpened());
            statement.setInt(12, stats.getSupplyDropsOpened());
            statement.setInt(13, stats.getEnvironmentDeaths());
            statement.setInt(14, stats.getBorderDeaths());
            statement.setInt(15, stats.getPlayerDeaths());
            statement.setInt(16, stats.getArrowsShot());
            statement.setInt(17, stats.getArrowsLanded());
            statement.setInt(18, stats.getFireworksShot());
            statement.setInt(19, stats.getFireworksLanded());
            statement.setInt(20, stats.getAttacksBlocked());
            statement.setInt(21, stats.getPotionsUsed());
            statement.setInt(22, stats.getFoodConsumed());
            statement.setInt(23, stats.getTotemsPopped());
            statement.setDouble(24, stats.getDamageDealt());
            statement.setDouble(25, stats.getProjectileDamageDealt());
            statement.setDouble(26, stats.getDamageTaken());
            statement.setDouble(27, stats.getProjectileDamageTaken());
            statement.setDouble(28, stats.getHealthRegenerated());
            statement.setDouble(29, stats.getSoloPercentile());
            statement.setDouble(30, stats.getTeamPercentile());
            statement.setDate(31, new Date(stats.getLastLogin().getTime()));
            statement.setDate(32, new Date(stats.getLastLogout().getTime()));
            statement.setLong(33, stats.getSecondsPlayed());
            statement.setString(34, stats.getUuid());

            statement.executeUpdate();
        }

        String monthYear = currentMonth();
        try (PreparedStatement monthStatement = getConnection().prepareStatement("UPDATE player_monthly_playtime SET " + monthYear + " = ? WHERE uuid = ?")) {
            monthStatement.setLong(1, stats.getSecondsPlayedMonth());
            monthStatement.setString(2, stats.getUuid());

            monthStatement.executeUpdate();
        }
    }

    public synchronized void deletePlayerStats(String uuid) throws SQLException {
        for (String table : new String[]{"player_stats", "player_monthly_playtime"}) {
            try (PreparedStatement statement = getConnection().prepareStatement("DELETE FROM " + table + " WHERE uuid = ?")) {
                statement.setString(1, uuid);
                statement.executeUpdate();
            }
        }
    }

    public synchronized void addMonthColumn(String monthYear) throws SQLException {
        if (!monthYear.matches("[a-z]{3}_[0-9]{4}")) throw new SQLException("Invalid monthly statistics column");
        try (PreparedStatement lookup = getConnection().prepareStatement(
        "SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()"
        + " AND TABLE_NAME = 'player_monthly_playtime' AND COLUMN_NAME = ?")) {
            lookup.setString(1, monthYear);
            try (ResultSet columns = lookup.executeQuery()) {
                if (columns.next()) return;
            }
        }
        try (Statement statement = getConnection().createStatement()) {
            statement.executeUpdate("ALTER TABLE player_monthly_playtime ADD COLUMN " + monthYear + " INT DEFAULT 0");
        }
    }

    private static String currentMonth() {
        LocalDate today = LocalDate.now();
        return today.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toLowerCase(Locale.ROOT) + "_" + today.getYear();
    }

    private void ensureCurrentMonth() throws SQLException {
        getConnection();
        String month = currentMonth();
        if (!month.equals(ensuredMonth)) {
            addMonthColumn(month);
            ensuredMonth = month;
        }
    }

    public synchronized void getPlayerLeaderboards () throws SQLException {
        ensureCurrentMonth();
        String[] stats = {"deaths", "kills", "soloGamesPlayed", "teamGamesPlayed", "soloGamesWon", "teamGamesWon", "secondsPlayed", "secondsPlayedMonth"};

        for (String stat : stats) {
            LinkedHashMap<UUID, Double> leaderboardMap = new LinkedHashMap<>();

            String sql;
            if (!stat.equals("secondsPlayedMonth")) {
                sql = "SELECT uuid, " + stat + " FROM player_stats ORDER BY " + stat + " DESC LIMIT 20";
            } else {
                String columnName = currentMonth();
                sql = "SELECT uuid, " + columnName + " FROM player_monthly_playtime ORDER BY " + columnName + " DESC LIMIT 20";
            }
            try (PreparedStatement statement = getConnection().prepareStatement(sql);
            ResultSet results = statement.executeQuery()) {


                while (results.next()) {
                    leaderboardMap.put(UUID.fromString(results.getString(1)), results.getDouble(2));
                }

                leaderboards.put(stat, leaderboardMap);
            }
        }
    }

    public synchronized void changeSecondsPlayedType() throws SQLException {
        String sql = "SELECT COLUMN_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'player_stats' AND COLUMN_NAME = 'secondsPlayed'";
        try (PreparedStatement statement = getConnection().prepareStatement(sql);
        ResultSet results = statement.executeQuery()) {
            if (!results.next() || results.getString(1).toLowerCase(Locale.ROOT).startsWith("int")) return;
        }
        try (Statement statement = getConnection().createStatement()) {
            statement.executeUpdate("ALTER TABLE player_stats MODIFY secondsPlayed INT NOT NULL DEFAULT 0");
        }
    }

    public synchronized void closeConnection() {
        if (this.connection != null) {
            try {
                this.connection.close();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, e.toString());
            } finally {
                connection = null;
                ensuredMonth = null;
            }
        }
    }
}
