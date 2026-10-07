package me.aymanisam.hungergames.handlers;

import me.aymanisam.hungergames.HungerGames;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ChestLootTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = Material.class, names = {"CHEST", "TRAPPED_CHEST"})
    void bothHalvesOfAnOldDoubleChestRecordAreFilledAndRolledOnlyOnce(Material type) {
        HungerGames plugin = mock(HungerGames.class);
        when(plugin.getConfigHandler()).thenReturn(mock(ConfigHandler.class));
        ChestRefillHandler handler = new ChestRefillHandler(plugin, mock(LangHandler.class));
        org.bukkit.World world = mock(org.bukkit.World.class);
        when(world.getName()).thenReturn("arena");
        Chest left = mock(Chest.class), right = mock(Chest.class);
        Inventory firstView = mock(Inventory.class), secondView = mock(Inventory.class);
        var joined = mock(org.bukkit.block.DoubleChest.class);
        when(joined.getLeftSide()).thenReturn(left);
        when(joined.getRightSide()).thenReturn(right);
        when(left.getInventory()).thenReturn(firstView);
        when(right.getInventory()).thenReturn(secondView);
        when(firstView.getHolder()).thenReturn(joined);
        when(secondView.getHolder()).thenReturn(joined);
        when(left.getLocation()).thenReturn(new Location(world, 10, 64, 10));
        when(right.getLocation()).thenReturn(new Location(world, 11, 64, 10));
        Location leftLocation = mock(Location.class), rightLocation = mock(Location.class);
        Block leftBlock = mock(Block.class), rightBlock = mock(Block.class);
        when(leftLocation.getBlock()).thenReturn(leftBlock);
        when(rightLocation.getBlock()).thenReturn(rightBlock);
        when(leftBlock.getState()).thenReturn(left);
        when(rightBlock.getState()).thenReturn(right);
        when(leftBlock.getType()).thenReturn(type);
        when(rightBlock.getType()).thenReturn(type);
        when(firstView.getSize()).thenReturn(54);
        Map<Integer, ItemStack> contents = new HashMap<>();
        when(firstView.getItem(anyInt())).thenAnswer(call -> contents.get(call.getArgument(0)));
        doAnswer(call -> { contents.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(firstView).setItem(anyInt(), any(ItemStack.class));
        var loot = new YamlConfiguration();
        String key = type == Material.CHEST ? "chest-items" : "trapped-chest-items";
        loot.set(key, List.of(Map.of("type", "STONE_SWORD", "amount", 1, "weight", 50),
                Map.of("type", "IRON_INGOT", "amount", 1, "chance", 30)));
        Random seeded = spy(new Random(7));
        handler.refillInventory(List.of(leftLocation, rightLocation, leftLocation), key, loot, 5, 5, seeded);
        verify(firstView, times(1)).clear();
        verify(secondView, never()).clear();
        verify(seeded, times(1)).nextDouble();
        assertTrue(contents.size() >= 5 && contents.size() <= 6);
        assertEquals(ChestIdentity.of(left, leftLocation), ChestIdentity.of(right, rightLocation));
        assertEquals(new Location(world, 10, 64, 10), ChestIdentity.canonicalLocation(right, rightLocation));
    }

    @Test
    void amountsIncludeBothEndsAndSupportFixedRanges() {
        Random random = new Random(1234);
        Map<String, Object> item = Map.of("amount", Map.of("min", 1, "max", 2));
        Set<Integer> amounts = new HashSet<>();
        for (int i = 0; i < 100; i++) amounts.add(ChestRefillHandler.rollAmount(item, random));
        assertEquals(Set.of(1, 2), amounts);
        assertEquals(2, ChestRefillHandler.rollAmount(Map.of("amount", Map.of("min", 2, "max", 2)), random));
    }

    @Test
    void thirtyPercentIsAnIndependentRollPerChest() {
        assertEquals(1, fillWithChance(0.299999).stream().filter(i -> i.getType() == Material.IRON_INGOT).count());
        assertEquals(0, fillWithChance(0.30).stream().filter(i -> i.getType() == Material.IRON_INGOT).count());
    }

    private Collection<ItemStack> fillWithChance(double roll) {
        HungerGames plugin = mock(HungerGames.class);
        when(plugin.getConfigHandler()).thenReturn(mock(ConfigHandler.class));
        ChestRefillHandler handler = new ChestRefillHandler(plugin, mock(LangHandler.class));
        Location location = mock(Location.class);
        Block block = mock(Block.class);
        Chest chest = mock(Chest.class);
        Inventory inventory = mock(Inventory.class);
        when(location.getBlock()).thenReturn(block);
        when(block.getState()).thenReturn(chest);
        when(chest.getInventory()).thenReturn(inventory);
        when(inventory.getSize()).thenReturn(27);
        Map<Integer, ItemStack> contents = new HashMap<>();
        when(inventory.getItem(anyInt())).thenAnswer(call -> contents.get(call.getArgument(0)));
        doAnswer(call -> { contents.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(inventory).setItem(anyInt(), any(ItemStack.class));
        YamlConfiguration loot = new YamlConfiguration();
        loot.set("chest-items", List.of(Map.of("type", "STONE_SWORD", "amount", 1, "weight", 50),
                Map.of("type", "IRON_INGOT", "amount", Map.of("min", 1, "max", 2), "chance", 30)));
        Random random = new Random(7) {
            @Override public double nextDouble() { return roll; }
        };
        handler.refillInventory(List.of(location), "chest-items", loot, 5, 5, random);
        assertEquals(5, contents.values().stream().filter(i -> i.getType() == Material.STONE_SWORD).count());
        contents.values().stream().filter(i -> i.getType() == Material.IRON_INGOT)
                .forEach(i -> assertTrue(i.getAmount() == 1 || i.getAmount() == 2));
        return contents.values();
    }

    @Test
    void configuredSupplyDropsHaveDiamondAndExperienceBonusStacks() throws Exception {
        YamlConfiguration loot = new YamlConfiguration();
        try (var reader = new java.io.InputStreamReader(Objects.requireNonNull(
                getClass().getClassLoader().getResourceAsStream("items.yml")), java.nio.charset.StandardCharsets.UTF_8)) {
            loot.load(reader);
        }
        List<Map<?, ?>> normal = loot.getMapList("chest-items");
        Map<?, ?> iron = normal.stream().filter(i -> "IRON_INGOT".equals(i.get("type"))).findFirst().orElseThrow();
        assertEquals(30, iron.get("chance"));
        List<Map<?, ?>> drops = loot.getMapList("supply-drop-items");
        for (String type : List.of("DIAMOND", "EXPERIENCE_BOTTLE")) {
            Map<?, ?> entry = drops.stream().filter(i -> type.equals(i.get("type"))).findFirst().orElseThrow();
            assertEquals(100, entry.get("chance"));
            assertTrue(ChestRefillHandler.rollChance(entry, new Random(5)));
        }
    }
}
