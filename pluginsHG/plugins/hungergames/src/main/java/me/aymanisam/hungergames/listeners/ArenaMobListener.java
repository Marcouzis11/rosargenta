package me.aymanisam.hungergames.listeners;

import me.aymanisam.hungergames.HungerGames;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public final class ArenaMobListener implements Listener {
    private final HungerGames plugin;
    private final Set<String> arenas = new HashSet<>();

    public ArenaMobListener(HungerGames plugin) {
        this.plugin = plugin;
    }

    public void protectConfiguredWorld(World world) {
        if (world.getName().equals(plugin.getConfigHandler().getPluginSettings().getString("lobby-world"))) return;
        File file = new File(new File(plugin.getDataFolder(), world.getName()), "arena.yml");
        if (!file.isFile()) return;
        if (world.getName().equals(YamlConfiguration.loadConfiguration(file).getString("region.world"))) {
            protectArena(world);
        }
    }

    public void protectArena(World world) {
        arenas.add(world.getName());
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        removeMobs(world.getEntities());
    }

    private void removeMobs(Iterable<? extends Entity> entities) {
        for (Entity entity : entities) {
            if (entity instanceof Mob) entity.remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (arenas.contains(event.getLocation().getWorld().getName()) && event.getEntity() instanceof Mob) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        protectConfiguredWorld(event.getWorld());
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (arenas.contains(event.getWorld().getName())) removeMobs(event.getEntities());
    }
}
