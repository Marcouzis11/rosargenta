package me.aymanisam.hungergames;

import me.aymanisam.hungergames.handlers.*;
import me.aymanisam.hungergames.listeners.*;
import me.aymanisam.hungergames.stats.DatabaseHandler;
import me.aymanisam.hungergames.stats.HungerGamesExpansion;
import me.aymanisam.hungergames.stats.PlayerStatsHandler;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import static me.aymanisam.hungergames.handlers.VersionHandler.getLatestPluginVersion;

public final class HungerGames extends JavaPlugin {
    public static Map<String, Boolean> gameStarted = new HashMap<>();
    public static Map<String, Boolean> gameStarting = new HashMap<>();
    public static List<String> hgWorldNames = new ArrayList<>();
    public static List<String> worldNames = new ArrayList<>();
    public static Map<Player, Long> totalTimeSpent = new HashMap<>();
    public static Map<String, List<Player>> customTeams = new HashMap<>();
	public static Map<UUID, PlayerStatsHandler> statsMap = new ConcurrentHashMap<>();
	public static Map<String, LinkedHashMap<UUID, Double>> leaderboards = new ConcurrentHashMap<>();
    public static boolean teamsFinalized = false;

    private GameSequenceHandler gameSequenceHandler;
    private ConfigHandler configHandler;
    private ArenaMobListener arenaMobListener;
    private SupplyDropTracker supplyDropTracker;
    private DatabaseHandler database;
    private boolean databaseReady;

    public DatabaseHandler getDatabase() {
        return database;
    }

    public boolean isDatabaseEnabled() {
        return databaseReady && configHandler.getPluginSettings().getBoolean("database.enabled");
    }

    @Override
    public void onEnable() {

        getServer().getConsoleSender().sendMessage("""
                \n
                
                 ██░ ██  █    ██  ███▄    █   ▄████ ▓█████  ██▀███    ▄████  ▄▄▄       ███▄ ▄███▓▓█████   ██████\s
                ▓██░ ██▒ ██  ▓██▒ ██ ▀█   █  ██▒ ▀█▒▓█   ▀ ▓██ ▒ ██▒ ██▒ ▀█▒▒████▄    ▓██▒▀█▀ ██▒▓█   ▀ ▒██    ▒\s
                ▒██▀▀██░▓██  ▒██░▓██  ▀█ ██▒▒██░▄▄▄░▒███   ▓██ ░▄█ ▒▒██░▄▄▄░▒██  ▀█▄  ▓██    ▓██░▒███   ░ ▓██▄  \s
                ░▓█ ░██ ▓▓█  ░██░▓██▒  ▐▌██▒░▓█  ██▓▒▓█  ▄ ▒██▀▀█▄  ░▓█  ██▓░██▄▄▄▄██ ▒██    ▒██ ▒▓█  ▄   ▒   ██▒
                ░▓█▒░██▓▒▒█████▓ ▒██░   ▓██░░▒▓███▀▒░▒████▒░██▓ ▒██▒░▒▓███▀▒ ▓█   ▓██▒▒██▒   ░██▒░▒████▒▒██████▒▒
                 ▒ ░░▒░▒░▒▓▒ ▒ ▒ ░ ▒░   ▒ ▒  ░▒   ▒ ░░ ▒░ ░░ ▒▓ ░▒▓░ ░▒   ▒  ▒▒   ▓▒█░░ ▒░   ░  ░░░ ▒░ ░▒ ▒▓▒ ▒ ░
                 ▒ ░▒░ ░░░▒░ ░ ░ ░ ░░   ░ ▒░  ░   ░  ░ ░  ░  ░▒ ░ ▒░  ░   ░   ▒   ▒▒ ░░  ░      ░ ░ ░  ░░ ░▒  ░ ░
                 ░  ░░ ░ ░░░ ░ ░    ░   ░ ░ ░ ░   ░    ░     ░░   ░ ░ ░   ░   ░   ▒   ░      ░      ░   ░  ░  ░ \s
                 ░  ░  ░   ░              ░       ░    ░  ░   ░           ░       ░  ░       ░      ░  ░      ░ \s
                                                                                                                \s
                """);

        // Bstats
        int bstatsPluginId = 21512;
        new Metrics(this, bstatsPluginId);

	    this.configHandler = new ConfigHandler(this);
	    configHandler.validateSettingsKeys();
        supplyDropTracker = new SupplyDropTracker(this);

        // Initializing shared classes
	    LangHandler langHandler = new LangHandler(this);
        TeamVotingListener teamVotingListener = new TeamVotingListener(langHandler);
        getServer().getPluginManager().registerEvents(teamVotingListener, this);
        ArenaHandler arenaHandler = new ArenaHandler(this, langHandler);
        ScoreBoardHandler scoreBoardHandler = new ScoreBoardHandler(this, langHandler);
        SetSpawnHandler setSpawnHandler = new SetSpawnHandler(this, langHandler);
        CompassHandler compassHandler = new CompassHandler(langHandler);
        CompassListener compassListener = new CompassListener(this, langHandler, compassHandler);
        TeamsHandler teamsHandler = new TeamsHandler(this, langHandler);
        this.gameSequenceHandler = new GameSequenceHandler(this, langHandler, setSpawnHandler, compassListener, teamsHandler);
        CountDownHandler countDownHandler = new CountDownHandler(this, langHandler, gameSequenceHandler, teamVotingListener);
        setSpawnHandler.setCountDownHandler(countDownHandler);
        WorldBorderHandler worldBorderHandler = new WorldBorderHandler(this, langHandler);
	    langHandler.normalizeFileNames();
	    langHandler.saveLanguageFiles();
	    langHandler.validateLanguageKeys();
	    langHandler.loadLanguageConfigs();

        if (configHandler.getPluginSettings().getBoolean("database.enabled")) {
            // Database
            try {
                this.database = new DatabaseHandler(this);
                database.initializeDatabase();
                database.changeSecondsPlayedType();
                databaseReady = true;
            } catch (SQLException e) {
                this.getLogger().log(Level.SEVERE, "Statistics unavailable: could not initialize MySQL. HungerGames will continue without statistics.", e);
                if (database != null) database.closeConnection();
            }

            if (isDatabaseEnabled()) {
                int interval = Math.max(1, configHandler.getPluginSettings().getInt("database.interval", 60));
                getServer().getScheduler().runTaskTimer(this, () -> saveToDatabase(false), 20L * interval, 20L * interval);
            }
        }

		if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null && isDatabaseEnabled()) {
			new HungerGamesExpansion(this, setSpawnHandler).register();
			try {
				database.getPlayerLeaderboards();
			} catch (SQLException e) {
                getLogger().log(Level.WARNING, "Could not load statistics leaderboards", e);
			}
		}

