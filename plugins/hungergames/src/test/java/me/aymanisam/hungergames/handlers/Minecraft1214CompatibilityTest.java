package me.aymanisam.hungergames.handlers;

import me.aymanisam.hungergames.HungerGames;
import me.aymanisam.hungergames.commands.JoinGameCommand;
import me.aymanisam.hungergames.listeners.PlayerListener;
import org.bukkit.*;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class Minecraft1214CompatibilityTest {
    @TempDir Path temp;
    private HungerGames plugin;
    private ConfigHandler configs;
    private LangHandler lang;
    private World arena;
    private YamlConfiguration worldConfig;

    @BeforeEach void setup() {
        HungerGames.gameStarting.clear();
        HungerGames.gameStarted.clear();
        HungerGames.hgWorldNames.clear();
        GameSequenceHandler.playersAlive.clear();
        GameSequenceHandler.playerPlacements.clear();
        TeamsHandler.teamsAlive.clear();
        TeamsHandler.teams.clear();
        SignHandler.signLocations.clear();
        plugin = mock(HungerGames.class);
        configs = mock(ConfigHandler.class);
        lang = mock(LangHandler.class);
        worldConfig = new YamlConfiguration();
        arena = mock(World.class);
        when(arena.getName()).thenReturn("arena");
        when(arena.getSpawnLocation()).thenReturn(new Location(arena, 0, 64, 0));
        when(plugin.getConfigHandler()).thenReturn(configs);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("hg-1214-test"));
        when(plugin.getDataFolder()).thenReturn(temp.toFile());
        when(plugin.getServer()).thenReturn(Bukkit.getServer());
        when(configs.getWorldConfig(arena)).thenReturn(worldConfig);
        when(configs.getPluginSettings()).thenReturn(new YamlConfiguration());
        SetSpawnHandler.spawnPointMap = new HashMap<>();
    }

    @AfterEach void cleanup() {
        HungerGames.hgWorldNames.clear();
        HungerGames.gameStarting.clear();
        HungerGames.gameStarted.clear();
        SetSpawnHandler.spawnPointMap.clear();
        GameSequenceHandler.playersAlive.clear();
        SetSpawnHandler.autoStartTasks.clear();
    }

    @Test void everyBundledLootEntryBuildsOn1214IncludingPotionsBooksAndFireworks() throws Exception {
        YamlConfiguration loot = new YamlConfiguration();
        try (var reader = new java.io.InputStreamReader(Objects.requireNonNull(
                getClass().getResourceAsStream("/items.yml")), java.nio.charset.StandardCharsets.UTF_8)) {
            loot.load(reader);
        }
        ChestRefillHandler handler = new ChestRefillHandler(plugin, lang);
        int entries = 0;
        for (String key : loot.getKeys(false)) {
            for (Map<?, ?> entry : loot.getMapList(key)) {
                assertDoesNotThrow(() -> handler.createItem(entry, new Random(31)), key + ": " + entry);
                entries++;
            }
        }
        assertTrue(entries > 100);
    }

    @Test void legacyPotionsKeepTheirLevelAndDurationInMigratedYaml() {
        assertEquals(PotionType.STRONG_SWIFTNESS, ChestRefillHandler.resolvePotionType("SPEED", false, 2));
        assertEquals(PotionType.LONG_LEAPING, ChestRefillHandler.resolvePotionType("JUMP", true, 1));
        assertEquals(PotionType.STRONG_HEALING, ChestRefillHandler.resolvePotionType("INSTANT_HEAL", false, 2));
        assertEquals(PotionType.HARMING, ChestRefillHandler.resolvePotionType("INSTANT_DAMAGE", false, 1));
        assertEquals(PotionType.LONG_REGENERATION, ChestRefillHandler.resolvePotionType("REGEN", true, 1));
        assertEquals(org.bukkit.potion.PotionEffectType.RESISTANCE, PotionCompatibilityHandler.resolveEffectType("DAMAGE_RESISTANCE"));
        assertEquals(org.bukkit.potion.PotionEffectType.STRENGTH, PotionCompatibilityHandler.resolveEffectType("INCREASE_DAMAGE"));
        assertEquals(org.bukkit.potion.PotionEffectType.JUMP_BOOST, PotionCompatibilityHandler.resolveEffectType("JUMP"));
        var item = new ChestRefillHandler(plugin, lang).createItem(
                Map.of("type", "SPLASH_POTION", "potion-type", "SPEED", "level", 2), new Random(5));
        assertEquals(PotionType.STRONG_SWIFTNESS, ((PotionMeta) item.getItemMeta()).getBasePotionType());
    }

    @Test void oneInvalidLootEntryDoesNotPreventValidLootInSameChest() {
        Inventory inventory = inventoryWithLoot(List.of(
                Map.of("type", "POTION", "potion-type", "INVALID"),
                Map.of("type", "STONE_SWORD", "weight", 4)));
        verify(inventory).clear();
        verify(inventory, times(2)).setItem(anyInt(), argThat(item -> item.getType() == Material.STONE_SWORD));
    }

    @Test void entirelyInvalidLootDoesNotEraseExistingContents() {
        Inventory inventory = inventoryWithLoot(List.of(Map.of("type", "POTION", "potion-type", "INVALID")));
        verify(inventory, never()).clear();
        verify(inventory, never()).setItem(anyInt(), any());
    }

    private Inventory inventoryWithLoot(List<Map<String, Object>> entries) {
        Chest chest = mock(Chest.class);
        Inventory inventory = mock(Inventory.class);
        when(inventory.getSize()).thenReturn(27);
        Map<Integer, org.bukkit.inventory.ItemStack> contents = new HashMap<>();
        when(inventory.getItem(anyInt())).thenAnswer(call -> contents.get(call.getArgument(0)));
        doAnswer(call -> { contents.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(inventory).setItem(anyInt(), any());
        when(chest.getInventory()).thenReturn(inventory);
        Location entry = mock(Location.class);
        org.bukkit.block.Block block = mock(org.bukkit.block.Block.class);
        when(entry.getBlock()).thenReturn(block);
        when(block.getType()).thenReturn(Material.CHEST);
        when(block.getState()).thenReturn(chest);
        YamlConfiguration loot = new YamlConfiguration();
        loot.set("chest-items", entries);
        new ChestRefillHandler(plugin, lang).refillInventory(List.of(entry), "chest-items", loot, 2, 2, new Random(5));
        return inventory;
    }

    @Test void cancelledTeleportDoesNotReserveSpawnOrFreezePlayer() {
        SetSpawnHandler spawns = spy(new SetSpawnHandler(plugin, lang));
        Player player = player(UUID.randomUUID());
        doReturn("arena,0,64,0").when(spawns).assignPlayerToSpawnPoint(player, arena);
        when(player.teleport(any(Location.class))).thenReturn(false);
        assertFalse(spawns.teleportPlayerToSpawnpoint(player, arena));
        assertTrue(SetSpawnHandler.spawnPointMap.get("arena").isEmpty());
        assertTrue(spawns.playersWaiting.get("arena").isEmpty());
        verify(player, never()).setGameMode(any());
    }

    @Test void successfulJoinRegistersOnlyAfterResetAndTeleport() {
        SetSpawnHandler spawns = spy(new SetSpawnHandler(plugin, lang));
        Player player = player(UUID.randomUUID());
        when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
        when(player.getAttribute(any())).thenReturn(mock(AttributeInstance.class));
        when(player.teleport(any(Location.class))).thenAnswer(call -> {
            assertTrue(SetSpawnHandler.spawnPointMap.get("arena").isEmpty());
            return true;
        });
        doReturn("arena,0,64,0").when(spawns).assignPlayerToSpawnPoint(player, arena);
        assertTrue(spawns.teleportPlayerToSpawnpoint(player, arena));
        assertTrue(JoinGameCommand.isPlayerInGame(player));
        assertEquals(List.of(player), spawns.playersWaiting.get("arena"));
    }

    @Test void disconnectDuringCountdownClearsReservationAndAllowsReconnect() {
        HungerGames.hgWorldNames.add("arena");
        HungerGames.gameStarting.put("arena", true);
        UUID uuid = UUID.randomUUID();
        Player oldSession = player(uuid), newSession = player(uuid);
        SetSpawnHandler spawns = new SetSpawnHandler(plugin, lang);
        spawns.setCountDownHandler(mock(CountDownHandler.class));
        SetSpawnHandler.spawnPointMap.put("arena", new HashMap<>(Map.of("arena,0,64,0", oldSession)));
        spawns.playersWaiting.put("arena", new ArrayList<>(List.of(oldSession)));
        GameSequenceHandler.playersAlive.put("arena", new ArrayList<>(List.of(oldSession)));
        PlayerQuitEvent event = mock(PlayerQuitEvent.class);
        when(event.getPlayer()).thenReturn(oldSession);
        new PlayerListener(plugin, lang, spawns).onPlayerQuit(event);
        assertTrue(SetSpawnHandler.spawnPointMap.get("arena").isEmpty());
        assertTrue(spawns.playersWaiting.get("arena").isEmpty());
        assertTrue(GameSequenceHandler.playersAlive.get("arena").isEmpty());
        assertFalse(JoinGameCommand.isPlayerInGame(newSession));
    }

    @Test void reservationsAreRemovedByUuidAcrossPlayerObjects() {
        SetSpawnHandler spawns = new SetSpawnHandler(plugin, lang);
        UUID uuid = UUID.randomUUID();
        Player first = player(uuid), reconnect = player(uuid);
        SetSpawnHandler.spawnPointMap.put("arena", new HashMap<>(Map.of("arena,0,64,0", first)));
        spawns.playersWaiting.put("arena", new ArrayList<>(List.of(first)));
        spawns.removePlayerFromSpawnPoint(reconnect, arena);
        assertTrue(SetSpawnHandler.spawnPointMap.get("arena").isEmpty());
        assertTrue(spawns.playersWaiting.get("arena").isEmpty());
    }

    @Test void staleMatchInAnotherWorldDoesNotBlockJoiningFromLobby() {
        Player player = player(UUID.randomUUID());
        World lobby = mock(World.class);
        when(lobby.getName()).thenReturn("lobby");
        when(player.getWorld()).thenReturn(lobby);
        GameSequenceHandler.playersAlive.put("arena", new ArrayList<>(List.of(player)));
        SetSpawnHandler.spawnPointMap.put("arena", new HashMap<>(Map.of("arena,0,64,0", player)));
        assertFalse(JoinGameCommand.isPlayerInGame(player));
    }

    @Test void scoreboardShowsTimeWithMissingPreviousMatchDataAndZeroIntervals() {
        worldConfig.set("game-time", 600);
        Player player = player(UUID.randomUUID());
        org.bukkit.WorldBorder border = mock(org.bukkit.WorldBorder.class);
        when(arena.getWorldBorder()).thenReturn(border);
        when(border.getSize()).thenReturn(100.0);
        when(lang.getMessage(any(), anyString(), any(Object[].class))).thenAnswer(call ->
                call.getArgument(1, String.class) + " " + Arrays.toString(Arrays.copyOfRange(call.getArguments(), 2, call.getArguments().length)));
        GameSequenceHandler.timeLeft.remove("arena");
        GameSequenceHandler.startingPlayers.remove("arena");
        List<String> lines = assertDoesNotThrow(() -> new ScoreBoardHandler(plugin, lang).linesFor(player, arena));
        assertTrue(lines.stream().anyMatch(line -> line.contains("score.time") && line.contains("10:00")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("score.chestrefill") && line.contains("00:00")));
    }

    @Test void endingMatchClearsSpawnsWaitingTeamsAndPendingAutostart() {
        HungerGames.hgWorldNames.add("arena");
        HungerGames.gameStarted.put("arena", true);
        SetSpawnHandler spawns = new SetSpawnHandler(plugin, lang);
        Player player = player(UUID.randomUUID());
        SetSpawnHandler.spawnPointMap.put("arena", new HashMap<>(Map.of("arena,0,64,0", player)));
        spawns.playersWaiting.put("arena", new ArrayList<>(List.of(player)));
        GameSequenceHandler.playersAlive.put("arena", new ArrayList<>(List.of(player)));
        TeamsHandler.teamsAlive.put("arena", new ArrayList<>(List.of(new ArrayList<>(List.of(player)))));
        org.bukkit.scheduler.BukkitTask task = mock(org.bukkit.scheduler.BukkitTask.class);
        SetSpawnHandler.autoStartTasks.put("arena", new ArrayList<>(List.of(task)));
        GameSequenceHandler game = new GameSequenceHandler(plugin, lang, spawns,
                mock(me.aymanisam.hungergames.listeners.CompassListener.class), mock(TeamsHandler.class));
        game.endGame(true, arena);
        assertFalse(HungerGames.gameStarted.get("arena"));
        assertTrue(SetSpawnHandler.spawnPointMap.get("arena").isEmpty());
        assertTrue(spawns.playersWaiting.get("arena").isEmpty());
        assertTrue(GameSequenceHandler.playersAlive.get("arena").isEmpty());
        assertFalse(TeamsHandler.teamsAlive.containsKey("arena"));
        assertFalse(SetSpawnHandler.autoStartTasks.containsKey("arena"));
        verify(task).cancel();
    }

    @Test void failedStartupRollsBackFlagsAndReservations() {
        HungerGames.hgWorldNames.add("arena");
        HungerGames.gameStarting.put("arena", true);
        SetSpawnHandler spawns = new SetSpawnHandler(plugin, lang);
        Player player = player(UUID.randomUUID());
        SetSpawnHandler.spawnPointMap.put("arena", new HashMap<>(Map.of("arena,0,64,0", player)));
        GameSequenceHandler.playersAlive.put("arena", new ArrayList<>(List.of(player)));
        doThrow(new IllegalStateException("startup failure")).doNothing().when(arena).setPVP(false);
        try (var validation = mockConstruction(ArenaValidationHandler.class,
                (mock, context) -> when(mock.validate(any(), anyCollection(), anyInt())).thenReturn(List.of()))) {
            GameSequenceHandler game = new GameSequenceHandler(plugin, lang, spawns,
                    mock(me.aymanisam.hungergames.listeners.CompassListener.class), mock(TeamsHandler.class));
            assertDoesNotThrow(() -> game.startGame(arena));
        }
        assertFalse(HungerGames.gameStarted.get("arena"));
        assertFalse(HungerGames.gameStarting.get("arena"));
        assertTrue(SetSpawnHandler.spawnPointMap.get("arena").isEmpty());
        assertTrue(GameSequenceHandler.playersAlive.get("arena").isEmpty());
    }

    private Player player(UUID uuid) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getWorld()).thenReturn(arena);
        when(player.getGameMode()).thenReturn(GameMode.ADVENTURE);
        return player;
    }
}
