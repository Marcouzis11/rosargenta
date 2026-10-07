package me.aymanisam.hungergames.handlers;

import me.aymanisam.hungergames.HungerGames;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StartupCompatibilityTest {
    @TempDir Path temp;
    private HungerGames plugin;
    private ConfigHandler config;
    private YamlConfiguration settings;

    @BeforeEach void setup() throws Exception {
        plugin = mock(HungerGames.class);
        when(plugin.getDataFolder()).thenReturn(temp.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("hg-test"));
        when(plugin.getResource(anyString())).thenAnswer(call ->
                getClass().getResourceAsStream("/" + call.getArgument(0, String.class)));
        config = new ConfigHandler(plugin);
        when(plugin.getConfigHandler()).thenReturn(config);
        settings = new YamlConfiguration();
        settings.set("default-language", "en_us");
        settings.set("lobby-world", "lobby");
        settings.save(temp.resolve("settings.yml").toFile());
        config.createPluginSettings();

        Path jar = temp.resolve("plugin.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            for (String locale : List.of("en_us", "es_es")) {
                String resource = "lang/" + locale + ".yml";
                out.putNextEntry(new JarEntry(resource));
                try (InputStream in = plugin.getResource(resource)) { in.transferTo(out); }
                out.closeEntry();
            }
        }
        when(plugin.getPluginFile()).thenReturn(jar.toFile());
        doAnswer(call -> {
            String resource = call.getArgument(0);
            Path target = temp.resolve(resource);
            Files.createDirectories(target.getParent());
            try (InputStream in = plugin.getResource(resource)) { Files.copy(in, target); }
            return null;
        }).when(plugin).saveResource(anyString(), eq(false));
    }

    @AfterEach void cleanup() {
        HungerGames.worldNames.clear();
        HungerGames.hgWorldNames.clear();
        SignHandler.signLocations.clear();
        me.aymanisam.hungergames.commands.SignSetCommand.slots.clear();
    }

    @Test void findsWorldsInServerContainerAndReloadDoesNotDuplicateOrRestoreLobby() throws Exception {
        Path container = Files.createDirectory(temp.resolve("worlds"));
        Files.writeString(Files.createDirectory(container.resolve("MapaHG")).resolve("level.dat"), "world");
        Files.writeString(Files.createDirectory(container.resolve(".MapaHG-hg-previous")).resolve("level.dat"), "backup");
        Server server = mock(Server.class);
        World lobby = mock(World.class), external = mock(World.class);
        when(lobby.getName()).thenReturn("lobby");
        when(external.getName()).thenReturn("external");
        when(server.getWorldContainer()).thenReturn(container.toFile());
        when(server.getWorlds()).thenReturn(List.of(lobby, external));
        when(plugin.getServer()).thenReturn(server);
        doCallRealMethod().when(plugin).loadWorldFiles();
        plugin.loadWorldFiles();
        plugin.loadWorldFiles();
        assertEquals(List.of("MapaHG", "external", "lobby"), HungerGames.worldNames);
        assertEquals(List.of("MapaHG", "external"), HungerGames.hgWorldNames);
        config.getPluginSettings().set("whitelist-worlds", true);
        config.getPluginSettings().set("ignored-worlds", List.of("MapaHG", "lobby"));
        plugin.loadWorldFiles();
        assertEquals(List.of("MapaHG"), HungerGames.hgWorldNames);
    }

    @Test void firstLanguageLoadExtractsAndReadsNewFiles() {
        LangHandler lang = new LangHandler(plugin);
        assertEquals("You do not have permission to run this command", org.bukkit.ChatColor.stripColor(lang.getMessage(null, "no-permission")));
        assertTrue(Files.isRegularFile(temp.resolve("lang/en_us.yml")));
    }

    @Test void migratesLegacyLanguageBeforeBundledFilesCanMaskIt() throws Exception {
        Files.createDirectories(temp.resolve("lang"));
        Files.writeString(temp.resolve("lang/ES_ES.YML"), "no-permission: 'Traducción personalizada'\n");
        LangHandler lang = new LangHandler(plugin);
        lang.loadLanguageConfigs();
        Player player = mock(Player.class);
        when(player.getLocale()).thenReturn("ES-es");
        assertEquals("Traducción personalizada", lang.getMessage(player, "no-permission"));
        assertFalse(Files.exists(temp.resolve("lang/ES_ES.YML")));
    }

    @Test void conflictingLanguageNamesKeepBothCustomFilesAndUseCanonicalName() throws Exception {
        Files.createDirectories(temp.resolve("lang"));
        Files.writeString(temp.resolve("lang/es_es.yml"), "no-permission: 'actual'\n");
        Files.writeString(temp.resolve("lang/ES_ES.yml"), "no-permission: 'anterior'\n");
        LangHandler lang = new LangHandler(plugin);
        lang.loadLanguageConfigs();
        Player player = mock(Player.class);
        when(player.getLocale()).thenReturn("es_es");
        assertEquals("actual", lang.getMessage(player, "no-permission"));
        assertTrue(Files.readString(temp.resolve("lang/ES_ES.yml")).contains("anterior"));
    }

    @Test void unknownDefaultAndClientLocaleFallBackWithoutNullMessages() {
        config.getPluginSettings().set("default-language", "custom_missing");
        LangHandler lang = new LangHandler(plugin);
        Player player = mock(Player.class);
        when(player.getLocale()).thenReturn("ja_jp");
        assertFalse(lang.getMessage(player, "no-permission").contains("Missing translation"));
    }

    @Test void legacyConfiguredDefaultAndTurkishSystemLocaleUseStableKeys() {
        Locale old = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            config.getPluginSettings().set("default-language", "ES-ES");
            LangHandler lang = new LangHandler(plugin);
            Player player = mock(Player.class);
            when(player.getLocale()).thenReturn("zz_zz");
            assertSame(lang.getLangConfig(), lang.getLangConfig(player));
            assertFalse(lang.getMessage(player, "no-permission").contains("Missing translation"));
        } finally { Locale.setDefault(old); }
    }

    @Test void languageValidationUsesEachLanguagesOwnDefaultsAndReloadRefreshesCache() throws Exception {
        LangHandler lang = new LangHandler(plugin);
        lang.loadLanguageConfigs();
        Path spanish = temp.resolve("lang/es_es.yml");
        Files.writeString(spanish, "no-permission: 'personalizado'\n");
        lang.validateLanguageKeys();
        try (InputStream in = plugin.getResource("lang/es_es.yml")) {
            var defaults = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            assertEquals(defaults.getString("no-server"), YamlConfiguration.loadConfiguration(spanish.toFile()).getString("no-server"));
        }
        lang.loadLanguageConfigs();
        Player player = mock(Player.class);
        when(player.getLocale()).thenReturn("es_es");
        assertEquals("personalizado", lang.getMessage(player, "no-permission"));
    }

    @Test void creatingArenaConfigsDoesNotOverwriteRootConfigurationOrLoot() throws Exception {
        Files.writeString(temp.resolve("config.yml"), "game-time: 123\n");
        Files.writeString(temp.resolve("items.yml"), "custom: preservado\n");
        World world = mock(World.class);
        when(world.getName()).thenReturn("MapaHG");
        config.createWorldConfig(world);
        config.loadItemsConfig(world);
        assertEquals("game-time: 123\n", Files.readString(temp.resolve("config.yml")));
        assertEquals("custom: preservado\n", Files.readString(temp.resolve("items.yml")));
        verify(plugin, never()).saveResource(anyString(), eq(true));
    }

    @Test void malformedSettingsAreReportedWithoutRewritingTheFile() throws Exception {
        String invalid = "database: [unterminated\n";
        Files.writeString(temp.resolve("settings.yml"), invalid);
        assertThrows(IllegalStateException.class, config::validateSettingsKeys);
        assertEquals(invalid, Files.readString(temp.resolve("settings.yml")));
    }

    @Test void validatingKeysRefreshesCachedDefaultsAndKeepsCustomValues() throws Exception {
        World world = mock(World.class);
        when(world.getName()).thenReturn("MapaHG");
        Files.createDirectory(temp.resolve("MapaHG"));
        Files.writeString(temp.resolve("MapaHG/config.yml"), "game-time: 123\n");
        config.createWorldConfig(world);
        config.validateConfigKeys(world);
        assertEquals(123, config.getWorldConfig(world).getInt("game-time"));
        assertEquals(30, config.getWorldConfig(world).getInt("deathmatch.border-size"));
    }

    @Test void badSignsAndUnloadedWorldsDoNotPreventOtherSignsLoading() throws Exception {
        YamlConfiguration signs = new YamlConfiguration();
        signs.set("signs", List.of("old,1,2,3", "missing,1,2,3,unloaded", "lobby,NaN,2,3,bad", "lobby,1,2,3,valid"));
        signs.set("slots", List.of("broken", "valid,MapaHG"));
        signs.save(temp.resolve("signs.yml").toFile());
        World world = mock(World.class);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("lobby")).thenReturn(world);
            assertDoesNotThrow(config::loadSignLocations);
            assertDoesNotThrow(config::loadSlots);
            assertEquals(1, SignHandler.signLocations.size());
            assertEquals(world, SignHandler.signLocations.get("valid").getWorld());
            assertEquals("MapaHG", me.aymanisam.hungergames.commands.SignSetCommand.slots.get("valid"));
            signs.set("signs", List.of()); signs.set("slots", List.of());
            signs.save(temp.resolve("signs.yml").toFile());
            config.loadSignLocations(); config.loadSlots();
            assertTrue(SignHandler.signLocations.isEmpty());
            assertTrue(me.aymanisam.hungergames.commands.SignSetCommand.slots.isEmpty());
        }
    }

    @Test void consoleCommandsRejectAnUnloadedArenaWithoutThrowing() {
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        HungerGames.hgWorldNames.add("MapaHG");
        var sender = mock(org.bukkit.command.CommandSender.class);
        LangHandler lang = mock(LangHandler.class);
        var executors = List.of(
                new me.aymanisam.hungergames.commands.StartGameCommand(plugin, lang, mock(SetSpawnHandler.class), mock(CountDownHandler.class)),
                new me.aymanisam.hungergames.commands.EndGameCommand(plugin, lang, mock(GameSequenceHandler.class), mock(CountDownHandler.class)),
                new me.aymanisam.hungergames.commands.SaveWorldCommand(plugin, lang, mock(WorldBorderHandler.class)),
                new me.aymanisam.hungergames.commands.SupplyDropCommand(plugin, lang),
                new me.aymanisam.hungergames.commands.ArenaScanCommand(plugin, lang),
                new me.aymanisam.hungergames.commands.ChestRefillCommand(plugin, lang),
                new me.aymanisam.hungergames.commands.ReloadConfigCommand(plugin, lang));
        for (var executor : executors) {
            assertDoesNotThrow(() -> executor.onCommand(sender, mock(org.bukkit.command.Command.class), "hg", new String[]{"MapaHG"}));
        }
        assertDoesNotThrow(() -> new me.aymanisam.hungergames.commands.BorderSetCommand(plugin, lang)
                .onCommand(sender, mock(org.bukkit.command.Command.class), "hg", new String[]{"MapaHG", "100", "0", "0"}));
    }

    @Test void consoleBorderAcceptsWorldAndThreeCoordinates() throws Exception {
        Server server = mock(Server.class);
        World world = mock(World.class);
        var border = mock(org.bukkit.WorldBorder.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getWorld("MapaHG")).thenReturn(world);
        when(world.getName()).thenReturn("MapaHG");
        when(world.getWorldBorder()).thenReturn(border);
        HungerGames.hgWorldNames.add("MapaHG");
        Files.createDirectory(temp.resolve("MapaHG"));
        Files.writeString(temp.resolve("MapaHG/arena.yml"), "region:\n  world: MapaHG\n");
        var sender = mock(org.bukkit.command.CommandSender.class);
        assertTrue(new me.aymanisam.hungergames.commands.BorderSetCommand(plugin, mock(LangHandler.class))
                .onCommand(sender, mock(org.bukkit.command.Command.class), "hg", new String[]{"MapaHG", "100", "5", "10"}));
        verify(border).setSize(100);
        verify(border).setCenter(5, 10);
        assertEquals(100, config.getWorldConfig(world).getInt("border.size"));
    }

    @Test void chestEventsDuringAsynchronousStatsLoadingDoNotThrow() {
        when(plugin.isDatabaseEnabled()).thenReturn(true);
        World world = mock(World.class);
        when(world.getName()).thenReturn("MapaHG");
        Player player = mock(Player.class);
        when(player.getWorld()).thenReturn(world);
        when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        var event = mock(org.bukkit.event.player.PlayerInteractEvent.class);
        var block = mock(org.bukkit.block.Block.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getClickedBlock()).thenReturn(block);
        when(block.getType()).thenReturn(org.bukkit.Material.CHEST);
        when(event.getAction()).thenReturn(org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK);
        var listener = new me.aymanisam.hungergames.listeners.PlayerListener(plugin, mock(LangHandler.class), mock(SetSpawnHandler.class));
        assertDoesNotThrow(() -> listener.onChestOpen(event));
    }

    @Test void soloCompassFindsNearestEnemyWithoutATeam() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("solo-compass");
        Player player = mock(Player.class), near = mock(Player.class), far = mock(Player.class);
        when(world.getPlayers()).thenReturn(List.of(player, far, near));
        for (Player target : List.of(player, near, far)) {
            when(target.getWorld()).thenReturn(world);
            when(target.isOnline()).thenReturn(true);
            when(target.getGameMode()).thenReturn(org.bukkit.GameMode.ADVENTURE);
        }
        when(player.getLocation()).thenReturn(new org.bukkit.Location(world, 0, 64, 0));
        when(near.getLocation()).thenReturn(new org.bukkit.Location(world, 5, 64, 0));
        when(far.getLocation()).thenReturn(new org.bukkit.Location(world, 50, 64, 0));
        assertSame(near, new CompassHandler(mock(LangHandler.class)).findNearestEnemy(player, false, world));
        TeamsHandler.teams.remove("solo-compass");
    }

    @Test void deathStatisticsBelongToVictimKillerAndAssistingPlayer() throws Exception {
        World world = mock(World.class);
        when(world.getName()).thenReturn("stats-arena");
        Player victim = mock(Player.class), killer = mock(Player.class), assister = mock(Player.class);
        var victimStats = mock(me.aymanisam.hungergames.stats.PlayerStatsHandler.class);
        var killerStats = mock(me.aymanisam.hungergames.stats.PlayerStatsHandler.class);
        var assistStats = mock(me.aymanisam.hungergames.stats.PlayerStatsHandler.class);
        List<Player> players = List.of(victim, killer, assister);
        var records = List.of(victimStats, killerStats, assistStats);
        for (int i = 0; i < players.size(); i++) {
            java.util.UUID id = java.util.UUID.randomUUID();
            when(players.get(i).getUniqueId()).thenReturn(id);
            when(players.get(i).getWorld()).thenReturn(world);
            HungerGames.statsMap.put(id, records.get(i));
        }
        try {
            when(plugin.isDatabaseEnabled()).thenReturn(true);
            when(victim.getKiller()).thenReturn(killer);
            when(victim.getLocation()).thenReturn(new org.bukkit.Location(world, 0, 64, 0));
            var inventory = mock(org.bukkit.inventory.PlayerInventory.class);
            when(victim.getInventory()).thenReturn(inventory);
            when(inventory.getContents()).thenReturn(new org.bukkit.inventory.ItemStack[0]);
            var spawns = mock(SetSpawnHandler.class);
            spawns.playersWaiting = new java.util.HashMap<>();
            var listener = new me.aymanisam.hungergames.listeners.PlayerListener(plugin, mock(LangHandler.class), spawns);
            var damagersField = listener.getClass().getDeclaredField("playerDamagers");
            damagersField.setAccessible(true);
            @SuppressWarnings("unchecked")
            var damagers = (java.util.Map<Player, java.util.Set<Player>>) damagersField.get(listener);
            damagers.put(victim, new java.util.HashSet<>(List.of(killer, assister)));
            HungerGames.gameStarted.put("stats-arena", true);
            GameSequenceHandler.playersAlive.put("stats-arena", new java.util.ArrayList<>(players));
            var death = mock(org.bukkit.event.entity.PlayerDeathEvent.class);
            when(death.getEntity()).thenReturn(victim);
            assertDoesNotThrow(() -> listener.onPlayerDeath(death));
            verify(victimStats).setDeaths(1);
            verify(killerStats).setKills(1);
            verify(assistStats).setKillAssists(1);
            verify(victimStats, never()).setKills(anyInt());
            verify(victimStats, never()).setKillAssists(anyInt());
        } finally {
            players.forEach(player -> HungerGames.statsMap.remove(player.getUniqueId()));
            HungerGames.gameStarted.remove("stats-arena");
            GameSequenceHandler.playersAlive.remove("stats-arena");
            GameSequenceHandler.playerPlacements.remove("stats-arena");
            GameSequenceHandler.playerBossBars.remove("stats-arena");
            TeamsHandler.teams.remove("stats-arena"); TeamsHandler.teamsAlive.remove("stats-arena");
            GameSequenceHandler.teamPlacements.remove("stats-arena");
            me.aymanisam.hungergames.listeners.PlayerListener.playerKills.remove("stats-arena");
        }
    }
}
