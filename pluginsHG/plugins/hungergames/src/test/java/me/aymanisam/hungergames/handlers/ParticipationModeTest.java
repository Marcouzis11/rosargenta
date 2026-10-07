package me.aymanisam.hungergames.handlers;

import me.aymanisam.hungergames.HungerGames;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ParticipationModeTest {
    @Test void survivalCreativeAndAdventureCountButSpectatorsDoNot() {
        List<Player> players = new ArrayList<>();
        for (GameMode mode : GameMode.values()) {
            Player player = mock(Player.class);
            when(player.getGameMode()).thenReturn(mode);
            players.add(player);
        }
        assertEquals(3, ParticipationHandler.count(players));
        players.add(players.get(0));
        assertEquals(3, ParticipationHandler.count(players));
    }

    @Test void modeChangeRemovesParticipantAndEmptyTeamWithoutResurrectingThem() {
        String name = "mode-test";
        World world = mock(World.class);
        when(world.getName()).thenReturn(name);
        Player changing = mock(Player.class), survivor = mock(Player.class);
        when(changing.getGameMode()).thenReturn(GameMode.CREATIVE);
        when(survivor.getGameMode()).thenReturn(GameMode.SURVIVAL);
        HungerGames.gameStarted.put(name, true);
        GameSequenceHandler.playersAlive.put(name, new ArrayList<>(List.of(changing, survivor)));
        List<Player> original = List.of(changing);
        TeamsHandler.teams.put(name, List.of(original, List.of(survivor)));
        TeamsHandler.teamsAlive.put(name, new ArrayList<>(List.of(new ArrayList<>(original), new ArrayList<>(List.of(survivor)))));
        try {
            assertEquals(2, ParticipationHandler.count(GameSequenceHandler.playersAlive.get(name)));
            when(changing.getGameMode()).thenReturn(GameMode.SPECTATOR);
            ParticipationHandler.removeSpectators(world);
            ParticipationHandler.removeSpectators(world);
            assertEquals(List.of(survivor), GameSequenceHandler.playersAlive.get(name));
            assertEquals(1, TeamsHandler.teamsAlive.get(name).size());
            assertEquals(List.of(changing), GameSequenceHandler.playerPlacements.get(name));
            assertEquals(List.of(original), GameSequenceHandler.teamPlacements.get(name));
            when(changing.getGameMode()).thenReturn(GameMode.SURVIVAL);
            ParticipationHandler.removeSpectators(world);
            assertEquals(List.of(survivor), GameSequenceHandler.playersAlive.get(name));
        } finally {
            HungerGames.gameStarted.remove(name);
            GameSequenceHandler.playersAlive.remove(name);
            GameSequenceHandler.playerPlacements.remove(name);
            GameSequenceHandler.teamPlacements.remove(name);
            TeamsHandler.teams.remove(name);
            TeamsHandler.teamsAlive.remove(name);
        }
    }
}
