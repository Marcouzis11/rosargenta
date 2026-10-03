package me.aymanisam.hungergames.handlers;

import org.bukkit.Location;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;

/** Identifies a physical chest independently of the half used to access it. */
public record ChestIdentity(String world, int x, int y, int z) {
    public static Location canonicalLocation(Chest chest, Location fallback) {
        if (chest.getInventory().getHolder() instanceof DoubleChest joined
                && joined.getLeftSide() instanceof Chest left && joined.getRightSide() instanceof Chest right) {
            Location a = left.getLocation(), b = right.getLocation();
            if (a.getBlockX() != b.getBlockX()) return a.getBlockX() < b.getBlockX() ? a : b;
            return a.getBlockZ() <= b.getBlockZ() ? a : b;
        }
        return fallback;
    }

    public static ChestIdentity of(Chest chest, Location fallback) {
        Location location = canonicalLocation(chest, fallback);
        return new ChestIdentity(location.getWorld() == null ? null : location.getWorld().getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
