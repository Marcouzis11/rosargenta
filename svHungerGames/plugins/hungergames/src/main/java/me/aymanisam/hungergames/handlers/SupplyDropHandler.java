package me.aymanisam.hungergames.handlers;

import me.aymanisam.hungergames.HungerGames;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class SupplyDropHandler {
    private final HungerGames plugin;
    private final ConfigHandler configHandler;
    private final ArenaHandler arenaHandler;
    private final ChestRefillHandler chestRefillHandler;
    private final LangHandler langHandler;
    private final Map<UUID, DropColumn> lastDropColumns = new HashMap<>();

    private record DropColumn(int x, int z) {}

    public SupplyDropHandler(HungerGames plugin, LangHandler langHandler) {
        this.plugin = plugin;
        this.configHandler = plugin.getConfigHandler();
        this.arenaHandler = new ArenaHandler(plugin, langHandler);
        this.chestRefillHandler = new ChestRefillHandler(plugin, langHandler);
        this.langHandler = langHandler;
    }

    public void setSupplyDrop(World world) {
        FileConfiguration config = configHandler.getWorldConfig(world);
        FileConfiguration arenaConfig = arenaHandler.getArenaConfig(world);

        if (arenaConfig == null || !world.getName().equals(arenaConfig.getString("region.world"))
                || !arenaConfig.isSet("region.pos1.x") || !arenaConfig.isSet("region.pos1.z")
                || !arenaConfig.isSet("region.pos2.x") || !arenaConfig.isSet("region.pos2.z")) {
            plugin.getLogger().warning("Supply drop skipped: arena region is missing or invalid for world " + world.getName());
            return;
        }

        WorldBorder border = world.getWorldBorder();

        int numSupplyDrops = config.getInt("num-supply-drops");

        int minX = (int) Math.ceil(Math.max(Math.min(arenaConfig.getDouble("region.pos1.x"), arenaConfig.getDouble("region.pos2.x")), border.getCenter().getX() - border.getSize() / 2));
        int minZ = (int) Math.ceil(Math.max(Math.min(arenaConfig.getDouble("region.pos1.z"), arenaConfig.getDouble("region.pos2.z")), border.getCenter().getZ() - border.getSize() / 2));
        int maxX = (int) Math.floor(Math.min(Math.max(arenaConfig.getDouble("region.pos1.x"), arenaConfig.getDouble("region.pos2.x")), border.getCenter().getX() + border.getSize() / 2));
        int maxZ = (int) Math.floor(Math.min(Math.max(arenaConfig.getDouble("region.pos1.z"), arenaConfig.getDouble("region.pos2.z")), border.getCenter().getZ() + border.getSize() / 2));

        if (minX > maxX || minZ > maxZ || (minX == maxX && minZ == maxZ)) {
            plugin.getLogger().warning("Supply drop skipped: arena and world border leave fewer than two possible columns in world " + world.getName());
            return;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < numSupplyDrops; i++) {
            int x = 0;
            int z = 0;
            int highestY = -1;
            DropColumn previous = lastDropColumns.get(world.getUID());
            for (int attempt = 0; attempt < 32; attempt++) {
                x = random.nextInt(minX, maxX + 1);
                z = random.nextInt(minZ, maxZ + 1);
                if (previous != null && previous.x() == x && previous.z() == z) {
                    if (minX < maxX) {
                        x = x < maxX ? x + 1 : minX;
                    } else {
                        z = z < maxZ ? z + 1 : minZ;
                    }
                }
                highestY = world.getHighestBlockYAt(x, z);
                if (highestY >= -60) {
                    break;
                }
            }

            if (highestY < -60) {
                if (previous != null && previous.x() >= minX && previous.x() <= maxX
                        && previous.z() >= minZ && previous.z() <= maxZ
                        && world.getHighestBlockYAt(previous.x(), previous.z()) >= -60) {
                    x = previous.x();
                    z = previous.z();
                    highestY = world.getHighestBlockYAt(x, z);
                    plugin.getLogger().warning("Supply drop reused its previous column because no other suitable ground was found in world " + world.getName());
                } else {
                    plugin.getLogger().warning("Supply drop skipped: no suitable ground found inside the arena and border in world " + world.getName());
                    continue;
                }
            }

            lastDropColumns.put(world.getUID(), new DropColumn(x, z));

            Block portalBlock = world.getBlockAt(x, highestY + 1, z);
            portalBlock.setType(Material.END_GATEWAY);

            Location portalBlockLocation = portalBlock.getLocation().add(0.5, 0.5, 0.5);
            ArmorStand armorStand = (ArmorStand) world.spawnEntity(portalBlockLocation, EntityType.ARMOR_STAND);
            armorStand.setVisible(false);
            armorStand.setGravity(false);
            armorStand.setCanPickupItems(false);

            PersistentDataContainer armorStandData = armorStand.getPersistentDataContainer();
            armorStandData.set(new NamespacedKey(plugin, "supplydrop"), PersistentDataType.STRING, "true");

            Block topmostBlock = world.getBlockAt(x, highestY + 2, z);
            topmostBlock.setType(Material.RED_SHULKER_BOX);

            if (topmostBlock.getState() instanceof ShulkerBox shulkerBox) {

                PersistentDataContainer shulkerBoxData = shulkerBox.getPersistentDataContainer();
                shulkerBoxData.set(new NamespacedKey(plugin, "supplydrop"), PersistentDataType.STRING, "true");

                shulkerBox.update();
            }

            world.playSound(portalBlockLocation, Sound.BLOCK_END_PORTAL_SPAWN, 1.0f, 1.0f);

            int minSupplyDropContent = config.getInt("min-supply-drop-content");
            int maxSupplyDropContent = config.getInt("max-supply-drop-content");

            YamlConfiguration itemsConfig = configHandler.loadItemsConfig(world);

            List<Location> blockList = new ArrayList<>();
            blockList.add(topmostBlock.getLocation());

            chestRefillHandler.refillInventory(blockList, "supply-drop-items", itemsConfig, minSupplyDropContent, maxSupplyDropContent);

            String message = " X: " + topmostBlock.getX() + " Y: " + topmostBlock.getY() + " Z: " + topmostBlock.getZ();

            for (Player player : world.getPlayers()) {
                player.sendMessage(langHandler.getMessage(player, "supplydrop.spawned", message));
            }
        }
    }
}
