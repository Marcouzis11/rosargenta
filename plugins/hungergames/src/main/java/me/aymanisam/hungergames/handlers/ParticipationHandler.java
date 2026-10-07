package me.aymanisam.hungergames.handlers;

import org.bukkit.World;
import org.bukkit.entity.Player;
import java.util.*;

public final class ParticipationHandler {
    public static boolean samePlayer(Player first, Player second) {
        return first.equals(second) || (first.getUniqueId() != null && first.getUniqueId().equals(second.getUniqueId()));
    }
    public static boolean countsAsPlayer(Player player) {
        return player.getGameMode() != org.bukkit.GameMode.SPECTATOR;
    }

    public static int count(Collection<Player> players) {
        return (int) players.stream().filter(ParticipationHandler::countsAsPlayer).distinct().count();
    }

    public static void removeSpectators(World world) {
        for (Player player : new ArrayList<>(GameSequenceHandler.playersAlive.getOrDefault(world.getName(), List.of()))) {
            if (!countsAsPlayer(player)) leave(player, world);
        }
    }

    public static void leave(Player player, World world) {
        String name = world.getName();
        boolean active = me.aymanisam.hungergames.HungerGames.isGameStartingOrStarted(name)
                && !GameSequenceHandler.celebratingWorlds.contains(name);
        boolean alive = GameSequenceHandler.playersAlive.getOrDefault(name, new ArrayList<>())
                .removeIf(registered -> samePlayer(registered, player));
        if (alive && active) {
            List<Player> placements = GameSequenceHandler.playerPlacements.computeIfAbsent(name, k -> new ArrayList<>());
            if (!placements.contains(player)) placements.add(player);
        }
        List<List<Player>> teams = TeamsHandler.teamsAlive.getOrDefault(name, new ArrayList<>());
        for (Iterator<List<Player>> iterator = teams.iterator(); iterator.hasNext();) {
            List<Player> team = iterator.next();
            if (team.removeIf(registered -> samePlayer(registered, player)) && team.isEmpty()) {
                iterator.remove();
                for (List<Player> original : TeamsHandler.teams.getOrDefault(name, List.of())) {
                    if (active && original.stream().anyMatch(registered -> samePlayer(registered, player))) GameSequenceHandler.teamPlacements.computeIfAbsent(name, k -> new ArrayList<>()).add(original);
                }
            }
        }
    }
}
