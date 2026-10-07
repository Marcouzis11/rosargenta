package me.aymanisam.hungergames.listeners;

import me.aymanisam.hungergames.HungerGames;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Chunk;
import org.bukkit.entity.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArenaMobListenerTest {
    @Test
    void cleanupRemovesHostileAndPassiveMobsAndPreservesPluginEntities() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("arena");
        Zombie zombie = mock(Zombie.class);
        Cow cow = mock(Cow.class);
        Player player = mock(Player.class);
        ArmorStand dropMarker = mock(ArmorStand.class);
        Item loot = mock(Item.class);
        Firework celebration = mock(Firework.class);
        when(world.getEntities()).thenReturn(List.of(zombie, cow, player, dropMarker, loot, celebration));
        new ArenaMobListener(mock(HungerGames.class)).protectArena(world);
        verify(world).setGameRule(GameRule.DO_MOB_SPAWNING, false);
        verify(zombie).remove();
        verify(cow).remove();
        for (Entity entity : List.of(player, dropMarker, loot, celebration)) verify(entity, never()).remove();
    }

    @Test
    void allSpawnReasonsAreBlockedOnlyInArena() {
        World arena = mock(World.class), other = mock(World.class);
        when(arena.getName()).thenReturn("arena");
        when(other.getName()).thenReturn("other");
        when(arena.getEntities()).thenReturn(List.of());
        ArenaMobListener listener = new ArenaMobListener(mock(HungerGames.class));
        listener.protectArena(arena);
        for (CreatureSpawnEvent.SpawnReason reason : CreatureSpawnEvent.SpawnReason.values()) {
            Zombie zombie = mock(Zombie.class);
            when(zombie.getLocation()).thenReturn(new Location(arena, 0, 64, 0));
            CreatureSpawnEvent event = new CreatureSpawnEvent(zombie, reason);
            listener.onSpawn(event);
            assertTrue(event.isCancelled(), reason.name());
        }
        Cow cow = mock(Cow.class);
        when(cow.getLocation()).thenReturn(new Location(other, 0, 64, 0));
        CreatureSpawnEvent outside = new CreatureSpawnEvent(cow, CreatureSpawnEvent.SpawnReason.NATURAL);
        listener.onSpawn(outside);
        assertFalse(outside.isCancelled());
    }

    @Test
    void mobsSavedInUnloadedChunksAreRemovedWhenTheirEntitiesLoad() {
        World arena = mock(World.class);
        when(arena.getName()).thenReturn("arena");
        when(arena.getEntities()).thenReturn(List.of());
        ArenaMobListener listener = new ArenaMobListener(mock(HungerGames.class));
        listener.protectArena(arena);
        Chunk chunk = mock(Chunk.class);
        when(chunk.getWorld()).thenReturn(arena);
        Zombie zombie = mock(Zombie.class);
        ArmorStand marker = mock(ArmorStand.class);
        listener.onEntitiesLoad(new EntitiesLoadEvent(chunk, List.of(zombie, marker)));
        verify(zombie).remove();
        verify(marker, never()).remove();
    }
}
