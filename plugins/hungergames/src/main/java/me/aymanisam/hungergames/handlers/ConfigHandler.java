package me.aymanisam.hungergames.handlers;

import me.aymanisam.hungergames.HungerGames;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;

import static me.aymanisam.hungergames.commands.SignSetCommand.slots;
import static me.aymanisam.hungergames.handlers.SignHandler.signLocations;

public class ConfigHandler {
    private final HungerGames plugin;

    private File worldFile;
	private File signFile;
    private final Map<String, FileConfiguration> worldConfigs = new HashMap<>();
    private FileConfiguration pluginSettings;

    public ConfigHandler(HungerGames plugin) {
        this.plugin = plugin;
    }

    public void createWorldConfig(World world) {
        String worldName = world.getName();
        worldFile = new File(plugin.getDataFolder() + File.separator + worldName, "config.yml");

        File parentDirectory = worldFile.getParentFile();
        if (!parentDirectory.exists()) {
            if (!parentDirectory.mkdirs()) {
                plugin.getLogger().log(Level.SEVERE, "Could not find parent directory for world: " + worldName);
                return;
            }
        }

        if (!worldFile.exists()) {
            try {
                copyDefaultResource("config.yml", worldFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create config file for world " + worldName, e);
            }
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(worldFile);
        worldConfigs.put(world.getName(), config);
    }

    public void createPluginSettings() {
        File file = new File(plugin.getDataFolder(), "settings.yml");
        if (!file.exists()) {
            plugin.saveResource("settings.yml", true);
        }

        pluginSettings = YamlConfiguration.loadConfiguration(file);
    }

    public FileConfiguration getPluginSettings() {
        if (pluginSettings == null) {
            createPluginSettings();
        }
        return pluginSettings;
    }

    public FileConfiguration getWorldConfig(World world) {
        if (!worldConfigs.containsKey(world.getName())) {
            createWorldConfig(world);
        }
        return worldConfigs.get(world.getName());
    }

    public void saveWorldConfig(World world) {
        FileConfiguration configToSave = getWorldConfig(world);
        File fileToSave = new File(plugin.getDataFolder(), world.getName() + File.separator + "config.yml");
        if (configToSave != null && fileToSave != null) {
            try {
                configToSave.save(fileToSave);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not save config file for world " + fileToSave.getName(), e);
            }
        }
    }

    public YamlConfiguration loadItemsConfig(World world) {
        String worldName = world.getName();
        File itemsFile = new File(plugin.getDataFolder() + File.separator + worldName, "items.yml");

        File parentDirectory = itemsFile.getParentFile();
        if (!parentDirectory.exists()) {
            if (!parentDirectory.mkdirs()) {
                plugin.getLogger().log(Level.SEVERE, "Could not find parent directory for world: " + worldName);
                return null;
            }
        }

        if (!itemsFile.exists()) {
            try {
                copyDefaultResource("items.yml", itemsFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create items file for world " + worldName, e);
            }
        }

        return YamlConfiguration.loadConfiguration(itemsFile);
    }

    public FileConfiguration loadSignFile() {
        signFile = new File(plugin.getDataFolder(), "signs.yml");
        if (!signFile.exists()) {
            plugin.saveResource("signs.yml", false);
        }
        return YamlConfiguration.loadConfiguration(signFile);
    }

	public void saveSignLocations() {
		FileConfiguration config = loadSignFile();
		List<String> locations = new ArrayList<>();
		for (Map.Entry<String, Location> entry : signLocations.entrySet()) {
			Location location = entry.getValue();
			String locString = Objects.requireNonNull(location.getWorld()).getName() + "," + location.getX() + "," + location.getY() + "," + location.getZ() + "," + entry.getKey();
			locations.add(locString);
		}
		config.set("signs", locations);
		try {
			config.save(signFile);
		} catch (IOException e) {
			plugin.getLogger().log(Level.SEVERE, "Could not save sign.yml", e);
		}
	}

	public void loadSignLocations() {
		signLocations.clear();
		List<String> locations = loadSignFile().getStringList("signs");
		for (String locString : locations) {
			try {
                String[] parts = locString.split(",", -1);
                if (parts.length != 5 || parts[4].isBlank()) throw new IllegalArgumentException("Expected world,x,y,z,slot");
                World world = Bukkit.getWorld(parts[0]);
                if (world == null) {
                    plugin.getLogger().warning("Sign world is not loaded: " + parts[0] + "; check its name and capitalization in signs.yml.");
                    continue;
                }
                double x = Double.parseDouble(parts[1]), y = Double.parseDouble(parts[2]), z = Double.parseDouble(parts[3]);
                if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException("Invalid coordinates");
                signLocations.put(parts[4], new Location(world, x, y, z));
			} catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Skipping invalid signs.yml entry: " + locString + " (" + e.getMessage() + ")");
			}
		}
	}

	public void loadSlots() {
		slots.clear();
		for (String slot : loadSignFile().getStringList("slots")) {
            String[] parts = slot.split(",", -1);
            if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()) slots.put(parts[0], parts[1]);
            else plugin.getLogger().warning("Skipping invalid signs.yml slot: " + slot);
		}
	}

	public void setSlots() {
		List<String> items = new ArrayList<>();
		FileConfiguration config = loadSignFile();

		for (Map.Entry<String, String> entry : slots.entrySet()) {
			items.add(entry.getKey() + "," + entry.getValue());
		}

		config.set("slots", items);

		try {
			config.save(signFile);
		} catch (IOException e) {
			plugin.getLogger().log(Level.SEVERE, "Could not save sign.yml", e);
		}
	}

    public void validateConfigKeys(World world) {
        File serverConfigFile = new File(plugin.getDataFolder() + File.separator + world.getName(), "config.yml");
        worldConfigs.put(world.getName(), validateKeys("config.yml", serverConfigFile));
    }

    public void validateSettingsKeys() {
        File serverSettingsFile = new File(plugin.getDataFolder(), "settings.yml");
        pluginSettings = validateKeys("settings.yml", serverSettingsFile);
    }

    private YamlConfiguration validateKeys(String resourceName, File file) {
        try (InputStreamReader reader = new InputStreamReader(
                Objects.requireNonNull(plugin.getResource(resourceName)), StandardCharsets.UTF_8)) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
            YamlConfiguration config = new YamlConfiguration();
            // Loading strictly prevents replacing a malformed migrated file with defaults.
            if (file.exists()) config.load(file);
            for (String key : defaults.getKeys(true)) {
                if (!defaults.isConfigurationSection(key) && !config.isSet(key)) config.set(key, defaults.get(key));
            }
            config.save(file);
            return config;
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException e) {
            throw new IllegalStateException("Could not validate " + file + "; check YAML syntax and file permissions", e);
        }
    }

    public void copyDefaultResource(String resourceName, File target) throws IOException {
        Files.createDirectories(target.toPath().getParent());
        try (InputStream resource = plugin.getResource(resourceName)) {
            if (resource == null) throw new IOException("Missing bundled resource: " + resourceName);
            Files.copy(resource, target.toPath());
        }
    }
}
