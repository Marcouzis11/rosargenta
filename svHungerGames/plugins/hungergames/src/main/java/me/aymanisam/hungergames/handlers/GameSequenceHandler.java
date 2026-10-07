package me.aymanisam.hungergames.handlers;

import me.aymanisam.hungergames.HungerGames;
import me.aymanisam.hungergames.listeners.CompassListener;
import me.aymanisam.hungergames.stats.PlayerStatsHandler;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

import static me.aymanisam.hungergames.HungerGames.*;
import static me.aymanisam.hungergames.handlers.CountDownHandler.playersPerTeam;
import static me.aymanisam.hungergames.handlers.ScoreBoardHandler.boards;
import static me.aymanisam.hungergames.handlers.SetSpawnHandler.spawnPointMap;
import static me.aymanisam.hungergames.handlers.TeamsHandler.teams;
import static me.aymanisam.hungergames.handlers.TeamsHandler.teamsAlive;
import static me.aymanisam.hungergames.listeners.PlayerListener.playerKills;
import static me.aymanisam.hungergames.listeners.TeamVotingListener.playerVotes;

public class GameSequenceHandler {
    private final HungerGames plugin;
    private final LangHandler langHandler;
    private final SetSpawnHandler setSpawnHandler;
    private final WorldBorderHandler worldBorderHandler;
    private final ScoreBoardHandler scoreBoardHandler;
    private final ResetPlayerHandler resetPlayerHandler;
    private final ConfigHandler configHandler;
    private final WorldResetHandler worldResetHandler;
    private final CompassListener compassListener;
    private final TeamsHandler teamsHandler;
    private final SignHandler signHandler;

    public Map<String, Integer> gracePeriodTaskId = new HashMap<>();
    public Map<String, Integer> timerTaskId = new HashMap<>();
    public static Map<String, Integer> timeLeft = new HashMap<>();
    public static final Set<String> deathmatchWorlds = new HashSet<>();
    public static final Set<String> celebratingWorlds = new HashSet<>();
    public static final Set<String> preparingDeathmatchWorlds = new HashSet<>();
    private final Map<String, BukkitTask> deathmatchPreparationTasks = new HashMap<>();
    private final Map<String, List<BukkitTask>> celebrationTasks = new HashMap<>();
    private final Map<String, List<Firework>> celebrationRockets = new HashMap<>();
    private final Map<String, Map<UUID, Location>> matchSpawns = new HashMap<>();
    private final Map<String, List<Location>> matchPlatforms = new HashMap<>();
    public Map<String, BukkitTask> chestRefillTask = new HashMap<>();
    public Map<String, BukkitTask> supplyDropTask = new HashMap<>();
    public static Map<String, List<Player>> playersAlive = new HashMap<>();
    public static Map<String, List<Player>> startingPlayers = new HashMap<>();
    public static Map<String, Map<Player, BossBar>> playerBossBars = new HashMap<>();
    public static Map<String, List<Player>> playerPlacements = new HashMap<>();
    public static Map<String, List<List<Player>>> teamPlacements = new HashMap<>();

    public GameSequenceHandler(HungerGames plugin, LangHandler langHandler, SetSpawnHandler setSpawnHandler, CompassListener compassListener, TeamsHandler teamsHandler) {
        this.plugin = plugin;
        this.langHandler = langHandler;
        this.setSpawnHandler = setSpawnHandler;
        this.worldBorderHandler = new WorldBorderHandler(plugin, langHandler);
        this.scoreBoardHandler = new ScoreBoardHandler(plugin, langHandler);
        this.resetPlayerHandler = new ResetPlayerHandler();
        this.configHandler = plugin.getConfigHandler();
        this.worldResetHandler = new WorldResetHandler(plugin, worldBorderHandler);
        this.compassListener = compassListener;
        this.teamsHandler = teamsHandler;
        this.signHandler = new SignHandler(plugin, setSpawnHandler);
    }

