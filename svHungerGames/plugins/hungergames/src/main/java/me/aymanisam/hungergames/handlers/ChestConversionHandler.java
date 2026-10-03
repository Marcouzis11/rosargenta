package me.aymanisam.hungergames.handlers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Chest;

public final class ChestConversionHandler {
    public static boolean convertEnderChest(BlockState state) {
        if (state.getType() != Material.ENDER_CHEST) return false;
        Chest replacement = (Chest) Bukkit.createBlockData(Material.CHEST);
        replacement.setFacing(((Directional) state.getBlockData()).getFacing());
        replacement.setType(Chest.Type.SINGLE);
        state.getBlock().setBlockData(replacement, false);
        return true;
    }
}
