package me.aymanisam.hungergames.stats;

import me.aymanisam.hungergames.HungerGames;
import me.aymanisam.hungergames.handlers.ConfigHandler;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Date;
import java.util.Locale;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DatabaseCompatibilityTest {
    private HungerGames plugin() {
        HungerGames plugin = mock(HungerGames.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("hg-db-test"));
        ConfigHandler config = mock(ConfigHandler.class);
        when(config.getPluginSettings()).thenReturn(new YamlConfiguration());
        when(plugin.getConfigHandler()).thenReturn(config);
        return plugin;
    }

    @Test void enabledSettingDoesNotEnableStatisticsBeforeDatabaseInitializationSucceeds() {
        HungerGames plugin = plugin();
        plugin.getConfigHandler().getPluginSettings().set("database.enabled", true);
        doCallRealMethod().when(plugin).isDatabaseEnabled();
        assertFalse(plugin.isDatabaseEnabled());
    }

    @Test void emptyUrlReportsConfigurationError() {
        DatabaseHandler database = new DatabaseHandler(plugin());
        assertTrue(assertThrows(SQLException.class, database::getConnection).getMessage().contains("database.url"));
    }

    @Test void existingMonthlyColumnIsKeptAndResourcesAreClosed() throws Exception {
        DatabaseHandler database = spy(new DatabaseHandler(plugin()));
        Connection connection = mock(Connection.class);
        doReturn(connection).when(database).getConnection();
        PreparedStatement lookup = mock(PreparedStatement.class);
        ResultSet columns = mock(ResultSet.class);
        when(connection.prepareStatement(contains("TABLE_SCHEMA = DATABASE()"))).thenReturn(lookup);
        when(lookup.executeQuery()).thenReturn(columns);
        when(columns.next()).thenReturn(true);
        database.addMonthColumn("oct_2026");
        verify(lookup).setString(1, "oct_2026");
        verify(connection, never()).createStatement();
        verify(lookup).close(); verify(columns).close();
        assertThrows(SQLException.class, () -> database.addMonthColumn("bad;column"));
    }

    @Test void firstPlayerCreatesMonthlyColumnBeforeInsertingPlaytime() throws Exception {
        DatabaseHandler database = spy(new DatabaseHandler(plugin()));
        Connection connection = mock(Connection.class);
        doReturn(connection).when(database).getConnection();
        PreparedStatement lookup = mock(PreparedStatement.class), insert = mock(PreparedStatement.class);
        ResultSet columns = mock(ResultSet.class);
        Statement alter = mock(Statement.class);
        when(connection.prepareStatement(anyString())).thenAnswer(call ->
                call.getArgument(0, String.class).contains("INFORMATION_SCHEMA") ? lookup : insert);
        when(lookup.executeQuery()).thenReturn(columns);
        when(connection.createStatement()).thenReturn(alter);
        PlayerStatsHandler stats = mock(PlayerStatsHandler.class);
        when(stats.getLastLogin()).thenReturn(new Date()); when(stats.getLastLogout()).thenReturn(new Date());
        database.createPlayerStats(stats);
        String month = LocalDate.now().getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toLowerCase(Locale.ROOT)
                + "_" + LocalDate.now().getYear();
        var order = inOrder(alter, insert);
        order.verify(alter).executeUpdate("ALTER TABLE player_monthly_playtime ADD COLUMN " + month + " INT DEFAULT 0");
        order.verify(insert).executeUpdate();
        verify(insert, times(2)).close();
        verify(alter).close();
    }

    @Test void closingConnectionAllowsANewConnectionAttempt() throws Exception {
        DatabaseHandler database = new DatabaseHandler(plugin());
        Connection connection = mock(Connection.class);
        var field = DatabaseHandler.class.getDeclaredField("connection");
        field.setAccessible(true); field.set(database, connection);
        database.closeConnection();
        verify(connection).close();
        assertThrows(SQLException.class, database::getConnection);
    }
}