        // Registering command handler
        Objects.requireNonNull(getCommand("hg")).setExecutor(new CommandDispatcher(this, langHandler, setSpawnHandler, gameSequenceHandler, teamsHandler, scoreBoardHandler, countDownHandler, worldBorderHandler));

        // Registering Listeners
        ArenaSelectListener arenaSelectListener = new ArenaSelectListener(this, langHandler);
        getServer().getPluginManager().registerEvents(arenaSelectListener, this);

        SetSpawnListener setSpawnListener = new SetSpawnListener(this, langHandler, setSpawnHandler, arenaHandler, scoreBoardHandler);
        getServer().getPluginManager().registerEvents(setSpawnListener, this);

        SignClickListener signClickListener = new SignClickListener(this, langHandler, setSpawnHandler, arenaHandler, scoreBoardHandler);
        getServer().getPluginManager().registerEvents(signClickListener, this);

        PlayerListener playerListener = new PlayerListener(this, langHandler, setSpawnHandler);
        getServer().getPluginManager().registerEvents(playerListener, this);

        SpectateGuiListener spectateGuiListener = new SpectateGuiListener(langHandler);
        getServer().getPluginManager().registerEvents(spectateGuiListener, this);

        getServer().getPluginManager().registerEvents(compassListener, this);

        TeamChatListener teamChatListener = new TeamChatListener(teamsHandler);
        getServer().getPluginManager().registerEvents(teamChatListener, this);

        BlockBreakListener blockBreakListener = new BlockBreakListener(this);
        getServer().getPluginManager().registerEvents(blockBreakListener, this);

	    loadWorldFiles();

        arenaMobListener = new ArenaMobListener(this);
        getServer().getPluginManager().registerEvents(arenaMobListener, this);
        getServer().getWorlds().forEach(arenaMobListener::protectConfiguredWorld);

