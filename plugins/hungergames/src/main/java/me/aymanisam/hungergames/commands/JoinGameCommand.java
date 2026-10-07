package me.aymanisam.hungergames.commands;

import me.aymanisam.hungergames.HungerGames;
import me.aymanisam.hungergames.handlers.*;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static me.aymanisam.hungergames.HungerGames.*;
import static me.aymanisam.hungergames.handlers.GameSequenceHandler.playersAlive;
import static me.aymanisam.hungergames.handlers.SetSpawnHandler.spawnPointMap;

public class JoinGameCommand implements CommandExecutor {
    private final HungerGames plugin;
    private final LangHandler langHandler;
    private final SetSpawnHandler setSpawnHandler;
    private final ArenaHandler arenaHandler;
    private final ConfigHandler configHandler;
    private final ScoreBoardHandler scoreBoardHandler;

    public JoinGameCommand(HungerGames plugin, LangHandler langHandler, SetSpawnHandler setSpawnHandler, ScoreBoardHandler scoreBoardHandler) {
        this.plugin = plugin;
        this.langHandler = langHandler;
        this.setSpawnHandler = setSpawnHandler;
	    this.configHandler = plugin.getConfigHandler();
	    this.scoreBoardHandler = scoreBoardHandler;
	    this.arenaHandler = new ArenaHandler(plugin, langHandler);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(langHandler.getMessage(null, "no-server"));
            return true;
        }

        if (!(player.hasPermission("hungergames.join"))) {
            sender.sendMessage(langHandler.getMessage(player, "no-permission"));
            return true;
        }

        if (!(args.length == 1)) {
            sender.sendMessage(langHandler.getMessage(player, "teleport.no-arena"));
            return true;
        }

        String worldName = args[0];
        if (WorldResetHandler.busyWorlds.contains(worldName)) {
            player.sendMessage("§eLa arena se está preparando. Intentá nuevamente en unos segundos.");
            return true;
        }

        if (!hgWorldNames.contains(worldName)) {
            sender.sendMessage(langHandler.getMessage(player, "teleport.invalid-world", worldName));
            plugin.getLogger().info("Loaded maps:" + plugin.getServer().getWorlds().stream().map(World::getName).collect(Collectors.joining(", ")));
            return true;
        }

        World world = Bukkit.getWorld(worldName);

        if (isPlayerInGame(player)) {
            player.sendMessage(langHandler.getMessage(player, "game.already-joined"));
            return true;
        }

        if (configHandler.getPluginSettings().getBoolean("custom-teams")) {
            if (!teamsFinalized) {
                player.sendMessage(langHandler.getMessage(player, "team.no-finalize"));
                return true;
            }
            if (!isPlayerInAnyCustomTeam(player)) {
                player.sendMessage(langHandler.getMessage(player, "team.no-team"));
                return true;
            }
        }

        if (gameStarted.getOrDefault(worldName, false)) {
            player.sendMessage(langHandler.getMessage(player, "startgame.started"));
            return true;
        }

        if (world == null) {
            World createdWorld = Bukkit.createWorld(WorldCreator.name(worldName));
            if (createdWorld == null) {
                player.sendMessage("§cNo se pudo cargar la arena. Revisá la consola del servidor.");
                return true;
            }
            arenaHandler.loadWorldFiles(createdWorld);
            List<Player> worldPlayersWaiting = setSpawnHandler.playersWaiting.computeIfAbsent(worldName, k -> new ArrayList<>());
            if (worldPlayersWaiting.contains(player)) {
                return true;
            }
            setSpawnHandler.teleportPlayerToSpawnpoint(player, createdWorld);
            setSpawnHandler.createSetSpawnConfig(createdWorld);
        } else {
            setSpawnHandler.teleportPlayerToSpawnpoint(player, world);
            setSpawnHandler.createSetSpawnConfig(world);
        }

        if (gameStarting.getOrDefault(worldName, false)
                && spawnPointMap.getOrDefault(worldName, Map.of()).containsValue(player)) {
            playersAlive.computeIfAbsent(worldName, k -> new ArrayList<>()).add(player);
        }

        return true;
    }

    public static void teleportPlayerForSpectating(Player player, String worldName, World world, ConfigHandler configHandler, ScoreBoardHandler scoreBoardHandler, LangHandler langHandler) {
        if (configHandler.getPluginSettings().getBoolean("spectating")) {
            assert world != null;
            player.teleport(world.getSpawnLocation());
            if (gameStarted.getOrDefault(worldName, false)) {
                scoreBoardHandler.createBoard(player);
            }
            player.setGameMode(GameMode.SPECTATOR);
            player.sendMessage(langHandler.getMessage(player, "spectate.spectating-player"));
        }
    }

    public static boolean isPlayerInGame(Player player) {
        String worldName = player.getWorld().getName();
        boolean active = isGameStartingOrStarted(worldName)
                && playersAlive.getOrDefault(worldName, List.of()).stream()
                .anyMatch(registered -> ParticipationHandler.samePlayer(registered, player));
        return active || spawnPointMap.getOrDefault(worldName, Map.of()).values().stream()
                .anyMatch(registered -> ParticipationHandler.samePlayer(registered, player));
    }

    public static boolean isPlayerInAnyCustomTeam(Player player) {
        for (List<Player> team : customTeams.values()) {
            if (team.contains(player)) {
                return true;
            }
        }
        return false;
    }
}
