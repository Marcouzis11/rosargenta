package me.aymanisam.hungergames.handlers;

import me.aymanisam.hungergames.HungerGames;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeathmatchTest {
    @TempDir Path tempDir;
    private HungerGames plugin;
    private World world;
    private WorldBorder border;
    private BukkitScheduler scheduler;
    private YamlConfiguration config;
    private GameSequenceHandler game;
    private Player first;
    private Player second;

    @BeforeEach
    void setUp() throws Exception {
        HungerGames.gameStarted.clear();
        GameSequenceHandler.deathmatchWorlds.clear();
        GameSequenceHandler.celebratingWorlds.clear();
        GameSequenceHandler.preparingDeathmatchWorlds.clear();
        GameSequenceHandler.playersAlive.clear();
        GameSequenceHandler.playerBossBars.clear();
        GameSequenceHandler.playerPlacements.clear();
        GameSequenceHandler.teamPlacements.clear();
        TeamsHandler.teamsAlive.clear();
        CountDownHandler.playersPerTeam = 1;
        plugin = mock(HungerGames.class);
        Server server = mock(Server.class);
        scheduler = mock(BukkitScheduler.class);
        ConfigHandler configs = mock(ConfigHandler.class);
        config = new YamlConfiguration();
        config.set("game-time", 1);
        config.set("deathmatch.protection-seconds", 0);
        when(plugin.getConfigHandler()).thenReturn(configs);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(configs.getPluginSettings()).thenReturn(new YamlConfiguration());
        world = mock(World.class);
        border = mock(WorldBorder.class);
        when(world.getName()).thenReturn("arena");
        when(world.getWorldBorder()).thenReturn(border);
        when(configs.getWorldConfig(world)).thenReturn(config);
        when(border.getSize()).thenReturn(40.0);
        when(border.getCenter()).thenReturn(new Location(world, 0, 0, 0));
        first = mock(Player.class);
        second = mock(Player.class);
        when(first.getUniqueId()).thenReturn(UUID.randomUUID());
        when(second.getUniqueId()).thenReturn(UUID.randomUUID());
        when(first.isOnline()).thenReturn(true);
        when(second.isOnline()).thenReturn(true);
        when(first.getWorld()).thenReturn(world);
        when(second.getWorld()).thenReturn(world);
        when(world.getPlayers()).thenReturn(List.of(first, second));
        when(first.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(second.getLocation()).thenReturn(new Location(world, 1, 64, 0));
        LangHandler lang = mock(LangHandler.class);
        game = spy(new GameSequenceHandler(plugin, lang, mock(SetSpawnHandler.class),
                mock(me.aymanisam.hungergames.listeners.CompassListener.class), mock(TeamsHandler.class)));
        doNothing().when(game).endGame(anyBoolean(), eq(world));
        HungerGames.gameStarted.put("arena", true);
        GameSequenceHandler.playersAlive.put("arena", new ArrayList<>(List.of(first, second)));
        var field = GameSequenceHandler.class.getDeclaredField("matchSpawns");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Map<UUID, Location>> spawns = (Map<String, Map<UUID, Location>>) field.get(game);
        spawns.put("arena", Map.of(first.getUniqueId(), new Location(world, -10, 65, 0),
                second.getUniqueId(), new Location(world, 10, 65, 0)));
    }

    private Runnable startTimer() {
        game.mainGame(world);
        ArgumentCaptor<Runnable> timer = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleSyncRepeatingTask(eq(plugin), timer.capture(), eq(20L), eq(20L));
        return timer.getValue();
    }

    @Test
    void deathmatchWaitsFiveSecondsBeforeEnablingCombat() {
        config.set("deathmatch.protection-seconds", 5);
        BukkitTask task = mock(BukkitTask.class);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(20L), eq(20L))).thenReturn(task);
        startTimer().run();
        assertTrue(GameSequenceHandler.preparingDeathmatchWorlds.contains("arena"));
        verify(world).setPVP(false);
        verify(world, never()).setPVP(true);
        ArgumentCaptor<Runnable> countdown = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskTimer(eq(plugin), countdown.capture(), eq(20L), eq(20L));
        for (int i = 0; i < 4; i++) countdown.getValue().run();
        verify(world, never()).setPVP(true);
        countdown.getValue().run();
        verify(world).setPVP(true);
        verify(task).cancel();
        assertFalse(GameSequenceHandler.preparingDeathmatchWorlds.contains("arena"));
    }

    @Test
    void timeoutTeleportsSurvivorsOnceAndKeepsMatchRunning() {
        config.set("display-bossbar", true);
        BossBar bossBar = mock(BossBar.class);
        GameSequenceHandler.playerBossBars.put("arena", new HashMap<>(Map.of(first, bossBar)));
        BukkitTask pendingGrace = mock(BukkitTask.class);
        game.gracePeriodTaskId.put("arena", 17);
        game.chestRefillTask.put("arena", pendingGrace);
        Runnable timer = startTimer();
        timer.run();
        timer.run();
        assertTrue(GameSequenceHandler.deathmatchWorlds.contains("arena"));
        verify(first, times(1)).teleport(new Location(world, -10, 65, 0));
        verify(second, times(1)).teleport(new Location(world, 10, 65, 0));
        verify(world).setPVP(true);
        verify(scheduler).cancelTask(17);
        verify(pendingGrace).cancel();
        verify(game, never()).endGame(anyBoolean(), any());
        verify(border).setSize(30.0);
        verify(border, never()).setSize(anyDouble(), anyLong());
        verify(bossBar, atLeastOnce()).setColor(BarColor.RED);
        verify(bossBar, never()).setProgress(doubleThat(value -> value < 0.0 || value > 1.0));
    }

    @Test
    void remainingSurvivorWinsInsteadOfStartingDeathmatch() {
        Runnable timer = startTimer();
        GameSequenceHandler.playersAlive.get("arena").remove(second);
        timer.run();
        assertFalse(GameSequenceHandler.deathmatchWorlds.contains("arena"));
        verify(game, never()).endGame(anyBoolean(), any());
        assertTrue(GameSequenceHandler.celebratingWorlds.contains("arena"));
        ArgumentCaptor<Runnable> finish = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskLater(eq(plugin), finish.capture(), eq(200L));
        timer.run();
        assertEquals(1, GameSequenceHandler.playerPlacements.get("arena").size());
        finish.getValue().run();
        verify(game).endGame(false, world);
        verify(first, never()).teleport(any(Location.class));
    }

    @Test
    void teamsContinueFightingAfterTimeoutAndFinishWithOneTeam() {
        CountDownHandler.playersPerTeam = 2;
        config.set("players-per-team", 2);
        List<List<Player>> alive = new ArrayList<>(List.of(List.of(first), List.of(second)));
        TeamsHandler.teamsAlive.put("arena", alive);
        Runnable timer = startTimer();
        timer.run();
        verify(game, never()).endGame(anyBoolean(), any());
        alive.remove(1);
        timer.run();
        verify(game, never()).endGame(anyBoolean(), any());
        ArgumentCaptor<Runnable> finish = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskLater(eq(plugin), finish.capture(), eq(200L));
        finish.getValue().run();
        verify(game).endGame(false, world);
    }

    @Test
    void celebrationFireworksFollowTheWinner() {
        org.bukkit.entity.Firework rocket = mock(org.bukkit.entity.Firework.class);
        when(rocket.getFireworkMeta()).thenReturn(mock(org.bukkit.inventory.meta.FireworkMeta.class));
        when(world.spawn(any(Location.class), eq(org.bukkit.entity.Firework.class))).thenReturn(rocket);
        Runnable timer = startTimer();
        GameSequenceHandler.playersAlive.get("arena").remove(second);
        timer.run();
        ArgumentCaptor<Runnable> fireworks = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskTimer(eq(plugin), fireworks.capture(), eq(0L), eq(20L));
        when(first.getLocation()).thenReturn(new Location(world, 7, 65, 9));
        fireworks.getValue().run();
        verify(world).spawn(new Location(world, 7, 66, 9), org.bukkit.entity.Firework.class);
        verify(rocket).setFireworkMeta(any());
    }

    @Test
    void fixedBorderIncludesAllOffCenterPlatforms() {
        config.set("deathmatch.border-size", 12);
        config.set("deathmatch.shrink-seconds", 8);
        new WorldBorderHandler(plugin, mock(LangHandler.class)).startDeathmatchBorder(world,
                List.of(new Location(world, 100, 65, -80), new Location(world, 160, 65, -40)));
        verify(border).setCenter(130, -60);
        verify(border).setSize(64.0);
        verify(border, never()).setSize(anyDouble(), anyLong());
    }

    @Test
    void normalBorderStaysFixedEvenWithLegacyShrinkConfiguration() {
        config.set("border.size", 400);
        config.set("border.start-time", 1);
        config.set("border.end-time", 2);
        config.set("border.final-size", 10);
        new WorldBorderHandler(plugin, mock(LangHandler.class)).startWorldBorder(world);
        verify(border).setSize(400.0);
        verify(border, never()).setSize(anyDouble(), anyLong());
        verifyNoInteractions(scheduler);
    }
}
