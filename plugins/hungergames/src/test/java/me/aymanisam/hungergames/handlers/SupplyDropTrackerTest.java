package me.aymanisam.hungergames.handlers;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SupplyDropTrackerTest {
    @Test void latestDropReplacesPreviousTargetAndEmptyDropClearsIndicator() {
        var plugin = org.mockito.Mockito.mock(me.aymanisam.hungergames.HungerGames.class);
        var server = org.mockito.Mockito.mock(org.bukkit.Server.class);
        var scheduler = org.mockito.Mockito.mock(org.bukkit.scheduler.BukkitScheduler.class);
        var world = org.mockito.Mockito.mock(org.bukkit.World.class);
        var player = org.mockito.Mockito.mock(org.bukkit.entity.Player.class);
        var indicator = org.mockito.Mockito.mock(org.bukkit.boss.BossBar.class);
        org.mockito.Mockito.when(server.createBossBar(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(org.bukkit.boss.BarColor.YELLOW), org.mockito.ArgumentMatchers.eq(org.bukkit.boss.BarStyle.SOLID))).thenReturn(indicator);
        var block = org.mockito.Mockito.mock(org.bukkit.block.Block.class);
        var box = org.mockito.Mockito.mock(org.bukkit.block.ShulkerBox.class);
        var inventory = org.mockito.Mockito.mock(org.bukkit.inventory.Inventory.class);
        org.mockito.Mockito.when(plugin.getServer()).thenReturn(server);
        org.mockito.Mockito.when(server.getScheduler()).thenReturn(scheduler);
        org.mockito.Mockito.doReturn(java.util.List.of(player)).when(server).getOnlinePlayers();
        org.mockito.Mockito.when(world.getName()).thenReturn("tracker-test");
        org.mockito.Mockito.when(world.getUID()).thenReturn(java.util.UUID.randomUUID());
        org.mockito.Mockito.when(world.getBlockAt(org.mockito.ArgumentMatchers.any(Location.class))).thenReturn(block);
        org.mockito.Mockito.when(block.getType()).thenReturn(org.bukkit.Material.RED_SHULKER_BOX);
        org.mockito.Mockito.when(block.getState()).thenReturn(box);
        org.mockito.Mockito.when(box.getInventory()).thenReturn(inventory);
        org.mockito.Mockito.when(inventory.getContents()).thenReturn(new org.bukkit.inventory.ItemStack[]{new org.bukkit.inventory.ItemStack(org.bukkit.Material.APPLE)});
        org.mockito.Mockito.when(player.getWorld()).thenReturn(world);
        org.mockito.Mockito.when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        org.mockito.Mockito.when(player.getGameMode()).thenReturn(org.bukkit.GameMode.SURVIVAL);
        org.mockito.Mockito.when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        me.aymanisam.hungergames.HungerGames.gameStarted.put("tracker-test", true);
        GameSequenceHandler.playersAlive.put("tracker-test", java.util.List.of(player));
        try {
            SupplyDropTracker tracker = new SupplyDropTracker(plugin);
            tracker.track(new Location(world, 10, 64, 0));
            assertTrue(tracker.directionFor(player).contains("←"));
            assertTrue(tracker.directionFor(player).contains("10 m"));
            tracker.track(new Location(world, -20, 64, 0));
            assertTrue(tracker.directionFor(player).contains("→"));
            assertTrue(tracker.directionFor(player).contains("20 m"));
            var runnable = org.mockito.ArgumentCaptor.forClass(Runnable.class);
            org.mockito.Mockito.verify(scheduler).runTaskTimer(org.mockito.ArgumentMatchers.eq(plugin), runnable.capture(), org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.eq(10L));
            org.mockito.Mockito.when(inventory.getContents()).thenReturn(new org.bukkit.inventory.ItemStack[0]);
            runnable.getValue().run();
            assertEquals("", tracker.directionFor(player));
            var messages = org.mockito.ArgumentCaptor.forClass(String.class);
            org.mockito.Mockito.verify(indicator, org.mockito.Mockito.times(2)).setTitle(messages.capture());
            assertTrue(messages.getAllValues().get(0).contains("← ← ←"));
            assertTrue(messages.getAllValues().get(1).contains("→ → →"));
            assertTrue(messages.getAllValues().get(1).contains("20 m"));
            org.mockito.Mockito.verify(server, org.mockito.Mockito.times(1)).createBossBar(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(org.bukkit.boss.BarColor.YELLOW), org.mockito.ArgumentMatchers.eq(org.bukkit.boss.BarStyle.SOLID));
            org.mockito.Mockito.verify(indicator).addPlayer(player);
            org.mockito.Mockito.verify(indicator).removeAll();
            org.mockito.Mockito.verify(indicator).setVisible(false);
            org.mockito.Mockito.verify(player, org.mockito.Mockito.never()).spigot();
        } finally {
            me.aymanisam.hungergames.HungerGames.gameStarted.remove("tracker-test");
            GameSequenceHandler.playersAlive.remove("tracker-test");
        }
    }
    @Test void arrowTracksAllEightDirectionsRelativeToPlayerView() {
        Location player = new Location(null, 0, 64, 0, 0, 0);
        double[][] points = {{0,10},{-10,10},{-10,0},{-10,-10},{0,-10},{10,-10},{10,0},{10,10}};
        String[] arrows = {"↑","↗","→","↘","↓","↙","←","↖"};
        for (int i = 0; i < points.length; i++) {
            assertEquals(arrows[i], SupplyDropTracker.arrow(player, new Location(null, points[i][0], 70, points[i][1])));
        }
    }
    @Test void rotatingOrMovingPlayerAndReplacingTargetRedirectsArrow() {
        Location player = new Location(null, 0, 64, 0, -90, 0);
        assertEquals("↑", SupplyDropTracker.arrow(player, new Location(null, 10, 64, 0)));
        assertEquals("↓", SupplyDropTracker.arrow(player, new Location(null, -10, 64, 0)));
        player.setYaw(270);
        assertEquals("↑", SupplyDropTracker.arrow(player, new Location(null, 10, 64, 0)));
        assertEquals("●", SupplyDropTracker.arrow(player, new Location(null, 1, 90, 0)));
    }
}
