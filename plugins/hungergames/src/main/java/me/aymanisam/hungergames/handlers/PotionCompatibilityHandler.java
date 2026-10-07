package me.aymanisam.hungergames.handlers;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;

public final class PotionCompatibilityHandler {
    private PotionCompatibilityHandler() {}

    /** Accept Bukkit effect names from migrated configs as well as current registry keys. */
    public static PotionEffectType resolveEffectType(String name) {
        if (name == null) return null;
        String type = switch (name.toUpperCase(Locale.ROOT)) {
            case "SLOW" -> "slowness";
            case "FAST_DIGGING" -> "haste";
            case "SLOW_DIGGING" -> "mining_fatigue";
            case "INCREASE_DAMAGE" -> "strength";
            case "HEAL" -> "instant_health";
            case "HARM" -> "instant_damage";
            case "JUMP" -> "jump_boost";
            case "CONFUSION" -> "nausea";
            case "DAMAGE_RESISTANCE" -> "resistance";
            default -> name.toLowerCase(Locale.ROOT);
        };
        NamespacedKey key = NamespacedKey.fromString(type);
        return key == null ? null : Registry.EFFECT.get(key);
    }
}
