package me.aymanisam.hungergames.commands;

import me.aymanisam.hungergames.HungerGames;
import me.aymanisam.hungergames.handlers.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.*;
import java.util.*;

public class ArenaAdminCommand implements CommandExecutor {
    private final HungerGames plugin;
    private final boolean loot;
    public ArenaAdminCommand(HungerGames plugin, boolean loot) { this.plugin = plugin; this.loot = loot; }
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("hungergames.config")) { sender.sendMessage("§cNo tenés permiso."); return true; }
        if (args.length < 2) {
            sender.sendMessage(loot ? "/hg loot <arena> <normal|especial|drop> [material min max peso [chance]]"
                    : "/hg config <arena> <resumen|validar|restaurar|tiempo|borde|proteccion> [valor]");
            return true;
        }
        String name = args[0];
        if (!HungerGames.hgWorldNames.contains(name) || WorldResetHandler.busyWorlds.contains(name)) {
            sender.sendMessage("§cArena inexistente u ocupada."); return true;
        }
        World world = plugin.getServer().getWorld(name);
        if (world == null) world = plugin.getServer().createWorld(new WorldCreator(name));
        if (world == null) { sender.sendMessage("§cNo se pudo cargar la arena."); return true; }
        var config = plugin.getConfigHandler().getWorldConfig(world);
        File folder = new File(plugin.getDataFolder(), name);
        try {
            if (loot) {
                String tier = switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "normal" -> "chest-items"; case "especial" -> "ender-chest-items"; case "drop" -> "supply-drop-items";
                    default -> throw new IllegalArgumentException("Usá normal, especial o drop.");
                };
                File file = new File(folder, "items.yml");
                YamlConfiguration items = YamlConfiguration.loadConfiguration(file);
                if (args.length == 2) { sender.sendMessage("§e" + tier + ": " + items.getMapList(tier)); return true; }
                if (HungerGames.isGameStartingOrStarted(name)) throw new IllegalArgumentException("Esperá a que termine la partida.");
                if (args.length != 6 && args.length != 7) throw new IllegalArgumentException("Indicá material, mínimo, máximo y peso; chance es opcional.");
                Material material = Material.matchMaterial(args[2]);
                if (material == null || !material.isItem() || material.isAir()) throw new IllegalArgumentException("Material inválido.");
                int min = Integer.parseInt(args[3]), max = Integer.parseInt(args[4]), weight = Integer.parseInt(args[5]);
                if (min < 1 || max < min || max > material.getMaxStackSize() || weight < 1 || weight > 100)
                    throw new IllegalArgumentException("Cantidades inválidas o peso fuera de 1–100.");
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("type", material.name()); entry.put("amount", Map.of("min", min, "max", max));
                if (args.length == 7) {
                    double chance = Double.parseDouble(args[6]);
                    if (!Double.isFinite(chance) || chance < 0 || chance > 100) throw new IllegalArgumentException("Chance debe estar entre 0 y 100.");
                    entry.put("chance", chance);
                } else entry.put("weight", weight);
                List<Map<?, ?>> entries = new ArrayList<>(items.getMapList(tier));
                entries.removeIf(item -> material.name().equals(item.get("type")));
                entries.add(entry); items.set(tier, entries); items.save(file);
                sender.sendMessage("§aBotín actualizado para la próxima partida."); return true;
            }
            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "restaurar" -> {
                    if (HungerGames.isGameStartingOrStarted(name) || !world.getPlayers().isEmpty())
                        throw new IllegalArgumentException("La arena debe estar vacía y sin partida.");
                    if (!new File(plugin.getDataFolder(), "templates/" + name + "/level.dat").isFile())
                        throw new IllegalArgumentException("Falta la plantilla; ejecutá /hg saveworld.");
                    EnderLootHandler.clear(name);
                    new WorldResetHandler(plugin, new WorldBorderHandler(plugin, null)).resetWorldState(world);
                    sender.sendMessage("§eRestaurando arena. Se habilitará al finalizar.");
                }
                case "resumen" -> sender.sendMessage("§eArena " + name + ": tiempo=" + config.getInt("game-time") + "s, borde inicial="
                        + config.getInt("border.size") + ", borde final=" + config.getDouble("deathmatch.border-size", 30)
                        + ", protección=" + config.getInt("deathmatch.protection-seconds", 5) + "s, cofres="
                        + config.getInt("min-chest-content") + "–" + config.getInt("max-chest-content") + ", restauración="
                        + plugin.getConfigHandler().getPluginSettings().getBoolean("reset-world"));
                case "validar" -> {
                    List<String> errors = new ArenaValidationHandler(plugin).validate(world,
                            YamlConfiguration.loadConfiguration(new File(folder, "setspawn.yml")).getStringList("spawnpoints"), config.getInt("min-players"));
                    sender.sendMessage(errors.isEmpty() ? "§aArena lista para jugar." : "§c" + String.join("\n§c", errors));
                }
                case "tiempo", "borde", "proteccion" -> {
                    if (HungerGames.isGameStartingOrStarted(name)) throw new IllegalArgumentException("Esperá a que termine la partida.");
                    if (args.length != 3) throw new IllegalArgumentException("Falta el valor.");
                    int value = Integer.parseInt(args[2]);
                    String key = switch (args[1].toLowerCase(Locale.ROOT)) { case "tiempo" -> "game-time"; case "borde" -> "deathmatch.border-size"; default -> "deathmatch.protection-seconds"; };
                    if (value < (key.endsWith("seconds") ? 0 : 1) || value > (key.endsWith("seconds") ? 30 : 86400)) throw new IllegalArgumentException("Valor fuera de rango.");
                    Object previous = config.get(key); config.set(key, value);
                    if (key.equals("deathmatch.border-size")) {
                        List<String> errors = new ArenaValidationHandler(plugin).validate(world,
                                YamlConfiguration.loadConfiguration(new File(folder, "setspawn.yml")).getStringList("spawnpoints"), config.getInt("min-players"));
                        if (!errors.isEmpty()) { config.set(key, previous); throw new IllegalArgumentException(String.join(" | ", errors)); }
                    }
                    plugin.getConfigHandler().saveWorldConfig(world); sender.sendMessage("§a" + key + " = " + value);
                }
                default -> throw new IllegalArgumentException("Usá resumen, validar, tiempo, borde o proteccion.");
            }
        } catch (IllegalArgumentException | IOException e) { sender.sendMessage("§c" + e.getMessage()); }
        return true;
    }
}
