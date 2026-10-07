package me.aymanisam.hungergames.stats;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.aymanisam.hungergames.HungerGames;
import me.aymanisam.hungergames.handlers.LangHandler;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.entity.Player;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.logging.Level;

public class DisplayStatsHandler {
	private final HungerGames plugin;
	private final LangHandler langHandler;

	public DisplayStatsHandler(HungerGames plugin, LangHandler langHandler) {
		this.plugin = plugin;
		this.langHandler = langHandler;
	}

	public void displayPlayerHead(Player player) {
		player.sendMessage(langHandler.getMessage(player, "stats.player", player.getName()));
        String name = player.getName();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String uuid = getPlayerUUID(name);
            String skinUrl = uuid == null ? null : getPlayerSkinUrl(uuid);
            BufferedImage head = skinUrl == null ? null : getPlayerHead(skinUrl);
            if (!plugin.isEnabled()) return;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && head != null) player.spigot().sendMessage(getHeadAsTextComponent(head));
            });
        });
	}

	private String getPlayerUUID(String username) {
		try {
            JsonObject jsonObj = readJson("https://api.mojang.com/users/profiles/minecraft/" + username);

			return jsonObj.get("id").getAsString();
		} catch (Exception e) {
			plugin.getLogger().log(Level.WARNING, "Failed to fetch player UUID: " + e.getMessage(), e);
			return null;
		}
	}

	private String getPlayerSkinUrl(String uuid) {
		try {
            JsonObject jsonObj = readJson("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
			JsonObject properties = jsonObj.getAsJsonArray("properties").get(0).getAsJsonObject();
			String base64Value = properties.get("value").getAsString();

			String decoded = new String(Base64.getDecoder().decode(base64Value), StandardCharsets.UTF_8);
			JsonObject textureJson = JsonParser.parseString(decoded).getAsJsonObject();

			return textureJson.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
		} catch (Exception e) {
			plugin.getLogger().log(Level.WARNING, "Failed to fetch player skin URL: " + e.getMessage(), e);
			return null;
		}
	}

	private BufferedImage getPlayerHead(String imageUrl) {
        HttpURLConnection connection = null;
        try {
            connection = openConnection(imageUrl);
            try (InputStream in = connection.getInputStream()) {
                BufferedImage skin = ImageIO.read(in);
                return skin == null ? null : skin.getSubimage(8, 8, 8, 8);
            }
		} catch (Exception e) {
			plugin.getLogger().log(Level.WARNING, "Failed to download player skin: " + e.getMessage(), e);
			return null;
		} finally { if (connection != null) connection.disconnect(); }
	}

    private static HttpURLConnection openConnection(String address) throws java.io.IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestMethod("GET");
        return connection;
    }

    private static JsonObject readJson(String address) throws java.io.IOException {
        HttpURLConnection connection = openConnection(address);
        connection.setRequestProperty("Accept", "application/json");
        try (InputStreamReader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } finally { connection.disconnect(); }
    }

	private BaseComponent[] getHeadAsTextComponent(BufferedImage headImage) {
		if (headImage == null) return new ComponentBuilder("⚠ Could not load head").create();

		ComponentBuilder builder = new ComponentBuilder("").append("\n");

		for (int y = 0; y < headImage.getHeight(); y++) {
			for (int x = 0; x < headImage.getWidth(); x++) {
				int color = headImage.getRGB(x, y) & 0xFFFFFF;
				ChatColor textColor = ChatColor.of(String.format("#%06X", color));
				builder.append("█").color(textColor);
			}
			builder.append("\n");
		}
		return builder.create();
	}
}
