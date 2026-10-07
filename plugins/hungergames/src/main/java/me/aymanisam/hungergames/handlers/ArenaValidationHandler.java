package me.aymanisam.hungergames.handlers;

import me.aymanisam.hungergames.HungerGames;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.util.*;

public class ArenaValidationHandler {
    private final HungerGames plugin;
    public ArenaValidationHandler(HungerGames plugin) { this.plugin = plugin; }

    public List<String> validate(World world, Collection<String> points, int required) {
        List<String> errors = new ArrayList<>();
        var config = plugin.getConfigHandler().getWorldConfig(world);
        var region = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), world.getName() + "/arena.yml"));
        if (!world.getName().equals(region.getString("region.world"))
                || !region.contains("region.pos1.x") || !region.contains("region.pos2.x"))
            errors.add("Falta definir la región de la arena con /hg select y /hg create.");
        if (WorldResetHandler.busyWorlds.contains(world.getName())) errors.add("La arena está guardándose o restaurándose.");
        String lobbyName = plugin.getConfigHandler().getPluginSettings().getString("lobby-world");
        if (lobbyName == null || plugin.getServer().getWorld(lobbyName) == null || world.getName().equals(lobbyName))
            errors.add("Configurá un mundo lobby distinto de la arena y cargado.");
        if (config.getInt("game-time") < 1) errors.add("game-time debe ser mayor que cero.");
        if (config.getInt("min-players") < 1) errors.add("min-players debe ser al menos 1.");
        for (String tier : List.of("chest", "barrel", "trapped-chest")) {
            int min = config.getInt("min-" + tier + "-content"), max = config.getInt("max-" + tier + "-content");
            if (min < 1 || max < min || max > 27) errors.add("Cantidad de botín inválida para " + tier + ".");
        }
        for (String path : List.of("supplydrop.interval", "chestrefill.interval"))
            if (config.getInt(path) < 1) errors.add(path + " debe ser mayor que cero.");
        if (points.size() < Math.max(2, required)) errors.add("Faltan spawns: se necesitan " + Math.max(2, required) + ".");
        Set<String> unique = new HashSet<>();
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (String point : points) {
            try {
                String[] parts = point.split(",");
                if (parts.length != 4 || !parts[0].equals(world.getName())) throw new IllegalArgumentException();
                double x = Double.parseDouble(parts[1]) + .5, y = Double.parseDouble(parts[2]) + 1, z = Double.parseDouble(parts[3]) + .5;
                if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException();
                if (!unique.add(x + "," + y + "," + z)) errors.add("Spawn duplicado: " + point);
                if (y < world.getMinHeight() || y >= world.getMaxHeight()) errors.add("Spawn fuera de altura: " + point);
                if (!world.getBlockAt((int)Math.floor(x), (int)Math.floor(y), (int)Math.floor(z)).isPassable()
                        || !world.getBlockAt((int)Math.floor(x), (int)Math.floor(y + 1), (int)Math.floor(z)).isPassable()
                        || !world.getBlockAt((int)Math.floor(x), (int)Math.floor(y - 1), (int)Math.floor(z)).getType().isSolid())
                    errors.add("Spawn sin suelo seguro o bloqueado: " + point);
                double half = config.getDouble("border.size") / 2;
                if (Math.abs(x - config.getDouble("border.center-x")) >= half || Math.abs(z - config.getDouble("border.center-z")) >= half)
                    errors.add("Spawn fuera del borde inicial: " + point);
                minX = Math.min(minX, x); maxX = Math.max(maxX, x); minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
            } catch (RuntimeException e) { errors.add("Spawn inválido: " + point); }
        }
        double finalSize = config.getDouble("deathmatch.border-size", 30);
        if (!Double.isFinite(finalSize) || finalSize < Math.max(maxX - minX, maxZ - minZ) + 4 || finalSize < 1)
            errors.add("Borde final demasiado chico: necesitás al menos " + Math.max(1, Math.max(maxX - minX, maxZ - minZ) + 4) + " bloques para los spawns.");
        File folder = new File(plugin.getDataFolder(), world.getName());
        var chests = YamlConfiguration.loadConfiguration(new File(folder, "chest-locations.yml"));
        int containers = 0;
        for (String key : List.of("chest-locations", "trapped-chests-locations", "barrel-locations", "ender-chests-locations")) {
            for (Map<?, ?> entry : chests.getMapList(key)) {
                containers++;
                try {
                    int x = ((Number)entry.get("x")).intValue(), y = ((Number)entry.get("y")).intValue(), z = ((Number)entry.get("z")).intValue();
                    Material expected = switch (key) { case "barrel-locations" -> Material.BARREL; case "trapped-chests-locations" -> Material.TRAPPED_CHEST; case "ender-chests-locations" -> Material.ENDER_CHEST; default -> Material.CHEST; };
                    if (!world.getName().equals(entry.get("world")) || world.getBlockAt(x, y, z).getType() != expected)
                        errors.add("Cofre registrado inexistente en " + x + "," + y + "," + z + "; ejecutá /hg scanarena.");
                } catch (RuntimeException e) { errors.add("Ubicación de cofre inválida; ejecutá /hg scanarena."); }
            }
        }
        if (containers == 0) errors.add("No hay cofres registrados; ejecutá /hg scanarena.");
        if (plugin.getConfigHandler().getPluginSettings().getBoolean("reset-world")
                && !new File(plugin.getDataFolder(), "templates/" + world.getName() + "/level.dat").isFile())
            errors.add("Falta la plantilla: ejecutá /hg saveworld antes de jugar.");
        return errors;
    }
}
