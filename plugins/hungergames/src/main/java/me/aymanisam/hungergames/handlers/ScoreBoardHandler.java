package me.aymanisam.hungergames.handlers;

import fr.mrmicky.fastboard.FastBoard;
import me.aymanisam.hungergames.HungerGames;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;

import static me.aymanisam.hungergames.handlers.CountDownHandler.playersPerTeam;
import static me.aymanisam.hungergames.handlers.GameSequenceHandler.*;
import static me.aymanisam.hungergames.handlers.TeamsHandler.teams;
import static me.aymanisam.hungergames.listeners.PlayerListener.playerKills;

public class ScoreBoardHandler {
    private final HungerGames plugin;
    private final LangHandler langHandler;
    private final ConfigHandler configHandler;

    public static final Map<UUID, FastBoard> boards = new HashMap<>();

    public ScoreBoardHandler(HungerGames plugin, LangHandler langHandler) {
        this.plugin = plugin;
        this.langHandler = langHandler;
        this.configHandler = plugin.getConfigHandler();
    }

    private ChatColor getColor(int interval, int countdown) {
        ChatColor color;
        if (countdown <= interval / 3) {
            color = ChatColor.RED;
        } else if (countdown <= 2 * interval / 3) {
            color = ChatColor.YELLOW;
        } else {
            color = ChatColor.GREEN;
        }

        return color;
    }

    private String formatScore(Player player, String messageKey, int countdown, int interval) {
        int minutes = countdown / 60;
        int seconds = countdown % 60;
        String timeFormatted = String.format("%02d:%02d", minutes, seconds);

        return langHandler.getMessage(player, messageKey, getColor(interval, countdown) + timeFormatted);
    }

    public void createBoard(Player player) {
        if (!configHandler.getWorldConfig(player.getWorld()).getBoolean("display-scoreboard")) {
            return;
        }
        removeScoreboard(player);
        FastBoard board = new FastBoard(player);
        if (configHandler.getWorldConfig(player.getWorld()).getInt("players-per-team", 1) == 1) {
            board.updateTitle(langHandler.getMessage(player, "score.name-solo"));
        } else {
            board.updateTitle(langHandler.getMessage(player, "score.name-team"));
        }

        boards.put(player.getUniqueId(), board);
        updateBoard(board, player.getWorld());
    }

    public void updateBoard(FastBoard board, World world) {
        if (board == null || !configHandler.getWorldConfig(world).getBoolean("display-scoreboard")) {
            return;
        }
        board.updateLines(linesFor(board.getPlayer(), world));
    }

    List<String> linesFor(Player player, World world) {
        FileConfiguration worldConfig = configHandler.getWorldConfig(world);
        int gameTimeConfig = worldConfig.getInt("game-time");
        int pvpTimeConfig = worldConfig.getInt("grace-period");
        int chestRefillInterval = worldConfig.getInt("chestrefill.interval");
        int supplyDropInterval = worldConfig.getInt("supplydrop.interval");
        int borderStartSize = worldConfig.getInt("border.size");

        int worldTimeLeft = timeLeft.getOrDefault(world.getName(), gameTimeConfig);
        int worldPlayersAliveSize = ParticipationHandler.count(playersAlive.getOrDefault(world.getName(), List.of()));
        int worldStartingPlayers = startingPlayers.getOrDefault(world.getName(), List.of()).size();
        int worldBorderSize = (int) world.getWorldBorder().getSize();
        int pvpTimeLeft = (worldTimeLeft - gameTimeConfig) + pvpTimeConfig;
        int chestRefillTimeLeft = chestRefillInterval > 0 ? worldTimeLeft % chestRefillInterval : 0;
        int supplyDropTimeLeft = supplyDropInterval > 0 ? worldTimeLeft % supplyDropInterval : 0;
        ChatColor borderColor;

        if (borderStartSize == worldBorderSize) {
            borderColor = ChatColor.GREEN;
        } else if (deathmatchWorlds.contains(world.getName())) {
            borderColor = ChatColor.RED;
        } else {
            borderColor = ChatColor.YELLOW;
        }

        List<String> lines = new ArrayList<>();

        lines.add("");
        lines.add(langHandler.getMessage(player, "score.alive", getColor(worldStartingPlayers, worldPlayersAliveSize).toString() + worldPlayersAliveSize));
        Map<Player, Integer> worldPlayerKills = playerKills.computeIfAbsent(world.getName(), k -> new HashMap<>());
        lines.add(langHandler.getMessage(player, "score.kills", ChatColor.RED + worldPlayerKills.computeIfAbsent(player, k -> 0).toString()));
        lines.add(langHandler.getMessage(player, "score.border", borderColor.toString() + worldBorderSize));
        lines.add("");
        boolean deathmatch = deathmatchWorlds.contains(world.getName());
        lines.add(deathmatch ? langHandler.getMessage(player, "game.deathmatch-title")
                : formatScore(player, "score.time", worldTimeLeft, gameTimeConfig));

        if (!deathmatch && pvpTimeLeft >= 0) {
            lines.add(formatScore(player, "score.pvp", pvpTimeLeft, pvpTimeConfig));
        }

        lines.add("");
        if (!deathmatch) {
            lines.add(formatScore(player, "score.chestrefill", chestRefillTimeLeft, chestRefillInterval));
            String supplyLine = formatScore(player, "score.supplydrop", supplyDropTimeLeft, supplyDropInterval);
            SupplyDropTracker tracker = plugin.getSupplyDropTracker();
            if (tracker != null) supplyLine += tracker.directionFor(player);
            lines.add(supplyLine);
        }

        String teamScoreBoard = getScoreBoardTeam(player, world);

        if (teamScoreBoard != null) {
            lines.add("");
            lines.add(teamScoreBoard);
        }

        return lines;
    }

    private String getScoreBoardTeam(Player player, World world) {
        List<List<Player>> worldTeams = teams.computeIfAbsent(world.getName(), k -> new ArrayList<>());
        List<Player> worldPlayersAlive = playersAlive.computeIfAbsent(world.getName(), k -> new ArrayList<>());

        if (configHandler.getWorldConfig(world).getInt("players-per-team", 1) != 1) {
            for (List<Player> team : worldTeams) {
                if (team.contains(player)) {
                    for (Player teamMember : team) {
                        if (!teamMember.equals(player)) {
                            String teammateName = teamMember.getName();
                            ChatColor color = worldPlayersAlive.contains(teamMember) ? ChatColor.GREEN : ChatColor.RED;
                            return langHandler.getMessage(player, "score.teammate", color + teammateName);
                        }
                    }
                    break;
                }
            }
        }
        return null;
    }

    public void removeScoreboard(Player player) {
        FastBoard board = boards.remove(player.getUniqueId());

        if (board != null) {
            board.delete();
        }
    }
}