        // Checks if the current version is the latest version
        int spigotPluginId = 111936;

        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            String latestVersionString = getLatestPluginVersion(spigotPluginId);
            if (latestVersionString == null) {
                getLogger().warning("Failed to check for updates");
                return;
            }
            int latestHyphenIndex = latestVersionString.indexOf('-');
            String latestVersion = (latestHyphenIndex != -1) ? latestVersionString.substring(0, latestHyphenIndex) : latestVersionString;

            String currentVersionString = this.getDescription().getVersion();
            int currentHyphenIndex = currentVersionString.indexOf('-');
            String currentVersion = (currentHyphenIndex != -1) ? currentVersionString.substring(0, currentHyphenIndex) : currentVersionString;

            if (!Objects.equals(latestVersion, currentVersion)) {
                this.getLogger().log(Level.WARNING, "You are not running the latest version of HungerGames! ");
                this.getLogger().log(Level.WARNING, "Please update your plugin to the latest version " + "\u001B[36m" + latestVersion + "\u001B[33m" + " for the best experience and bug fixes.");
                this.getLogger().log(Level.WARNING, "https://modrinth.com/plugin/hungergames/versions#all-versions");
            }
        });

        TipsHandler tipsHandler = new TipsHandler(this, langHandler);
        if (configHandler.getPluginSettings().getBoolean("tips")) {
            tipsHandler.startSendingTips(600);
        }

        configHandler.loadSignLocations();
		configHandler.loadSlots();
    }

	public void loadWorldFiles() {
		File serverDirectory = getServer().getWorldContainer();
		File[] files = serverDirectory.listFiles();
        Set<String> discovered = new TreeSet<>();
        getServer().getWorlds().forEach(world -> discovered.add(world.getName()));

		if (files != null) {
		    for (File file : files) {
		        if (file.isDirectory() && !file.isHidden()) {
		            File levelDat = new File(file, "level.dat");
		            if (levelDat.exists()) {
		                String worldName = file.getName();
                        discovered.add(worldName);
		            }
		        }
		    }
		}
        worldNames.clear();
        worldNames.addAll(discovered);
        hgWorldNames.clear();
        FileConfiguration settings = getConfigHandler().getPluginSettings();
        String lobby = settings.getString("lobby-world", "world");
        List<String> configured = settings.getStringList("ignored-worlds");
        for (String name : discovered) {
            if (!name.equals(lobby) && configured.contains(name) == settings.getBoolean("whitelist-worlds")) {
                hgWorldNames.add(name);
            }
        }
	}

	public ConfigHandler getConfigHandler() {
        return configHandler;
    }

    public ArenaMobListener getArenaMobListener() {
        return arenaMobListener;
    }

    public SupplyDropTracker getSupplyDropTracker() {
        return supplyDropTracker;
    }

    @Override
    public void onDisable() {
        if (gameSequenceHandler != null) {
            for (World world: Bukkit.getWorlds()) {
                try {
                    gameSequenceHandler.endGame(true, world);
                } catch (RuntimeException e) {
                    getLogger().log(Level.SEVERE, "Could not finish arena during shutdown: " + world.getName(), e);
                }
            }
        }

	    if (isDatabaseEnabled()) {
		    saveToDatabase(true);
	    }

        if (this.database != null) {
            this.database.closeConnection();
        }
        databaseReady = false;
    }

    public File getPluginFile() {
        return this.getFile();
    }

    public static boolean isGameStartingOrStarted(String worldName) {
        return gameStarted.getOrDefault(worldName, false) ||
                gameStarting.getOrDefault(worldName, false);
    }

	private void saveToDatabase(Boolean stopping) {
        if (!isDatabaseEnabled()) return;
		Runnable saveTask = () -> {
			try {
				for (PlayerStatsHandler playerStats : statsMap.values()) {
					if (playerStats.isDirty()) {
						getDatabase().updatePlayerStats(playerStats);
						playerStats.setClean();
					}
				}
			} catch (SQLException e) {
				this.getLogger().log(Level.SEVERE, e.toString());
			}
		};

		if (stopping) {
			saveTask.run();
		} else {
			getServer().getScheduler().runTaskAsynchronously(this, saveTask);
		}
	}
}
