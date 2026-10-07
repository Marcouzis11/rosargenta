package me.aymanisam.hungergames.handlers;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArenaReliabilityTest {
    @TempDir Path temp;
    @Test void restoreReplacesBlocksAndEntitiesAndPreservesWorldIdentity() throws Exception {
        Path world = Files.createDirectory(temp.resolve("arena"));
        Path templates = Files.createDirectory(temp.resolve("templates"));
        Path template = Files.createDirectory(templates.resolve("arena"));
        Files.writeString(template.resolve("level.dat"), "baseline");
        Files.writeString(template.resolve("region.mca"), "original blocks and entities");
        Files.writeString(world.resolve("level.dat"), "played");
        Files.writeString(world.resolve("extra.mca"), "new chunk");
        Files.writeString(world.resolve("uid.dat"), "stable uuid");
        WorldResetHandler.restoreFiles(world.toFile(), template.toFile());
        assertEquals("baseline", Files.readString(world.resolve("level.dat")));
        assertEquals("original blocks and entities", Files.readString(world.resolve("region.mca")));
        assertEquals("stable uuid", Files.readString(world.resolve("uid.dat")));
        assertFalse(Files.exists(world.resolve("extra.mca")));
        assertEquals("played", Files.readString(temp.resolve(".arena-hg-previous/level.dat")));
    }
    @Test void incompleteOrNestedTemplateDoesNotTouchExistingWorld() throws Exception {
        Path world = Files.createDirectory(temp.resolve("arena"));
        Files.writeString(world.resolve("level.dat"), "keep me");
        Path template = Files.createDirectory(temp.resolve("template"));
        assertThrows(java.io.IOException.class, () -> WorldResetHandler.restoreFiles(world.toFile(), template.toFile()));
        assertThrows(java.io.IOException.class, () -> WorldResetHandler.requireSafePaths(world.toFile(), world.resolve("nested").toFile()));
        assertEquals("keep me", Files.readString(world.resolve("level.dat")));
    }
    @Test void restoreKeepsWorldSymlinkAndUpdatesItsTarget() throws Exception {
        Path world = Files.createDirectory(temp.resolve("real-world"));
        Path template = Files.createDirectory(temp.resolve("template"));
        Files.writeString(world.resolve("level.dat"), "played");
        Files.writeString(template.resolve("level.dat"), "baseline");
        Path link = temp.resolve("arena-link");
        Files.createSymbolicLink(link, world);
        WorldResetHandler.restoreFiles(link.toFile(), template.toFile());
        assertTrue(Files.isSymbolicLink(link));
        assertEquals("baseline", Files.readString(world.resolve("level.dat")));
        assertEquals("played", Files.readString(temp.resolve(".real-world-hg-previous/level.dat")));
    }
    @Test void restoreSupportsTemplateOnAnotherLinuxFilesystem() throws Exception {
        Path sharedMemory = Path.of("/dev/shm");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isDirectory(sharedMemory) && Files.isWritable(sharedMemory));
        org.junit.jupiter.api.Assumptions.assumeFalse(Files.getFileStore(temp).equals(Files.getFileStore(sharedMemory)));
        Path template = Files.createTempDirectory(sharedMemory, "hg-restore-test-");
        try {
            Path world = Files.createDirectory(temp.resolve("mounted-arena"));
            Files.writeString(world.resolve("level.dat"), "played");
            Files.writeString(template.resolve("level.dat"), "baseline");
            WorldResetHandler.restoreFiles(world.toFile(), template.toFile());
            assertEquals("baseline", Files.readString(world.resolve("level.dat")));
            assertEquals("played", Files.readString(temp.resolve(".mounted-arena-hg-previous/level.dat")));
        } finally { org.apache.commons.io.FileUtils.deleteDirectory(template.toFile()); }
    }
    @Test void leavingLastTeamMemberEliminatesTeamOnlyOnce() {
        me.aymanisam.hungergames.HungerGames.gameStarted.put("arena", true);
        GameSequenceHandler.playersAlive.clear(); GameSequenceHandler.playerPlacements.clear();
        GameSequenceHandler.celebratingWorlds.clear(); TeamsHandler.teamsAlive.clear(); TeamsHandler.teams.clear();
        World world = mock(World.class); when(world.getName()).thenReturn("arena");
        Player player = mock(Player.class);
        GameSequenceHandler.playersAlive.put("arena", new ArrayList<>(List.of(player)));
        TeamsHandler.teamsAlive.put("arena", new ArrayList<>(List.of(new ArrayList<>(List.of(player)))));
        ParticipationHandler.leave(player, world); ParticipationHandler.leave(player, world);
        assertTrue(TeamsHandler.teamsAlive.get("arena").isEmpty());
        assertEquals(List.of(player), GameSequenceHandler.playerPlacements.get("arena"));
    }

    @Test void returningWinnerToLobbyDoesNotAddANewElimination() {
        me.aymanisam.hungergames.HungerGames.gameStarted.put("finished", false);
        me.aymanisam.hungergames.HungerGames.gameStarting.put("finished", false);
        World world = mock(World.class); when(world.getName()).thenReturn("finished");
        Player player = mock(Player.class);
        GameSequenceHandler.playersAlive.put("finished", new ArrayList<>(List.of(player)));
        GameSequenceHandler.playerPlacements.put("finished", new ArrayList<>());
        ParticipationHandler.leave(player, world);
        assertTrue(GameSequenceHandler.playerPlacements.get("finished").isEmpty());
    }
    @Test void basicLootContainsFoodAndWeaponWithoutExceedingStackCount() {
        List<ItemStack> pool = List.of(new ItemStack(Material.OAK_LOG), new ItemStack(Material.STICK),
                new ItemStack(Material.WOODEN_SWORD), new ItemStack(Material.COOKED_BEEF));
        var loot = ChestRefillHandler.balancedSelection(pool, 3);
        assertEquals(3, loot.size());
        assertTrue(loot.stream().anyMatch(item -> item.getType() == Material.WOODEN_SWORD));
        assertTrue(loot.stream().anyMatch(item -> item.getType() == Material.COOKED_BEEF));
    }

    @Test void enderLootIsSharedPerBlockAndClearedBetweenMatches() {
        EnderLootHandler.clear("arena");
        World world = mock(World.class); when(world.getName()).thenReturn("arena");
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.createInventory(org.mockito.ArgumentMatchers.any(org.bukkit.inventory.InventoryHolder.class),
                    org.mockito.ArgumentMatchers.eq(27), org.mockito.ArgumentMatchers.anyString()))
                    .thenAnswer(call -> mock(org.bukkit.inventory.Inventory.class));
            Location location = new Location(world, 5, 64, 5);
            var first = EnderLootHandler.inventoryAt(location);
            assertSame(first, EnderLootHandler.inventoryAt(location.clone()));
            assertNotSame(first, EnderLootHandler.inventoryAt(new Location(world, 6, 64, 5)));
            EnderLootHandler.clear("arena");
            assertNull(EnderLootHandler.existingAt(location));
            assertNotSame(first, EnderLootHandler.inventoryAt(location));
        }
    }

    @Test void validationRejectsUnsafeSpawnsAndTooSmallFinalBorder() throws Exception {
        var plugin = mock(me.aymanisam.hungergames.HungerGames.class);
        var configHandler = mock(ConfigHandler.class);
        var config = new org.bukkit.configuration.file.YamlConfiguration();
        config.set("game-time", 60); config.set("min-players", 2); config.set("border.size", 400);
        config.set("deathmatch.border-size", 10); config.set("supplydrop.interval", 30); config.set("chestrefill.interval", 600);
        for (String tier : List.of("chest", "barrel", "trapped-chest")) {
            config.set("min-" + tier + "-content", 3); config.set("max-" + tier + "-content", 6);
        }
        var settings = new org.bukkit.configuration.file.YamlConfiguration(); settings.set("lobby-world", "lobby");
        when(plugin.getConfigHandler()).thenReturn(configHandler); when(plugin.getDataFolder()).thenReturn(temp.toFile());
        World world = mock(World.class); when(world.getName()).thenReturn("arena");
        when(world.getMinHeight()).thenReturn(-64); when(world.getMaxHeight()).thenReturn(320);
        var server = mock(Server.class); when(plugin.getServer()).thenReturn(server); when(server.getWorld("lobby")).thenReturn(mock(World.class));
        when(configHandler.getWorldConfig(world)).thenReturn(config); when(configHandler.getPluginSettings()).thenReturn(settings);
        var block = mock(org.bukkit.block.Block.class); when(block.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(block);
        var errors = new ArenaValidationHandler(plugin).validate(world, List.of("arena,-10,64,0", "arena,10,64,0"), 2);
        assertTrue(errors.stream().anyMatch(error -> error.contains("Borde final")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("Spawn sin suelo")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("cofres registrados")));
    }
}