    public void startGame(World world) {
        ParticipationHandler.removeSpectators(world);
        List<String> errors = new ArenaValidationHandler(plugin).validate(world,
                setSpawnHandler.spawnPoints.getOrDefault(world.getName(), List.of()),
                playersAlive.getOrDefault(world.getName(), List.of()).size());
        if (!errors.isEmpty()) {
            gameStarting.put(world.getName(), false);
            for (Player player : world.getPlayers()) player.sendMessage("§cNo se puede iniciar: " + String.join(" | ", errors));
            return;
        }
        celebratingWorlds.remove(world.getName());
        deathmatchWorlds.remove(world.getName());
        gameStarted.put(world.getName(), true);
        gameStarting.put(world.getName(), false);
        world.setPVP(false);

        Map<String, Player> worldSpawnPointMap = spawnPointMap.computeIfAbsent(world.getName(), k -> new HashMap<>());
        List<Player> worldPlayersWaiting = setSpawnHandler.playersWaiting.computeIfAbsent(world.getName(), k -> new ArrayList<>());
        List<Player> worldPlayersAlive = playersAlive.computeIfAbsent(world.getName(), k -> new ArrayList<>());
        List<Player> worldStartingPlayers = startingPlayers.computeIfAbsent(world.getName(), k -> new ArrayList<>());

        Map<UUID, Location> assignedSpawns = new HashMap<>();
        for (Map.Entry<String, Player> entry : worldSpawnPointMap.entrySet()) {
            String[] coords = entry.getKey().split(",");
            Location location = new Location(world, Double.parseDouble(coords[1]) + 0.5,
                    Double.parseDouble(coords[2]) + 1.0, Double.parseDouble(coords[3]) + 0.5);
            location.setDirection(world.getSpawnLocation().toVector().subtract(location.toVector()));
            location.setPitch(0);
            assignedSpawns.put(entry.getValue().getUniqueId(), location);
        }
        matchSpawns.put(world.getName(), assignedSpawns);
        List<Location> platforms = new ArrayList<>();
        for (String point : setSpawnHandler.spawnPoints.getOrDefault(world.getName(), Collections.emptyList())) {
            String[] coords = point.split(",");
            platforms.add(new Location(world, Double.parseDouble(coords[1]) + 0.5,
                    Double.parseDouble(coords[2]) + 1.0, Double.parseDouble(coords[3]) + 0.5));
        }
        matchPlatforms.put(world.getName(), platforms);
        worldPlayersWaiting.clear();
        worldSpawnPointMap.clear();

        signHandler.setSignContent();

        worldBorderHandler.startWorldBorder(world);

        for (Player player : world.getPlayers()) {
            player.sendTitle("", langHandler.getMessage(player, "game.start"), 5, 20, 10);
            player.sendMessage(langHandler.getMessage(player, "game.grace-start"));
        }

        int gracePeriod = configHandler.getWorldConfig(world).getInt("grace-period");
        int worldGracePeriodTaskId = plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            world.setPVP(true);
            for (Player player : world.getPlayers()) {
                player.sendMessage(langHandler.getMessage(player, "game.grace-end"));
                player.sendTitle("", langHandler.getMessage(player, "game.grace-end"), 5, 20, 10);
                player.playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 1.0f);
            }
        }, gracePeriod * 20L);

        gracePeriodTaskId.put(world.getName(), worldGracePeriodTaskId);

        for (Player player : worldPlayersAlive) {
            worldStartingPlayers.add(player);

            if (configHandler.getWorldConfig(world).getBoolean("break-blocks.enabled")) {
                player.setGameMode(GameMode.SURVIVAL);
            }

            if (configHandler.getPluginSettings().getBoolean("database.enabled")) {
	            PlayerStatsHandler playerStats = statsMap.get(player.getUniqueId());

	            if (playersPerTeam != 1) {
	                playerStats.setTeamGamesPlayed(playerStats.getTeamGamesPlayed() + 1);
	            } else {
	                playerStats.setSoloGamesPlayed(playerStats.getSoloGamesPlayed() + 1);
	            }

	            playerStats.setDirty();
            }

            Long timeSpent = totalTimeSpent.getOrDefault(player, 0L);
            totalTimeSpent.put(player, timeSpent);

            if (configHandler.getWorldConfig(world).getBoolean("display-bossbar")) {
                BossBar bossBar = plugin.getServer().createBossBar(langHandler.getMessage(player, "time-remaining"), BarColor.GREEN, BarStyle.SOLID);
                bossBar.addPlayer(player);

                Map<Player, BossBar> worldPlayerBossBars = playerBossBars.computeIfAbsent(world.getName(), k -> new HashMap<>());

                worldPlayerBossBars.put(player, bossBar);
            }

            if (configHandler.getWorldConfig(world).getBoolean("bedrock-buff.enabled") && player.getName().startsWith(".")) {
                List<String> effectNames = configHandler.getWorldConfig(world).getStringList("bedrock-buff.effects");
                for (String effectName : effectNames) {
                    PotionEffectType effectType = PotionEffectType.getByName(effectName);
                    if (effectType != null) {
                        player.addPotionEffect(new PotionEffect(effectType, 200000, 1, true, false));
                    }
                }
            }
        }

        int supplyDropInterval = configHandler.getWorldConfig(world).getInt("supplydrop.interval") * 20;
        SupplyDropHandler supplyDropHandler = new SupplyDropHandler(plugin, langHandler);

        BukkitTask worldSupplyDropTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> supplyDropHandler.setSupplyDrop(world), supplyDropInterval, supplyDropInterval);
        supplyDropTask.put(world.getName(), worldSupplyDropTask);

        int chestRefillInterval = configHandler.getWorldConfig(world).getInt("chestrefill.interval") * 20;
        ChestRefillHandler chestRefillHandler = new ChestRefillHandler(plugin, langHandler);

        BukkitTask worldChestRefillTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> chestRefillHandler.refillChests(world), 0, chestRefillInterval);
        chestRefillTask.put(world.getName(), worldChestRefillTask);

        mainGame(world);
    }

    public void mainGame(World world) {
        int initialTimeLeft = configHandler.getWorldConfig(world).getInt("game-time");
        timeLeft.put(world.getName(), initialTimeLeft);

        for (Player player: world.getPlayers()) {
            scoreBoardHandler.createBoard(player);
        }

        List<List<Player>> worldTeamsAlive = teamsAlive.computeIfAbsent(world.getName(), k -> new ArrayList<>());
        List<Player> worldPlayersAlive = playersAlive.computeIfAbsent(world.getName(), k -> new ArrayList<>());

        boolean displayBossbars = configHandler.getWorldConfig(world).getBoolean("display-bossbar");

        int worldTimerTaskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (celebratingWorlds.contains(world.getName())) return;
            ParticipationHandler.removeSpectators(world);
            if (displayBossbars) {
                updateBossBars(world);
            }
            int currentTimeLeft = timeLeft.get(world.getName());
            currentTimeLeft--;
            timeLeft.put(world.getName(), currentTimeLeft);

            for (Player player: world.getPlayers()) {
                scoreBoardHandler.updateBoard(boards.get(player.getUniqueId()), world);
            }

            if (configHandler.getWorldConfig(world).getInt("players-per-team", 1) != 1) {
                if (worldTeamsAlive.size() <= 1) {
                    endGameWithTeams(world);
                    return;
                }
            } else {
                if (worldPlayersAlive.size() <= 1) {
                    endGameWithPlayers(world);
                    return;
                }
            }

            if (!deathmatchWorlds.contains(world.getName()) && (currentTimeLeft == 60 || currentTimeLeft == 30 || currentTimeLeft == 10)) {
                for (Player player : world.getPlayers()) player.sendMessage("§ePelea final en " + currentTimeLeft + " segundos.");
            }

            if (currentTimeLeft <= 0 && !deathmatchWorlds.contains(world.getName())) {
                handleTimeUp(world);
            }
        }, 20L, 20L);
        timerTaskId.put(world.getName(), worldTimerTaskId);

        runCustomGlobalCommands(false, world);
        runCustomPlayerCommands(false, world, worldPlayersAlive);
    }

    private void updateBossBars(World world) {
        Map<Player, BossBar> worldPlayerBossBars = playerBossBars.computeIfAbsent(world.getName(), k -> new HashMap<>());

        int worldTimeLeft = timeLeft.get(world.getName());

        for (Map.Entry<Player, BossBar> entry : worldPlayerBossBars.entrySet()) {
            Player player = entry.getKey();
            BossBar bossBar = entry.getValue();
            if (deathmatchWorlds.contains(world.getName())) {
                bossBar.setColor(BarColor.RED);
                bossBar.setProgress(1.0);
                bossBar.setTitle(langHandler.getMessage(player, "game.deathmatch-title"));
                continue;
            }
            bossBar.setProgress(Math.max(0.0, Math.min(1.0, (double) worldTimeLeft / Math.max(1, configHandler.getWorldConfig(world).getInt("game-time")))));
            int minutes = Math.max(0, worldTimeLeft - 1) / 60;
            int seconds = Math.max(0, worldTimeLeft - 1) % 60;
            String timeFormatted = String.format("%02d:%02d", minutes, seconds);
            bossBar.setTitle(langHandler.getMessage(player, "score.time", timeFormatted));
        }
    }

    private void endGameWithTeams(World world) {
        for (Player player : world.getPlayers()) {
            player.sendMessage(langHandler.getMessage(player, "game.game-end"));
        }

        List<List<Player>> worldTeamsAlive = teamsAlive.computeIfAbsent(world.getName(), k -> new ArrayList<>());
        List<List<Player>> worldTeamPlacements = teamPlacements.computeIfAbsent(world.getName(), k -> new ArrayList<>());

        if (worldTeamsAlive.size() == 1) {
            List<Player> winningTeam = worldTeamsAlive.get(0);
            worldTeamPlacements.add(winningTeam);
            winningTeam(winningTeam, "winner", world);
        } else {
            determineWinningTeam(world);
        }

        if (worldTeamsAlive.size() == 1) {
            celebrateWinners(world, new ArrayList<>(worldTeamsAlive.get(0)));
        } else {
            endGame(false, world);
        }
    }

    private void endGameWithPlayers(World world) {
        for (Player player : world.getPlayers()) {
            player.sendMessage(langHandler.getMessage(player, "game.game-end"));
        }

        List<Player> worldPlayersAlive = playersAlive.computeIfAbsent(world.getName(), k -> new ArrayList<>());
        List<Player> worldPlayerPlacements = playerPlacements.computeIfAbsent(world.getName(), k -> new ArrayList<>());

        Player winner = worldPlayersAlive.isEmpty() ? null : worldPlayersAlive.get(0);

        if (winner != null) {
            worldPlayerPlacements.add(winner);

            if (configHandler.getPluginSettings().getBoolean("database.enabled")) {
	            PlayerStatsHandler playerStats = statsMap.get(winner.getUniqueId());

	            playerStats.setSoloGamesWon(playerStats.getSoloGamesWon() + 1);

	            playerStats.setDirty();
            }
        }

        for (Player player : world.getPlayers()) {
            if (winner != null) {
                player.sendMessage(langHandler.getMessage(player, "game.winner", winner.getName()));
                player.sendTitle("", langHandler.getMessage(player, "game.winner", winner.getName()), 5, 180, 15);
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

            } else {
                player.sendTitle("", langHandler.getMessage(player, "game.team-no-winner"), 5, 20, 10);
                player.sendMessage(langHandler.getMessage(player, "game.team-no-winner"));
            }
        }
        if (winner != null) {
            runCustomPlayerCommands(true, world, List.of(winner));
            celebrateWinners(world, List.of(winner));
        } else {
            endGame(false, world);
        }
    }

    private void celebrateWinners(World world, List<Player> winners) {
        if (!celebratingWorlds.add(world.getName())) return;
        world.setPVP(false);
        Integer graceTask = gracePeriodTaskId.remove(world.getName());
        if (graceTask != null) plugin.getServer().getScheduler().cancelTask(graceTask);
        BukkitTask refill = chestRefillTask.remove(world.getName());
        if (refill != null) refill.cancel();
        BukkitTask supply = supplyDropTask.remove(world.getName());
        if (supply != null) supply.cancel();

        List<BukkitTask> tasks = new ArrayList<>();
        celebrationTasks.put(world.getName(), tasks);
        List<Firework> rockets = new ArrayList<>();
        celebrationRockets.put(world.getName(), rockets);
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player winner : winners) {
                if (!winner.isOnline() || !winner.getWorld().equals(world)) continue;
                Firework rocket = world.spawn(winner.getLocation().add(0, 1, 0), Firework.class);
                rockets.add(rocket);
                FireworkMeta meta = rocket.getFireworkMeta();
                meta.setPower(1);
                meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                        .withColor(Color.YELLOW, Color.ORANGE).withFade(Color.WHITE).trail(true).build());
                rocket.setFireworkMeta(meta);
            }
        }, 0L, 20L));
        tasks.add(plugin.getServer().getScheduler().runTaskLater(plugin, () -> endGame(false, world), 200L));
    }

    private void handleTimeUp(World world) {
        if (!gameStarted.getOrDefault(world.getName(), false) || !deathmatchWorlds.add(world.getName())) {
            return;
        }
        Map<UUID, Location> assignedSpawns = matchSpawns.getOrDefault(world.getName(), Collections.emptyMap());
        Integer graceTask = gracePeriodTaskId.remove(world.getName());
        if (graceTask != null) {
            plugin.getServer().getScheduler().cancelTask(graceTask);
        }
        BukkitTask refill = chestRefillTask.remove(world.getName());
        if (refill != null) refill.cancel();
        BukkitTask supply = supplyDropTask.remove(world.getName());
        if (supply != null) supply.cancel();

        for (Player player : new ArrayList<>(playersAlive.getOrDefault(world.getName(), Collections.emptyList()))) {
            Location spawn = assignedSpawns.get(player.getUniqueId());
            if (spawn != null && player.isOnline()) {
                player.setFallDistance(0);
                player.teleport(spawn.clone());
            }
        }
        worldBorderHandler.startDeathmatchBorder(world, matchPlatforms.containsKey(world.getName())
                ? matchPlatforms.get(world.getName()) : assignedSpawns.values());
        int protection = Math.max(0, configHandler.getWorldConfig(world).getInt("deathmatch.protection-seconds", 5));
        world.setPVP(protection == 0);
        if (protection > 0) {
            preparingDeathmatchWorlds.add(world.getName());
            final int[] remaining = {protection};
            BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                if (--remaining[0] <= 0) {
                    preparingDeathmatchWorlds.remove(world.getName());
                    BukkitTask finished = deathmatchPreparationTasks.remove(world.getName());
                    if (finished != null) finished.cancel();
                    if (gameStarted.getOrDefault(world.getName(), false) && !celebratingWorlds.contains(world.getName())) {
                        world.setPVP(true);
                        for (Player player : world.getPlayers()) player.sendTitle("§c¡A pelear!", "", 0, 30, 10);
                    }
                } else for (Player player : world.getPlayers()) player.sendTitle("§e" + remaining[0], "§ePrepará tu arma", 0, 20, 0);
            }, 20L, 20L);
            deathmatchPreparationTasks.put(world.getName(), task);
        }
        for (Player player : world.getPlayers()) {
            player.sendMessage(langHandler.getMessage(player, "game.deathmatch-start"));
            player.sendTitle(langHandler.getMessage(player, "game.deathmatch-title"),
                    langHandler.getMessage(player, "game.deathmatch-start"), 5, 80, 20);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
        }
        updateBossBars(world);
    }

    private void determineSoloWinner(World world) {
        Player winner = null;
        int maxKills = -1;
        Map<Player, Integer> worldPlayerKills = playerKills.computeIfAbsent(world.getName(), k -> new HashMap<>());
        for (Map.Entry<Player, Integer> entry : worldPlayerKills.entrySet()) {
            if (entry.getValue() > maxKills) {
                maxKills = entry.getValue();
                winner = entry.getKey();
            }
        }

        for (Player player : world.getPlayers()) {
            if (winner != null) {
                player.sendTitle("", langHandler.getMessage(player, "game.solo-kills", winner.getName()), 5, 20, 10);
                player.sendMessage(langHandler.getMessage(player, "game.solo-kills", winner.getName()));
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

                runCustomPlayerCommands(true, world, List.of(winner));
            } else {
                player.sendTitle("", langHandler.getMessage(player, "game.team-no-winner"), 5, 20, 10);
                player.sendMessage(langHandler.getMessage(player, "game.team-no-winner"));
            }

        }
        endGame(false, world);
    }

    private void winningTeam(List<Player> winningTeam, String winReason, World world) {
        if (winningTeam != null) {
            String allNames = getAllPlayerNames(winningTeam);
            String messageKey = getMessageKey(winReason);
            String titleKey = getTitleKey(winReason);

            for (Player player : world.getPlayers()) {
                player.sendTitle("", langHandler.getMessage(player, messageKey, allNames), 5, 20, 10);
                player.sendMessage(langHandler.getMessage(player, titleKey, allNames));
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }
            
            runCustomPlayerCommands(true, world, winningTeam);

            if (configHandler.getPluginSettings().getBoolean("database.enabled")) {
                for (Player player : winningTeam) {
	                PlayerStatsHandler playerStats = statsMap.get(player.getUniqueId());

	                playerStats.setTeamGamesWon(playerStats.getTeamGamesWon() + 1);

	                playerStats.setDirty();
                }
            }
        }
    }

    private String getAllPlayerNames(List<Player> players) {
        StringBuilder allNames = new StringBuilder();
        for (Player player : players) {
            allNames.append(player.getName()).append(", ");
        }
        if (!allNames.isEmpty()) {
            allNames.setLength(allNames.length() - 2); // Remove trailing comma and space
        }
        return allNames.toString();
    }

    private String getMessageKey(String winReason) {
        return switch (winReason) {
            case "winner" -> "game.winner";
            case "team-kills" -> "game.team-kills";
            case "team-alive" -> "game.team-alive";
            default -> "game.team-no-winner";
        };
    }

    private String getTitleKey(String winReason) {
        return switch (winReason) {
            case "winner" -> "game.winner";
            case "team-kills", "team-alive" -> "game.time-up";
            default -> "game.team-no-winner";
        };
    }

    private void determineWinningTeam(World world) {
        List<List<Player>> potentialWinningTeams = new ArrayList<>();
        int maxAlivePlayers = -1;
        int maxKills = -1;

        List<List<Player>> worldTeamsAlive = teamsAlive.computeIfAbsent(world.getName(), k -> new ArrayList<>());

        for (List<Player> team : worldTeamsAlive) {
            int alivePlayers = team.size();

            Map<Player, Integer> worldPlayerKills = playerKills.computeIfAbsent(world.getName(), k -> new HashMap<>());
            int teamKills = team.stream().mapToInt(player -> worldPlayerKills.getOrDefault(player, 0)).sum();

            if (alivePlayers > maxAlivePlayers || (alivePlayers == maxAlivePlayers && teamKills > maxKills)) {
                maxAlivePlayers = alivePlayers;
                maxKills = teamKills;
                potentialWinningTeams.clear();
                potentialWinningTeams.add(team);
            } else if (alivePlayers == maxAlivePlayers && teamKills == maxKills) {
                potentialWinningTeams.add(team);
            }
        }

        if (potentialWinningTeams.size() == 1) {
            List<Player> winningTeam = potentialWinningTeams.get(0);
            String winReason = maxAlivePlayers > 0 ? "team-alive" : "team-kills";
            winningTeam(winningTeam, winReason, world);
        } else {
            for (Player player : world.getPlayers()) {
                player.sendMessage(langHandler.getMessage(player, "game.team-no-winner"));
            }
        }
    }

    public void endGame(Boolean disable, World world) {
        if (plugin.getSupplyDropTracker() != null) plugin.getSupplyDropTracker().clear(world);
        if (!hgWorldNames.contains(world.getName())) return;
        preparingDeathmatchWorlds.remove(world.getName());
        BukkitTask preparing = deathmatchPreparationTasks.remove(world.getName());
        if (preparing != null) preparing.cancel();
        EnderLootHandler.clear(world.getName());
        celebratingWorlds.remove(world.getName());
        List<BukkitTask> celebration = celebrationTasks.remove(world.getName());
        if (celebration != null) {
            for (BukkitTask task : celebration) if (task != null) task.cancel();
        }
        List<Firework> rockets = celebrationRockets.remove(world.getName());
        if (rockets != null) {
            for (Firework rocket : rockets) if (rocket.isValid()) rocket.remove();
        }
        gameStarted.put(world.getName(), false);
        gameStarting.put(world.getName(), false);
        deathmatchWorlds.remove(world.getName());
        matchSpawns.remove(world.getName());
        matchPlatforms.remove(world.getName());

	    List<Player> worldPlayerPlacements = playerPlacements.computeIfAbsent(world.getName(), k -> new ArrayList<>());
        List<Player> worldPlayersAlive = playersAlive.computeIfAbsent(world.getName(), k -> new ArrayList<>());
        List<Player> worldStartingPlayers = startingPlayers.computeIfAbsent(world.getName(), k -> new ArrayList<>());
	    List<List<Player>> worldTeamPlacements = teamPlacements.computeIfAbsent(world.getName(), k -> new ArrayList<>());

        if (configHandler.getWorldConfig(world).getInt("players-per-team") == 1) {
		    if (startingPlayers != null && worldPlayerPlacements.size() == worldStartingPlayers.size()) {
			    for (Player player : worldPlayerPlacements) {
				    int playerIndex = worldPlayerPlacements.indexOf(player);
				    double percentile = worldPlayerPlacements.size() == 1 ? 100.0 : (1 - (playerIndex / (worldPlayerPlacements.size() - 1.0))) * 100.0;

                    if (configHandler.getPluginSettings().getBoolean("database.enabled")) {
	                    PlayerStatsHandler playerStats = statsMap.get(player.getUniqueId());

	                    double netPercentile = (playerStats.getSoloPercentile() * playerStats.getSoloGamesPlayed() + percentile) / (playerStats.getSoloGamesPlayed() + 1);
	                    playerStats.setSoloPercentile(netPercentile);

	                    playerStats.setDirty();
                    }
			    }
		    }
	    } else {
		    if (worldTeamPlacements.size() == teams.computeIfAbsent(world.getName(), k -> new ArrayList<>()).size()) {
			    for (List<Player> team : worldTeamPlacements) {
				    int teamIndex = worldTeamPlacements.indexOf(team);
				    double percentile = worldTeamPlacements.size() == 1 ? 100.0 : (1 - (teamIndex / (worldTeamPlacements.size() - 1.0))) * 100.0;

                    if (configHandler.getPluginSettings().getBoolean("database.enabled")) {
                        for (Player player : team) {
	                        PlayerStatsHandler playerStats = statsMap.get(player.getUniqueId());

	                        double netPercentile = (playerStats.getTeamPercentile() * playerStats.getTeamGamesPlayed() + percentile) / (playerStats.getTeamGamesPlayed() + 1);

	                        playerStats.setTeamPercentile(netPercentile);

	                        playerStats.setDirty();
                        }
                    }
			    }
		    }
	    }

	    worldPlayerPlacements.clear();

	    worldTeamPlacements.clear();

	    List<Player> players = new ArrayList<>(world.getPlayers());

        for (Player player : players) {
            resetPlayerHandler.resetPlayer(player);
            removeBossBar(player);
            String lobbyWorldName = configHandler.getPluginSettings().getString("lobby-world");
            assert lobbyWorldName != null;
            World lobbyWorld = Bukkit.getWorld(lobbyWorldName);
            assert lobbyWorld != null;
            player.teleport(lobbyWorld.getSpawnLocation());
            scoreBoardHandler.removeScoreboard(player);

            if (!disable && totalTimeSpent.containsKey(player)) {
                Long timeSpent = totalTimeSpent.getOrDefault(player, 0L);
                int timeAlive = configHandler.getWorldConfig(world).getInt("game-time") - timeLeft.getOrDefault(world.getName(), 0);
                totalTimeSpent.put(player, timeSpent + timeAlive);
            }
        }

        if (!disable) {
            if (configHandler.getPluginSettings().getBoolean("reset-world")) WorldResetHandler.busyWorlds.add(world.getName());
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (configHandler.getPluginSettings().getBoolean("reset-world")) {
                    WorldResetHandler.busyWorlds.remove(world.getName());
                    worldResetHandler.resetWorldState(world);
                } else {
                    worldBorderHandler.resetWorldBorder(world);

                    worldResetHandler.removeShulkers(world);

                    world.getEntitiesByClass(Item.class).forEach(Item::remove);
                    world.getEntitiesByClass(ExperienceOrb.class).forEach(ExperienceOrb::remove);
                    world.getEntitiesByClass(Arrow.class).forEach(Arrow::remove);
                    world.getEntitiesByClass(Trident.class).forEach(Trident::remove);

                    Bukkit.unloadWorld(world, true);
                }
            }, 20L);
        }

        world.setPVP(false);


        runCustomGlobalCommands(true, world);

        if (gracePeriodTaskId.containsKey(world.getName())) {
            int worldGracePeriodTaskId = gracePeriodTaskId.get(world.getName());
            plugin.getServer().getScheduler().cancelTask(worldGracePeriodTaskId);
        }

        if (timerTaskId.containsKey(world.getName())) {
            int worldTimerTaskId = timerTaskId.get(world.getName());
            plugin.getServer().getScheduler().cancelTask(worldTimerTaskId);
        }

        BukkitTask worldChestRefillTask = chestRefillTask.get(world.getName());
        BukkitTask worldSupplyDropTask = supplyDropTask.get(world.getName());

        if (worldChestRefillTask != null) {
            plugin.getServer().getScheduler().cancelTask(worldChestRefillTask.getTaskId());
        }

        if (worldSupplyDropTask != null) {
            plugin.getServer().getScheduler().cancelTask(worldSupplyDropTask.getTaskId());
        }

        compassListener.cancelGlowTask(world);
        teamsHandler.removeGlowFromAllPlayers(world);

        Map<Player, Integer> worldPlayerKills = playerKills.computeIfAbsent(world.getName(), k -> new HashMap<>());
        Map<Player, String> worldPlayerVotes = playerVotes.computeIfAbsent(world.getName(), k -> new HashMap<>());
        worldStartingPlayers = startingPlayers.computeIfAbsent(world.getName(), k -> new ArrayList<>());

        worldPlayersAlive.clear();
        worldPlayerKills.clear();
        worldStartingPlayers.clear();
        worldPlayerVotes.clear();

        signHandler.setSignContent();

        if (plugin.isEnabled()) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                for (Player player : players) {
                    player.sendMessage(langHandler.getMessage(player, "game.join-instruction"));
                    player.sendTitle("", langHandler.getMessage(player, "game.join-instruction"), 5, 20, 10);
                }
            }, 100L);
        }
    }

    private void runCustomPlayerCommands(Boolean end, World world, List<Player> players) {
        if (!configHandler.getWorldConfig(world).getBoolean("custom-commands.enabled")) {
            return;
        }

        List<String> commands;
        if (end) {
            commands = Objects.requireNonNull(configHandler.getWorldConfig(world).getStringList("custom-commands.end.foreach-winner"));
        } else {
            commands = Objects.requireNonNull(configHandler.getWorldConfig(world).getStringList("custom-commands.start.foreach-player"));
        }
        
        if (!commands.isEmpty()) {
            for (String command : commands) {
                for (Player player : players) {
                    plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), command.replace("{winner}", player.getName()));
                }
            }
        }
    }

    private void runCustomGlobalCommands(Boolean end, World world) {
        if (!configHandler.getWorldConfig(world).getBoolean("custom-commands.enabled")) {
            return;
        }

        List<String> commands;

        if (end) {
            commands = Objects.requireNonNull(configHandler.getWorldConfig(world).getStringList("custom-commands.end.global"));
        } else {
            commands = Objects.requireNonNull(configHandler.getWorldConfig(world).getStringList("custom-commands.start.global"));
        }

        if (!commands.isEmpty()) {
            for (String command : commands) {
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), command);
            }
        }
    }

    public static void removeBossBar(Player player) {
        Map<Player, BossBar> worldPlayerBossBar = playerBossBars.computeIfAbsent(player.getWorld().getName(), k -> new HashMap<>());

        BossBar bossBar = worldPlayerBossBar.get(player);
        if (bossBar != null) {
            bossBar.removePlayer(player);
            worldPlayerBossBar.remove(player);
            bossBar.setVisible(false);
        }
    }
}
