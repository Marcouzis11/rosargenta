package me.aymanisam.hungergames.handlers;

import me.aymanisam.hungergames.HungerGames;
import me.aymanisam.hungergames.commands.ArenaCreateCommand;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArenaHeightTest {
    @TempDir Path temp;

    private HungerGames plugin() {
        HungerGames plugin = mock(HungerGames.class);
        when(plugin.getDataFolder()).thenReturn(temp.toFile());
        when(plugin.getConfigHandler()).thenReturn(mock(ConfigHandler.class));
        when(plugin.getArenaMobListener()).thenReturn(mock(me.aymanisam.hungergames.listeners.ArenaMobListener.class));
        return plugin;
    }
    private World world(int min, int max) {
        World world = mock(World.class);
        when(world.getName()).thenReturn("arena");
        when(world.getMinHeight()).thenReturn(min);
        when(world.getMaxHeight()).thenReturn(max);
        return world;
    }
    private Path savedRegion() throws Exception {
        Path file = Files.createDirectories(temp.resolve("arena")).resolve("arena.yml");
        var region = new YamlConfiguration();
        region.set("region.world", "arena");
        region.set("region.pos1.x", -30); region.set("region.pos1.y", 10); region.set("region.pos1.z", 40);
        region.set("region.pos2.x", 50); region.set("region.pos2.y", 90); region.set("region.pos2.z", -60);
        region.save(file.toFile());
        return file;
    }
    @ParameterizedTest
    @CsvSource({"-64,320", "0,256", "-128,512"})
    void newSelectionsIgnoreClickHeightsAndRespectEachWorldBuildLimits(int min, int max) throws Exception {
        Path file = savedRegion();
        HungerGames plugin = plugin(); World world = world(min, max);
        Player player = mock(Player.class);
        when(player.getWorld()).thenReturn(world);
        when(player.hasPermission("hungergames.create")).thenReturn(true);
        when(player.hasMetadata("arena_pos1")).thenReturn(true);
        when(player.hasMetadata("arena_pos2")).thenReturn(true);
        MetadataValue first = mock(MetadataValue.class), second = mock(MetadataValue.class);
        when(first.value()).thenReturn(new Location(world, -30, 72, 40));
        when(second.value()).thenReturn(new Location(world, 50, 180, -60));
        when(player.getMetadata("arena_pos1")).thenReturn(List.of(first));
        when(player.getMetadata("arena_pos2")).thenReturn(List.of(second));
        new ArenaCreateCommand(plugin, mock(LangHandler.class)).onCommand(player, null, "hg", new String[0]);
        var region = YamlConfiguration.loadConfiguration(file.toFile());
        assertEquals(min, region.getInt("region.pos1.y"));
        assertEquals(max - 1, region.getInt("region.pos2.y"));
        assertEquals(-30, region.getInt("region.pos1.x"));
        assertEquals(-60, region.getInt("region.pos2.z"));
    }
    @Test
    void existingArenasUpgradeTheirHeightWithoutChangingHorizontalCorners() throws Exception {
        Path file = savedRegion();
        var region = new ArenaHandler(plugin(), mock(LangHandler.class)).getArenaConfig(world(-64, 320));
        assertEquals(-64, region.getInt("region.pos1.y"));
        assertEquals(319, region.getInt("region.pos2.y"));
        var persisted = YamlConfiguration.loadConfiguration(file.toFile());
        assertEquals(319, persisted.getInt("region.pos2.y"));
        assertEquals(40, persisted.getInt("region.pos1.z"));
        assertEquals(50, persisted.getInt("region.pos2.x"));
    }
}
