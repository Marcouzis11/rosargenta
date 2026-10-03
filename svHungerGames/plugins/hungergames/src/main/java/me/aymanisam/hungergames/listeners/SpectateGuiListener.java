package me.aymanisam.hungergames.listeners;

import me.aymanisam.hungergames.handlers.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import java.util.*;

public class SpectateGuiListener implements Listener {
    private final LangHandler lang;
    public SpectateGuiListener(LangHandler lang) { this.lang = lang; }
    @EventHandler
    public void onGuiOpen(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SpectatePlayerHandler.Menu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || player.getGameMode() != GameMode.SPECTATOR
                || !player.getWorld().getName().equals(menu.world)) return;
        UUID id = menu.targets.get(event.getRawSlot());
        if (id == null) return;
        Player target = Bukkit.getPlayer(id);
        if (target == null || !target.isOnline() || !target.getWorld().equals(player.getWorld())
                || !GameSequenceHandler.playersAlive.getOrDefault(menu.world, List.of()).contains(target)) {
            player.sendMessage("§eEse jugador ya no está en la partida.");
            new SpectatePlayerHandler(lang).openSpectatorGUI(player);
            return;
        }
        player.closeInventory();
        player.teleport(target.getLocation());
        player.setSpectatorTarget(target);
        player.sendMessage(lang.getMessage(player, "spectate.teleported", target.getName()));
    }
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof SpectatePlayerHandler.Menu) event.setCancelled(true);
    }
}
