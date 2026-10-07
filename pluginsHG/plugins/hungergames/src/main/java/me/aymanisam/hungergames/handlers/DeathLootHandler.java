package me.aymanisam.hungergames.handlers;

import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

public final class DeathLootHandler {
    public static void dropInventory(PlayerDeathEvent event) {
        event.setKeepInventory(false);
        event.getDrops().clear();
        // getContents includes storage, equipped armor and offhand exactly once.
        for (ItemStack item : event.getEntity().getInventory().getContents()) {
            if (item != null && !item.getType().isAir() && item.getAmount() > 0) {
                event.getDrops().add(item.clone());
            }
        }
    }
}
