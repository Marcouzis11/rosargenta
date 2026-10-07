package me.aymanisam.hungergames.handlers;

import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.Objects;

public class ResetPlayerHandler {
    public void resetPlayer(Player player) {
        player.setGameMode(GameMode.ADVENTURE);
        Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(20);
        player.setFoodLevel(20);
        player.setSaturation(5);
        player.getInventory().clear();
        player.setExp(0);
        player.setLevel(0);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        player.setHealth(20);
    }
}
