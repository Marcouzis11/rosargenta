package me.aymanisam.hungergames.handlers;

import me.aymanisam.hungergames.HungerGames;
import me.aymanisam.hungergames.commands.ArenaScanCommand;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.Directional;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ChestConversionTest {
    @TempDir Path temp;

    @Test void scanConvertsAndRegistersAsNormalThenDoesNotConvertAgain() throws Exception {
        HungerGames.gameStarted.clear(); HungerGames.gameStarting.clear(); WorldResetHandler.busyWorlds.clear();
        HungerGames.hgWorldNames.clear(); HungerGames.hgWorldNames.add("arena");
        HungerGames plugin = mock(HungerGames.class);
        when(plugin.getDataFolder()).thenReturn(temp.toFile());
        when(plugin.getConfigHandler()).thenReturn(mock(ConfigHandler.class));
        Server server = mock(Server.class); when(plugin.getServer()).thenReturn(server);
        World world = mock(World.class); when(server.getWorld("arena")).thenReturn(world);
        when(world.getName()).thenReturn("arena"); when(world.getMinHeight()).thenReturn(-64); when(world.getMaxHeight()).thenReturn(320);
        Path folder = Files.createDirectories(temp.resolve("arena"));
        var region = new YamlConfiguration(); region.set("region.world", "arena");
        for (String corner : List.of("pos1", "pos2")) {
            region.set("region." + corner + ".x", 0); region.set("region." + corner + ".y", 64); region.set("region." + corner + ".z", 0);
        }
        region.save(folder.resolve("arena.yml").toFile());
        Chunk chunk = mock(Chunk.class); when(world.getLoadedChunks()).thenReturn(new Chunk[]{chunk});
        when(world.getChunkAt(0, 0)).thenReturn(chunk); when(chunk.isLoaded()).thenReturn(true);
        Block block = mock(Block.class); BlockState ender = mock(BlockState.class); Chest normal = mock(Chest.class);
        when(ender.getType()).thenReturn(Material.ENDER_CHEST); when(ender.getBlock()).thenReturn(block);
        Directional originalData = mock(Directional.class); when(originalData.getFacing()).thenReturn(BlockFace.WEST);
        when(ender.getBlockData()).thenReturn(originalData);
        when(normal.getBlock()).thenReturn(block); when(normal.getType()).thenReturn(Material.CHEST);
        when(normal.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(normal.getInventory()).thenReturn(mock(Inventory.class));
        when(block.getState()).thenReturn(normal);
        when(chunk.getTileEntities()).thenReturn(new BlockState[]{ender}, new BlockState[]{normal});
        var replacement = mock(org.bukkit.block.data.type.Chest.class);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.createBlockData(Material.CHEST)).thenReturn(replacement);
            var command = new ArenaScanCommand(plugin, mock(LangHandler.class));
            CommandSender sender = mock(CommandSender.class);
            command.onCommand(sender, null, "hg", new String[]{"arena"});
            verify(replacement).setFacing(BlockFace.WEST);
            verify(block).setBlockData(replacement, false);
            var saved = YamlConfiguration.loadConfiguration(folder.resolve("chest-locations.yml").toFile());
            assertEquals(1, saved.getMapList("chest-locations").size());
            assertTrue(saved.getMapList("ender-chests-locations").isEmpty());
            assertEquals(64.0, saved.getMapList("chest-locations").get(0).get("y"));
            command.onCommand(sender, null, "hg", new String[]{"arena"});
            verify(block, times(1)).setBlockData(replacement, false);
        }
    }

    @Test void regularContainersAreNeverReplaced() {
        BlockState state = mock(BlockState.class);
        when(state.getType()).thenReturn(Material.CHEST);
        assertFalse(ChestConversionHandler.convertEnderChest(state));
        verify(state, never()).getBlock();
    }
}
