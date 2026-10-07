package me.aymanisam.hungergames.handlers;

import me.aymanisam.hungergames.HungerGames;
import me.aymanisam.hungergames.listeners.TeamVotingListener;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

import static me.aymanisam.hungergames.HungerGames.*;

public class SetSpawnHandler {
    private final HungerGames plugin;
    private final ResetPlayerHandler resetPlayerHandler;
    private final LangHandler langHandler;
    private final TeamVotingListener teamVotingListener;
    private final ConfigHandler configHandler;
    private final SignHandler signHandler;
	private CountDownHandler countDownHandler;

    public FileConfiguration setSpawnConfig;
    public Map<String, List<String>> spawnPoints;
    public static Map<String, Map<String, Player>> spawnPointMap;
    public Map<String, List<Player>> playersWaiting;
    private File setSpawnFile;
    public static final Map<String, List<BukkitTask>> autoStartTasks = new HashMap<>();

    public SetSpawnHandler(HungerGames plugin, LangHandler langHandler) {
        this.plugin = plugin;
        this.langHandler = langHandler;
        this.spawnPoints = new HashMap<>();
        spawnPointMap = new HashMap<>();
        this.playersWaiting = new HashMap<>();
        this.resetPlayerHandler = new ResetPlayerHandler();
        this.teamVotingListener = new TeamVotingListener(langHandler);
        this.configHandler = plugin.getConfigHandler();
        this.signHandler = new SignHandler(plugin, this);
    }

    public void setCountDownHandler(CountDownHandler countDownHandler) {
        this.countDownHandler = countDownHandler;
    }

