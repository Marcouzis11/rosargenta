package me.aymanisam.hungergames.handlers;

import me.aymanisam.hungergames.HungerGames;
import org.apache.commons.io.FileUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.EndGateway;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;
import java.util.HashSet;
import java.util.logging.Level;

public class WorldResetHandler {
    public static final Set<String> busyWorlds = new HashSet<>();
    private final HungerGames plugin;
    private final WorldBorderHandler worldBorderHandler;

    public WorldResetHandler(HungerGames plugin, WorldBorderHandler worldBorderHandler) {
        this.plugin = plugin;
	    this.worldBorderHandler = worldBorderHandler;
    }

    public void saveWorldState(World world) {
        if (HungerGames.isGameStartingOrStarted(world.getName()) || !busyWorlds.add(world.getName()))
            throw new IllegalStateException("La arena está ocupada.");
        File worldDirectory = world.getWorldFolder();
        File templateDirectory = new File(plugin.getDataFolder(), "templates" + File.separator + world.getName());

        boolean autoSave = world.isAutoSave();
        try {
            Files.createDirectories(templateDirectory.toPath().getParent());
            requireSafePaths(worldDirectory, templateDirectory);
            world.save();
            world.setAutoSave(false);
        } catch (IOException | RuntimeException e) {
            busyWorlds.remove(world.getName());
            throw new IllegalStateException("No se pudo preparar la plantilla de " + world.getName(), e);
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                File staging = new File(templateDirectory.getParentFile(), world.getName() + "-saving");
                requireSafePaths(worldDirectory, templateDirectory);
                requireChild(templateDirectory.getParentFile(), staging);
                if (staging.exists()) FileUtils.deleteDirectory(staging);
                FileUtils.copyDirectory(worldDirectory, staging, pathname -> {
                    String name = pathname.getName();
                    return !name.equals("session.lock") && !name.equals("uid.dat") && !name.equals("session.dat");
                });
                if (!new File(staging, "level.dat").isFile()) throw new IOException("Plantilla incompleta");
                File previous = new File(templateDirectory.getParentFile(), world.getName() + "-previous");
                requireChild(templateDirectory.getParentFile(), previous);
                if (previous.exists()) FileUtils.deleteDirectory(previous);
                if (templateDirectory.exists()) Files.move(templateDirectory.toPath(), previous.toPath());
                try { Files.move(staging.toPath(), templateDirectory.toPath()); }
                catch (IOException e) {
                    if (previous.exists()) Files.move(previous.toPath(), templateDirectory.toPath());
                    throw e;
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "No se pudo guardar la plantilla", e);
            } finally {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    world.setAutoSave(autoSave);
                    busyWorlds.remove(world.getName());
                });
            }
        });
    }

    public void resetWorldState(World world) {
        File worldDirectory = world.getWorldFolder();
        File templateDirectory = new File(plugin.getDataFolder(), "templates" + File.separator + world.getName());

	    if (!new File(templateDirectory, "level.dat").isFile()) {
		    Bukkit.getLogger().severe("Template directory does not exist");
		    return;
	    }
        if (!busyWorlds.add(world.getName())) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            boolean unloaded = Bukkit.unloadWorld(world, false);
            if (!unloaded) {
                plugin.getLogger().log(Level.SEVERE, "Could not unload world");
                busyWorlds.remove(world.getName());
                return;
            }

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    restoreFiles(worldDirectory, templateDirectory);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        World restored = Bukkit.createWorld(new WorldCreator(world.getName()));
                        if (restored != null) {
                            worldBorderHandler.startWorldBorder(restored);
                            restored.setPVP(false);
                            busyWorlds.remove(world.getName());
                        } else plugin.getLogger().severe("No se pudo cargar la arena restaurada; permanece bloqueada.");
                    });
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "Restauración fallida; arena bloqueada. Se conserva el mundo anterior.", e);
                }
            });
        });
    }

    static void requireSafePaths(File world, File template) throws IOException {
        var worldPath = world.getCanonicalFile().toPath();
        var templatePath = template.getCanonicalFile().toPath();
        if (worldPath.getParent() == null || templatePath.getParent() == null
                || worldPath.startsWith(templatePath) || templatePath.startsWith(worldPath))
            throw new IOException("Rutas de mundo y plantilla inseguras");
    }

    static void restoreFiles(File world, File template) throws IOException {
        requireSafePaths(world, template);
        world = world.getCanonicalFile();
        template = template.getCanonicalFile();
        if (!new File(template, "level.dat").isFile()) throw new IOException("Plantilla incompleta");
        // Rename on the world's filesystem, even if plugins/templates is on another mount.
        File staging = new File(world.getParentFile(), "." + world.getName() + "-hg-restoring");
        File previous = new File(world.getParentFile(), "." + world.getName() + "-hg-previous");
        requireChild(world.getParentFile(), staging);
        requireChild(world.getParentFile(), previous);
        if (staging.exists()) FileUtils.deleteDirectory(staging);
        FileUtils.copyDirectory(template, staging, file -> !Set.of("session.lock", "uid.dat", "session.dat").contains(file.getName()));
        // Keep the world UUID so saved chest locations and other plugins still resolve it.
        File uid = new File(world, "uid.dat");
        if (uid.isFile()) FileUtils.copyFile(uid, new File(staging, "uid.dat"));
        if (previous.exists()) FileUtils.deleteDirectory(previous);
        Files.move(world.toPath(), previous.toPath());
        try { Files.move(staging.toPath(), world.toPath()); }
        catch (IOException e) { Files.move(previous.toPath(), world.toPath()); throw e; }
    }

    private static void requireChild(File root, File target) throws IOException {
        var parent = root.getCanonicalFile().toPath();
        var path = target.getCanonicalFile().toPath();
        if (path.equals(parent) || !path.startsWith(parent)) throw new IOException("Ruta de restauración fuera de templates");
    }

    public void removeShulkers(World world) {
        NamespacedKey supplyDropKey = new NamespacedKey(plugin, "supplydrop");

        for (Chunk chunk : world.getLoadedChunks()) {
            for (BlockState state : chunk.getTileEntities()) {
                if (state instanceof ShulkerBox shulkerBox) {
                    PersistentDataContainer dataContainer = shulkerBox.getPersistentDataContainer();

                    if (dataContainer.has(supplyDropKey, PersistentDataType.STRING) &&
                            "true".equals(dataContainer.get(supplyDropKey, PersistentDataType.STRING))) {

                        Block block = state.getBlock();
                        block.setType(Material.AIR);
                    }
                } else if (state instanceof EndGateway) {
                    Block block = state.getBlock();
                    block.setType(Material.AIR);
                }
            }
        }

        for (Entity entity : world.getEntities()) {
            if (entity instanceof ArmorStand armorStand) {
                PersistentDataContainer dataContainer = armorStand.getPersistentDataContainer();

                if (dataContainer.has(supplyDropKey, PersistentDataType.STRING) && "true".equals(dataContainer.get(supplyDropKey, PersistentDataType.STRING))) {
                    armorStand.remove();
                }
            }
        }
    }
}
