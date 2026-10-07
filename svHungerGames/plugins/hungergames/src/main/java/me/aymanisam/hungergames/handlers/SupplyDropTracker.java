package me.aymanisam.hungergames.handlers;

import me.aymanisam.hungergames.HungerGames;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.ShulkerBox;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import java.util.*;

public final class SupplyDropTracker {
    private final HungerGames plugin;
    private final Map<UUID, Location> targets = new HashMap<>();
    private final Map<UUID, BossBar> indicators = new HashMap<>();

    public SupplyDropTracker(HungerGames plugin) {
        this.plugin = plugin;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::update, 10L, 10L);
    }

    public void track(Location location) {
        targets.put(Objects.requireNonNull(location.getWorld()).getUID(), location.clone());
        update();
    }

    public void clear(World world) {
        targets.remove(world.getUID());
        for (Player player : world.getPlayers()) removeIndicator(player.getUniqueId());
    }

    public String directionFor(Player player) {
        Location target = targets.get(player.getWorld().getUID());
        if (target == null || !ParticipationHandler.countsAsPlayer(player) || player.isDead()) return "";
        Location from = player.getLocation();
        long distance = Math.round(Math.hypot(target.getX() - from.getX(), target.getZ() - from.getZ()));
        return " §6§l" + arrow(from, target) + " §f" + distance + " m";
    }

    static String arrow(Location player, Location target) {
        double dx = target.getX() - player.getX(), dz = target.getZ() - player.getZ();
        if (Math.hypot(dx, dz) < 2) return "●";
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double relative = ((targetYaw - player.getYaw()) % 360 + 360) % 360;
        int sector = (int) Math.floor((relative + 22.5) / 45) % 8;
        return new String[]{"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"}[sector];
    }

    private void update() {
        for (Iterator<Location> iterator = targets.values().iterator(); iterator.hasNext();) {
            Location target = iterator.next();
            if (target.getBlock().getType() != Material.RED_SHULKER_BOX
                    || !(target.getBlock().getState() instanceof ShulkerBox box)
                    || Arrays.stream(box.getInventory().getContents()).noneMatch(item -> item != null && !item.getType().isAir())) {
                iterator.remove();
            }
        }
        Set<UUID> online = new HashSet<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            online.add(player.getUniqueId());
            World world = player.getWorld();
            Location target = targets.get(world.getUID());
            boolean eligible = target != null && HungerGames.gameStarted.getOrDefault(world.getName(), false)
                    && !GameSequenceHandler.celebratingWorlds.contains(world.getName())
                    && !GameSequenceHandler.deathmatchWorlds.contains(world.getName())
                    && ParticipationHandler.countsAsPlayer(player) && !player.isDead()
                    && GameSequenceHandler.playersAlive.getOrDefault(world.getName(), List.of()).contains(player);
            if (!eligible) {
                removeIndicator(player.getUniqueId());
                continue;
            }
            Location from = player.getLocation();
            long distance = Math.round(Math.hypot(target.getX() - from.getX(), target.getZ() - from.getZ()));
            int height = target.getBlockY() - from.getBlockY();
            String vertical = height > 3 ? " §7(más arriba)" : height < -3 ? " §7(más abajo)" : "";
            String direction = arrow(from, target);
            String title = "§6§l" + direction + " " + direction + " " + direction
                    + "   §e§lSUPPLY DROP   §f" + distance + " m" + vertical;
            BossBar indicator = indicators.computeIfAbsent(player.getUniqueId(), id -> {
                BossBar bar = plugin.getServer().createBossBar(title, BarColor.YELLOW, BarStyle.SOLID);
                bar.setProgress(1.0);
                bar.addPlayer(player);
                bar.setVisible(true);
                return bar;
            });
            indicator.setTitle(title);
        }
        for (UUID id : new ArrayList<>(indicators.keySet())) {
            if (!online.contains(id)) removeIndicator(id);
        }
    }

    private void removeIndicator(UUID id) {
        BossBar bar = indicators.remove(id);
        if (bar != null) {
            bar.removeAll();
            bar.setVisible(false);
        }
    }
}
