package me.aymanisam.hungergames.handlers;

import me.aymanisam.hungergames.HungerGames;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.WorldBorder;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collection;

public class WorldBorderHandler {
    private final ConfigHandler configHandler;

    public WorldBorderHandler(HungerGames plugin, LangHandler langHandler) {
        this.configHandler = plugin.getConfigHandler();
    }

    public void startWorldBorder(World world) {
        resetWorldBorder(world);

        FileConfiguration config = configHandler.getWorldConfig(world);

        WorldBorder border = world.getWorldBorder();
        int centerX = config.getInt("border.center-x");
        int centerZ = config.getInt("border.center-z");
        border.setCenter(centerX, centerZ);
    }

    public void startDeathmatchBorder(World world, Collection<Location> spawns) {
        WorldBorder border = world.getWorldBorder();
        double minX = spawns.stream().mapToDouble(Location::getX).min().orElse(border.getCenter().getX());
        double maxX = spawns.stream().mapToDouble(Location::getX).max().orElse(minX);
        double minZ = spawns.stream().mapToDouble(Location::getZ).min().orElse(border.getCenter().getZ());
        double maxZ = spawns.stream().mapToDouble(Location::getZ).max().orElse(minZ);
        border.setCenter((minX + maxX) / 2.0, (minZ + maxZ) / 2.0);
        FileConfiguration config = configHandler.getWorldConfig(world);
        double desiredFinalSize = Math.max(1.0, config.getDouble("deathmatch.border-size", 30.0));
        // Keep every starting platform inside the border when players are teleported.
        double finalSize = Math.max(desiredFinalSize, Math.max(maxX - minX, maxZ - minZ) + 4.0);
        border.setSize(finalSize);
    }

    public void resetWorldBorder(World world) {
        FileConfiguration config = configHandler.getWorldConfig(world);
        int borderSize = config.getInt("border.size");
        WorldBorder border = world.getWorldBorder();
        border.setSize(borderSize);
    }
}
