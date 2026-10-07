package me.aymanisam.hungergames.handlers;

import me.aymanisam.hungergames.HungerGames;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;

public class LangHandler {
    private final HungerGames plugin;

    private final Map<String, YamlConfiguration> langConfigs = new HashMap<>();

    public LangHandler(HungerGames plugin) {
        this.plugin = plugin;
    }

    public String getMessage(Player player, String key, Object... args) {
        YamlConfiguration langConfig;
        if (player != null) {
            langConfig = getLangConfig(player);
        } else {
            langConfig = getLangConfig();
        }

        String message = langConfig.getString(key);
        if (message != null) {
            for (int i = 0; i < args.length; i++) {
                message = message.replace("{" + i + "}", args[i].toString());
            }
            // Change Minecraft & based colors to bukkit colors
            return ChatColor.translateAlternateColorCodes('&', message);
        }

        plugin.getLogger().log(Level.WARNING, "Missing translation for key: " + key + ". For more information on how to fix this error and update language keys, visit: https://hungergames.aymanisam.me/docs/languages/overview#language-errors ");
        return (ChatColor.RED + "Missing translation for " + key);
    }

    public void loadLanguageConfigs() {
        normalizeFileNames();
        saveLanguageFiles();
        File langFolder = new File(plugin.getDataFolder(), "lang");
        File[] langFiles = langFolder.listFiles(file -> file.isFile() && file.getName().endsWith(".yml")
                && file.getName().equals(normalizeLocale(file.getName())));
        langConfigs.clear();
        if (langFiles == null) {
            plugin.getLogger().severe("Could not read language directory: " + langFolder);
            return;
        }
        Arrays.sort(langFiles, Comparator.comparing(File::getName));
        for (File langFile : langFiles) {
            String locale = langFile.getName().replace(".yml", "");
            YamlConfiguration langConfig = YamlConfiguration.loadConfiguration(langFile);
            langConfigs.put(normalizeLocale(locale), langConfig);
        }
    }

    public YamlConfiguration getLangConfig(Player player) {
        if (langConfigs.isEmpty()) {
            loadLanguageConfigs();
        }

        String locale = normalizeLocale(player.getLocale());
        if (langConfigs.containsKey(locale)) {
            return langConfigs.get(locale);
        }

        String languageOnly = locale.split("_")[0];
        for (String key : new TreeSet<>(langConfigs.keySet())) {
            if (key.startsWith(languageOnly + "_")) {
                return langConfigs.get(key);
            }
        }

        return getLangConfig();
    }

    public YamlConfiguration getLangConfig() {
        if (langConfigs.isEmpty()) {
            loadLanguageConfigs();
        }

        YamlConfiguration config = langConfigs.get(defaultLocale());
        if (config == null) {
            config = langConfigs.get("en_us");
            if (config == null) config = bundledLanguage("en_us");
        }

        return config;
    }

    public void saveLanguageFiles() {
        String resourceFolder = "lang";
        File langFolder = new File(plugin.getDataFolder(), resourceFolder);

        // Create a JarFile object from the plugin's file
        try (JarFile jar = new JarFile(plugin.getPluginFile())){
            Enumeration<JarEntry> entries = jar.entries();

            // Iterate over each entry in the JAR file
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().startsWith(resourceFolder + "/") && entry.getName().endsWith(".yml")) {
                    String fileName = new File(entry.getName()).getName();
                    File langFile = new File(langFolder, fileName);
                    if (!langFile.exists()) {
                        plugin.saveResource(resourceFolder + "/" + fileName, false);
                    }
                }
            }
        } catch (IOException | SecurityException e) {
            plugin.getLogger().log(Level.SEVERE, "No permission to create folders", e);
        }
    }

    public void normalizeFileNames() {
        File langFolder = new File(plugin.getDataFolder(), "lang");
        File[] files = langFolder.listFiles();
        if (files == null) return;
        for (File langFile : files) {
            if (!langFile.isFile() || !langFile.getName().toLowerCase(Locale.ROOT).endsWith(".yml")) continue;
            String langFileName = langFile.getName();
            String newFileName = normalizeLocale(langFileName);
            if (!langFileName.equals(newFileName)) {
                File newFile = new File(langFolder, newFileName);
                try {
                    // Linux permits both names. Never replace an existing custom translation.
                    if (newFile.exists() && !Files.isSameFile(langFile.toPath(), newFile.toPath())) {
                        plugin.getLogger().warning("Language files conflict: " + langFileName + " and " + newFileName
                                + "; keeping both and using " + newFileName);
                        continue;
                    }
                    Files.move(langFile.toPath(), newFile.toPath());
                    plugin.getLogger().warning("Migrated legacy language file " + langFileName + " → " + newFileName);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "Could not rename legacy language file " + langFileName, e);
                }
            }
        }
    }

    public void validateLanguageKeys() {
        File langFolder = new File(plugin.getDataFolder(), "lang");
        File[] langFiles = langFolder.listFiles(((dir, name) -> name.endsWith(".yml")));
        if (langFiles == null) {
            return;
        }

        for (File langFile : langFiles) {
            String locale = langFile.getName().substring(0, langFile.getName().length() - 4);
            YamlConfiguration pluginLangConfig = bundledLanguage(normalizeLocale(locale));
            YamlConfiguration langConfig = YamlConfiguration.loadConfiguration(langFile);
            boolean updated = false;

            for (String key : pluginLangConfig.getKeys(true)) {
                if (!langConfig.contains(key)) {
                    langConfig.set(key, pluginLangConfig.get(key));
                    updated = true;
                }
            }

            if (updated) {
                try {
                    langConfig.save(langFile);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "No permission to create folders", e);
                }
            }
        }
    }

    private String defaultLocale() {
        return normalizeLocale(plugin.getConfigHandler().getPluginSettings().getString("default-language", "en_us"));
    }

    private static String normalizeLocale(String locale) {
        return locale == null ? "en_us" : locale.trim().replace('-', '_').toLowerCase(Locale.ROOT);
    }

    private YamlConfiguration bundledLanguage(String locale) {
        InputStream resource = plugin.getResource("lang/" + locale + ".yml");
        if (resource == null) resource = plugin.getResource("lang/en_us.yml");
        if (resource == null) return new YamlConfiguration();
        try (InputStreamReader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read bundled language " + locale, e);
            return new YamlConfiguration();
        }
    }
}
