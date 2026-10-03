package me.aymanisam.hungergames.handlers;

import org.bukkit.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import java.util.*;

/** One shared loot inventory per physical Ender chest, never personal Ender storage. */
public final class EnderLootHandler implements InventoryHolder {
    private static final Map<String, Map<String, EnderLootHandler>> chests = new HashMap<>();
    private final Inventory inventory;
    private EnderLootHandler() { inventory = Bukkit.createInventory(this, 27, "§5Cofre especial de HG"); }
    public Inventory getInventory() { return inventory; }
    public static Inventory inventoryAt(Location location) {
        String key = location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
        return chests.computeIfAbsent(location.getWorld().getName(), k -> new HashMap<>())
                .computeIfAbsent(key, k -> new EnderLootHandler()).inventory;
    }
    public static Inventory existingAt(Location location) {
        String key = location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
        EnderLootHandler holder = chests.getOrDefault(location.getWorld().getName(), Map.of()).get(key);
        return holder == null ? null : holder.inventory;
    }
    public static void clear(String world) { chests.remove(world); }
}
