package me.aymanisam.hungergames.handlers;

import me.aymanisam.hungergames.HungerGames;
import me.aymanisam.hungergames.listeners.PlayerListener;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.*;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RespawnTest {
    @TempDir Path tempDir;
    private HungerGames plugin;
    private World arena, lobby;
    private Player player;
    private PlayerListener listener;
    private PlayerRespawnEvent event;
    private BukkitScheduler scheduler;
    private YamlConfiguration settings;

    @BeforeEach
    void setUp() throws Exception {
        HungerGames.gameStarted.clear();
        HungerGames.hgWorldNames.clear();
        GameSequenceHandler.celebratingWorlds.clear();
        plugin = mock(HungerGames.class);
        Server server = mock(Server.class);
        ConfigHandler configs = mock(ConfigHandler.class);
        scheduler = mock(BukkitScheduler.class);
        settings = new YamlConfiguration();
        settings.set("lobby-world", "lobby");
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(plugin.getConfigHandler()).thenReturn(configs);
        when(configs.getPluginSettings()).thenReturn(settings);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        arena = mock(World.class);
        lobby = mock(World.class);
        when(arena.getName()).thenReturn("arena");
        when(lobby.getName()).thenReturn("lobby");
        when(server.getWorld("lobby")).thenReturn(lobby);
        when(lobby.getSpawnLocation()).thenReturn(new Location(lobby, 0, 64, 0));
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(arena);
        when(player.isOnline()).thenReturn(true);
        when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
        when(player.getAttribute(Attribute.MAX_HEALTH)).thenReturn(mock(AttributeInstance.class));
        event = mock(PlayerRespawnEvent.class);
        when(event.getPlayer()).thenReturn(player);
        listener = new PlayerListener(plugin, mock(LangHandler.class), mock(SetSpawnHandler.class));
        var field = PlayerListener.class.getDeclaredField("respawnArenas");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, String> pending = (Map<UUID, String>) field.get(listener);
        pending.put(player.getUniqueId(), "arena");
    }

    @Test
    void eliminatedPlayerRespawnsInLobbyWhenSpectatingDisabled() {
        HungerGames.gameStarted.put("arena", true);
        listener.onPlayerRespawn(event);
        verify(event).setRespawnLocation(lobby.getSpawnLocation());
        ArgumentCaptor<Runnable> reset = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTask(eq(plugin), reset.capture());
        reset.getValue().run();
        verify(player).setGameMode(GameMode.ADVENTURE);
        verify(player).teleport(lobby.getSpawnLocation());
    }

    @Test
    void matchEndingBeforeRespawnDoesNotSendPlayerBackToArena() {
        settings.set("spectating", true);
        when(player.getWorld()).thenReturn(lobby);
        listener.onPlayerRespawn(event);
        verify(event).setRespawnLocation(lobby.getSpawnLocation());
    }

    @Test
    void spectatorsCanStillStayForAnActiveMatchIfEnabled() throws Exception {
        settings.set("spectating", true);
        HungerGames.gameStarted.put("arena", true);
        var field = PlayerListener.class.getDeclaredField("deathLocations");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Map<Player, Location>> deaths = (Map<String, Map<Player, Location>>) field.get(listener);
        Location death = new Location(arena, 20, 64, 20);
        deaths.put("arena", new HashMap<>(Map.of(player, death)));
        listener.onPlayerRespawn(event);
        verify(event).setRespawnLocation(death);
        ArgumentCaptor<Runnable> spectate = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTask(eq(plugin), spectate.capture());
        spectate.getValue().run();
        verify(player).setGameMode(GameMode.SPECTATOR);
    }

    @Test
    void livingPlayersCanCraftWithATableInAdventureMode() {
        HungerGames.gameStarted.put("arena", true);
        GameSequenceHandler.playersAlive.put("arena", new ArrayList<>(List.of(player)));
        org.bukkit.event.player.PlayerInteractEvent interact = mock(org.bukkit.event.player.PlayerInteractEvent.class);
        when(interact.getPlayer()).thenReturn(player);
        when(interact.getAction()).thenReturn(org.bukkit.event.block.Action.RIGHT_CLICK_AIR);
        when(interact.getItem()).thenReturn(new org.bukkit.inventory.ItemStack(Material.CRAFTING_TABLE));
        listener.onPortableCrafting(interact);
        verify(interact).setCancelled(true);
        verify(player).openWorkbench(null, true);
    }

    @Test
    void arenaPlacementIsBlockedEvenInSurvivalButOtherWorldsAndCreativeAreUnaffected() {
        HungerGames.hgWorldNames.add("arena");
        org.bukkit.event.block.BlockPlaceEvent placement = mock(org.bukkit.event.block.BlockPlaceEvent.class);
        org.bukkit.block.Block block = mock(org.bukkit.block.Block.class);
        when(placement.getPlayer()).thenReturn(player);
        when(placement.getBlock()).thenReturn(block);
        when(block.getWorld()).thenReturn(arena);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        listener.onBlockPlace(placement);
        verify(placement).setCancelled(true);

        clearInvocations(placement);
        when(block.getWorld()).thenReturn(lobby);
        listener.onBlockPlace(placement);
        verify(placement, never()).setCancelled(anyBoolean());

        when(block.getWorld()).thenReturn(arena);
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        listener.onBlockPlace(placement);
        verify(placement, never()).setCancelled(anyBoolean());
    }

    @Test
    void celebrationDamageCannotKillTheWinner() {
        GameSequenceHandler.celebratingWorlds.add("arena");
        org.bukkit.event.entity.EntityDamageEvent damage = mock(org.bukkit.event.entity.EntityDamageEvent.class);
        when(damage.getEntity()).thenReturn(player);
        listener.onCelebrationDamage(damage);
        verify(damage).setCancelled(true);
    }

    @Test
    void eliminatedPlayersStaySpectatorsDuringTheCelebration() throws Exception {
        settings.set("spectating", true);
        HungerGames.gameStarted.put("arena", true);
        GameSequenceHandler.celebratingWorlds.add("arena");
        var field = PlayerListener.class.getDeclaredField("deathLocations");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Map<Player, Location>> deaths = (Map<String, Map<Player, Location>>) field.get(listener);
        Location death = new Location(arena, 20, 64, 20);
        deaths.put("arena", new HashMap<>(Map.of(player, death)));
        listener.onPlayerRespawn(event);
        verify(event).setRespawnLocation(death);
        verify(event, never()).setRespawnLocation(lobby.getSpawnLocation());
    }
}
