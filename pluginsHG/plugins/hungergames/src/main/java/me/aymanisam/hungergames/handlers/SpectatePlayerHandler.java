package me.aymanisam.hungergames.handlers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

import static me.aymanisam.hungergames.handlers.GameSequenceHandler.playersAlive;

public class SpectatePlayerHandler {
    public static final class Menu implements InventoryHolder {
        public final String world;
        public final java.util.Map<Integer, java.util.UUID> targets = new java.util.HashMap<>();
        private Inventory inventory;
        public Menu(String world) { this.world = world; }
        public Inventory getInventory() { return inventory; }
    }
    private final LangHandler langHandler;

    public SpectatePlayerHandler(LangHandler langHandler) {
        this.langHandler = langHandler;
    }

    public void openSpectatorGUI(Player spectator) {
        List<Player> worldPlayersAlive = playersAlive.computeIfAbsent(spectator.getWorld().getName(), k -> new ArrayList<>());

        int size = Math.max(9, Math.min(54, (int) Math.ceil(worldPlayersAlive.size() / 9.0) * 9));
        Menu menu = new Menu(spectator.getWorld().getName());
        Inventory gui = Bukkit.createInventory(menu, size, langHandler.getMessage(spectator, "spectate.gui-message"));
        menu.inventory = gui;
        for (int i = 0; i < Math.min(size, worldPlayersAlive.size()); i++) {
            Player player = worldPlayersAlive.get(i);
            ItemStack playerItem = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) playerItem.getItemMeta();
            assert meta != null;
            meta.setOwningPlayer(player);
            meta.setDisplayName(player.getName());
            playerItem.setItemMeta(meta);

            gui.setItem(i, playerItem);
            menu.targets.put(i, player.getUniqueId());
        }

        spectator.openInventory(gui);
    }

}