    public void createSetSpawnConfig(World world) {
        File worldFolder = new File(plugin.getDataFolder() + File.separator + world.getName());
        setSpawnFile = new File(worldFolder, "setspawn.yml");

        File parentDirectory = setSpawnFile.getParentFile();
        if (!parentDirectory.exists()) {
            if (!parentDirectory.mkdirs()) {
                plugin.getLogger().log(Level.SEVERE, "Could not find parent directory for world: " + world.getName());
                return;
            }
        }

        if (!setSpawnFile.exists()) {
            try {
                configHandler.copyDefaultResource("setspawn.yml", setSpawnFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create spawnpoint file for world " + setSpawnFile, e);
            }
        }

        setSpawnConfig = YamlConfiguration.loadConfiguration(setSpawnFile);
        List<String> worldSpawnPoints = setSpawnConfig.getStringList("spawnpoints");
        spawnPoints.put(world.getName(), worldSpawnPoints);
    }

    public void saveSetSpawnConfig(World world) {
        if (setSpawnConfig == null || setSpawnFile == null) {
            return;
        }
        try {
            List<String> worldSpawnPoints = spawnPoints.computeIfAbsent(world.getName(), k -> new ArrayList<>());

            setSpawnConfig.set("spawnpoints", worldSpawnPoints);

            setSpawnConfig.save(setSpawnFile);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save config to the specified location." + setSpawnFile, ex);
        }
    }

    public String assignPlayerToSpawnPoint(Player player, World world) {
        createSetSpawnConfig(world);

        List<String> worldSpawnPoints = spawnPoints.computeIfAbsent(world.getName(), k -> new ArrayList<>());

        List<String> shuffledSpawnPoints = new ArrayList<>(worldSpawnPoints);
        Collections.shuffle(shuffledSpawnPoints);

        Map<String, Player> worldSpawnPointMap = spawnPointMap.computeIfAbsent(world.getName(), k -> new HashMap<>());

        if (configHandler.getPluginSettings().getBoolean("custom-teams")) {
            int playerIndex = 0;
            for (Map.Entry<String, List<Player>> entry : customTeams.entrySet()) {
                List<Player> team = entry.getValue();
                if (team.contains(player)) {
                    playerIndex += team.indexOf(player);
                    if (playerIndex < worldSpawnPoints.size()) {
                        return worldSpawnPoints.get(playerIndex);
                    }
                }
                playerIndex += team.size();
            }
        } else {
            Collections.shuffle(shuffledSpawnPoints);

            for (String spawnPoint : shuffledSpawnPoints) {
                if (!worldSpawnPointMap.containsKey(spawnPoint)) {
                    return spawnPoint;
                }
            }
        }

        player.sendMessage(langHandler.getMessage(player, "game.join-fail"));
        return null;
    }

    public void removePlayerFromSpawnPoint(Player player, World world) {
        Map<String, Player> worldSpawnPointMap = spawnPointMap.computeIfAbsent(world.getName(), k -> new HashMap<>());
        worldSpawnPointMap.values().removeIf(registered -> ParticipationHandler.samePlayer(registered, player));
        playersWaiting.getOrDefault(world.getName(), new ArrayList<>())
                .removeIf(registered -> ParticipationHandler.samePlayer(registered, player));
    }

    public boolean teleportPlayerToSpawnpoint(Player player, World world) {
        String spawnPoint = assignPlayerToSpawnPoint(player, world);

        if (spawnPoint == null) {
            return false;
        }

        Map<String, Player> worldSpawnPointMap = spawnPointMap.computeIfAbsent(world.getName(), k -> new HashMap<>());
        List<Player> worldPlayersWaiting = playersWaiting.computeIfAbsent(world.getName(), k -> new ArrayList<>());
        List<String> worldSpawnPoints = spawnPoints.computeIfAbsent(world.getName(), k -> new ArrayList<>());

        String[] coords = spawnPoint.split(",");
        if (coords.length != 4 || !coords[0].equals(world.getName())) {
            plugin.getLogger().warning("Invalid spawnpoint in " + world.getName() + ": " + spawnPoint);
            player.sendMessage(langHandler.getMessage(player, "game.join-fail"));
            return false;
        }
        try {
            double x = Double.parseDouble(coords[1]) + 0.5;
            double y = Double.parseDouble(coords[2]) + 1.0;
            double z = Double.parseDouble(coords[3]) + 0.5;
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("Spawn coordinates must be finite");
            }

            Location teleportLocation = new Location(world, x, y, z);

            Location spawnLocation = world.getSpawnLocation();

            Vector direction = spawnLocation.toVector().subtract(teleportLocation.toVector());

            float yaw = (float) (Math.toDegrees(Math.atan2(direction.getZ(), direction.getX())) - 90);

            teleportLocation.setYaw(yaw);

            if (!player.teleport(teleportLocation)) {
                player.sendMessage(langHandler.getMessage(player, "game.join-fail"));
                return false;
            }
            resetPlayerHandler.resetPlayer(player);
        } catch (RuntimeException | LinkageError ex) {
            plugin.getLogger().log(Level.SEVERE, "Cannot join arena " + world.getName() + " for " + player.getName(), ex);
            player.sendMessage(langHandler.getMessage(player, "game.join-fail"));
            return false;
        }
        worldSpawnPointMap.put(spawnPoint, player);
        worldPlayersWaiting.add(player);
        signHandler.setSignContent();

        for (Player onlinePlayer : world.getPlayers()) {
            onlinePlayer.sendMessage(langHandler.getMessage(onlinePlayer, "setspawn.joined-message", player.getName(), worldSpawnPointMap.size(), worldSpawnPoints.size()));
        }

        if (configHandler.getWorldConfig(world).getBoolean("voting")) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                if (worldSpawnPointMap.containsValue(player) && player.getWorld().equals(world)
                        && !isGameStartingOrStarted(world.getName())) {
                    teamVotingListener.openVotingInventory(player);
                }
            }, 100L);
        }

        if (configHandler.getWorldConfig(world).getBoolean("auto-start.enabled")) {
            if (ParticipationHandler.count(worldSpawnPointMap.values()) >= configHandler.getWorldConfig(world).getInt("auto-start.players")) {
                List<BukkitTask> worldAutoStartTasks = autoStartTasks.computeIfAbsent(world.getName(), k -> new ArrayList<>());

                if (!worldAutoStartTasks.isEmpty()) {
                    return true;
                }

                int autoStartDelay = configHandler.getWorldConfig(world).getInt("auto-start.delay");
                for (Player currentPlayer : world.getPlayers()) {
                    currentPlayer.sendMessage(langHandler.getMessage(currentPlayer, "game.auto-start", autoStartDelay));
                }

                BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    autoStartTasks.remove(world.getName());
                    if (isGameStartingOrStarted(world.getName())
                            || ParticipationHandler.count(worldSpawnPointMap.values()) < configHandler.getWorldConfig(world).getInt("min-players")) return;
                    gameStarting.put(world.getName(), true);
                    countDownHandler.startCountDown(world);
                }, autoStartDelay * 20L);
                worldAutoStartTasks.add(task);
            }
        }
        return true;
    }

    public void checkEnoughPlayers(World world) {
        Map<String, Player> worldSpawnPointMap = spawnPointMap.computeIfAbsent(world.getName(), k -> new HashMap<>());

        int minPlayers = configHandler.getWorldConfig(world).getInt("min-players");

        if (ParticipationHandler.count(worldSpawnPointMap.values()) < minPlayers) {
            if (gameStarting.getOrDefault(world.getName(), false)) {
                countDownHandler.cancelCountDown(world);
                for (Player p : world.getPlayers()) {
                    p.sendMessage(langHandler.getMessage(p, "startgame.cancelled"));
                }
            }
        }
    }
}
