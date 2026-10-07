package me.aymanisam.hungergames.listeners;

import me.aymanisam.hungergames.HungerGames;
import me.aymanisam.hungergames.handlers.*;
import me.aymanisam.hungergames.stats.DatabaseHandler;
import me.aymanisam.hungergames.stats.PlayerStatsHandler;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;

import static me.aymanisam.hungergames.HungerGames.*;
import static me.aymanisam.hungergames.handlers.GameSequenceHandler.*;
import static me.aymanisam.hungergames.handlers.TeamsHandler.teams;
import static me.aymanisam.hungergames.handlers.TeamsHandler.teamsAlive;
import static org.bukkit.event.entity.EntityDamageEvent.DamageCause.PROJECTILE;
import static org.bukkit.event.entity.EntityDamageEvent.DamageCause.WORLD_BORDER;

public class PlayerListener implements Listener {
    private final HungerGames plugin;
    private final SetSpawnHandler setSpawnHandler;
    private final LangHandler langHandler;
    private final ConfigHandler configHandler;
    private final SignHandler signHandler;
	private final DatabaseHandler databaseHandler;
    private final ResetPlayerHandler resetPlayerHandler;

	private final Map<String, Map<Player, Location>> deathLocations = new HashMap<>();
    private final Map<UUID, String> respawnArenas = new HashMap<>();
    private final Map<Player, Set<Player>> playerDamagers = new HashMap<>();
    public static final Map<String, Map<Player, Integer>> playerKills = new HashMap<>();

    public PlayerListener(HungerGames plugin, LangHandler langHandler, SetSpawnHandler setSpawnHandler) {
        this.setSpawnHandler = setSpawnHandler;
        this.plugin = plugin;
        this.langHandler = langHandler;
        this.configHandler = plugin.getConfigHandler();
        this.signHandler = new SignHandler(plugin, setSpawnHandler);
	    this.databaseHandler = plugin.getDatabase();
        this.resetPlayerHandler = new ResetPlayerHandler();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        event.setQuitMessage(null);

        ParticipationHandler.leave(player, player.getWorld());
        setSpawnHandler.removePlayerFromSpawnPoint(player, player.getWorld());
        if (hgWorldNames.contains(player.getWorld().getName())) setSpawnHandler.checkEnoughPlayers(player.getWorld());
        new ScoreBoardHandler(plugin, langHandler).removeScoreboard(player);
        removeBossBar(player);

        if (plugin.isDatabaseEnabled()) {
            UUID uuid = player.getUniqueId();
            String name = player.getName();
            Long accumulated = totalTimeSpent.remove(player);
            long elapsed = accumulated == null ? 0 : accumulated;
            if (accumulated != null && gameStarted.getOrDefault(player.getWorld().getName(), false)) {
                elapsed += Math.max(0, configHandler.getWorldConfig(player.getWorld()).getInt("game-time")
                        - timeLeft.getOrDefault(player.getWorld().getName(), 0));
            }
            long playtime = elapsed;
	        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
		        try {
			        PlayerStatsHandler playerStats;
			        if (statsMap.containsKey(uuid)) {
				        playerStats = statsMap.get(uuid);
			        } else {
				        playerStats = databaseHandler.getPlayerStatsFromDatabase(uuid.toString(), name);
				        statsMap.put(uuid, playerStats);
			        }

			        playerStats.setLastLogout(new Date());

                playerStats.setSecondsPlayed(playerStats.getSecondsPlayed() + playtime);
                playerStats.setSecondsPlayedMonth(playerStats.getSecondsPlayedMonth() + playtime);

			        playerStats.setDirty();

			        plugin.getDatabase().updatePlayerStats(playerStats);
		        } catch (SQLException e) {
			        plugin.getLogger().log(Level.SEVERE, e.toString());
		        }
	        });
        }

        signHandler.setSignContent();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() != GameMode.SPECTATOR) return;
        Player player = event.getPlayer();
        World world = player.getWorld();
        if (!playersAlive.getOrDefault(world.getName(), List.of()).contains(player)
                && !SetSpawnHandler.spawnPointMap.getOrDefault(world.getName(), Map.of()).containsValue(player)) return;
        // The event fires before Bukkit applies the new mode.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.getGameMode() != GameMode.SPECTATOR) return;
            ParticipationHandler.leave(player, world);
            if (!gameStarted.getOrDefault(world.getName(), false)) {
                setSpawnHandler.removePlayerFromSpawnPoint(player, world);
                setSpawnHandler.playersWaiting.getOrDefault(world.getName(), new ArrayList<>()).remove(player);
                if (gameStarting.getOrDefault(world.getName(), false)) setSpawnHandler.checkEnoughPlayers(world);
            }
            signHandler.setSignContent();
        });
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
		if (isInIgnoredWorld(event.getPlayer().getWorld().getName())) return;

        Player player = event.getPlayer();
        List<Player> worldPlayersWaiting = setSpawnHandler.playersWaiting.computeIfAbsent(player.getWorld().getName(), k -> new ArrayList<>());

        if (preparingDeathmatchWorlds.contains(player.getWorld().getName()) && player.getGameMode() != GameMode.SPECTATOR) {
            Location to = event.getTo(), from = event.getFrom();
            if (to != null && (to.getX() != from.getX() || to.getY() != from.getY() || to.getZ() != from.getZ())) {
                Location frozen = from.clone();
                frozen.setYaw(to.getYaw()); frozen.setPitch(to.getPitch());
                event.setTo(frozen);
            }
            return;
        }
        if (worldPlayersWaiting.contains(player)) {
            Location from = event.getFrom();
            Location to = event.getTo();

            assert to != null;
            if (from.getX() != to.getX() || from.getZ() != to.getZ()) {
                if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // A reconnect creates a new Player object; clear registrations by UUID.
        for (World arena : plugin.getServer().getWorlds()) {
            if (!hgWorldNames.contains(arena.getName())) continue;
            ParticipationHandler.leave(player, arena);
            setSpawnHandler.removePlayerFromSpawnPoint(player, arena);
            setSpawnHandler.checkEnoughPlayers(arena);
        }
        String lobbyWorldName = configHandler.getPluginSettings().getString("lobby-world");
        assert lobbyWorldName != null;
        World lobbyWorld = Bukkit.getWorld(lobbyWorldName);
        if (lobbyWorld != null) {
            if (hgWorldNames.contains(player.getWorld().getName())) resetPlayerHandler.resetPlayer(player);
            player.teleport(lobbyWorld.getSpawnLocation());
            if (player.getGameMode() == GameMode.SPECTATOR) {
                player.setGameMode(GameMode.ADVENTURE);
            }
        } else {
            plugin.getLogger().log(Level.SEVERE, "Could not find lobbyWorld [ " + lobbyWorldName + "]");
        }
        event.setJoinMessage(null);

        if (plugin.isDatabaseEnabled()) {
            UUID uuid = player.getUniqueId();
            String name = player.getName();
			plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
				try {
					PlayerStatsHandler playerStats;
					if (statsMap.containsKey(uuid)) {
						playerStats = statsMap.get(uuid);
					} else {
						playerStats = databaseHandler.getPlayerStatsFromDatabase(uuid.toString(), name);
						statsMap.put(uuid, playerStats);
					}

					playerStats.setUsername(name);

					playerStats.setLastLogin(new Date());

					playerStats.setDirty();
				} catch (SQLException e) {
					plugin.getLogger().log(Level.SEVERE, e.toString());
				}
			});
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        World world = player.getWorld();

	    if (isInIgnoredWorld(world.getName())) return;

        List<Player> worldPlayersWaiting = setSpawnHandler.playersWaiting.computeIfAbsent(world.getName(), k -> new ArrayList<>());
        List<Player> worldPlayersAlive = playersAlive.computeIfAbsent(world.getName(), k -> new ArrayList<>());
        List<Player> worldPlayersPlacement = playerPlacements.computeIfAbsent(player.getWorld().getName(), k -> new ArrayList<>());

        if (gameStarted.getOrDefault(world.getName(), false) || gameStarting.getOrDefault(world.getName(), false)) {
            respawnArenas.put(player.getUniqueId(), world.getName());
            if (gameStarted.getOrDefault(world.getName(), false) && worldPlayersAlive.contains(player)) {
                DeathLootHandler.dropInventory(event);
            }
            worldPlayersAlive.remove(player);
            if (configHandler.getWorldConfig(world).getInt("players-per-team") == 1) {
                worldPlayersPlacement.add(player);
            }
            event.setDeathMessage(null);

            player.sendMessage(langHandler.getMessage(player, "game.placed", worldPlayersAlive.size() + 1));

            if (plugin.isDatabaseEnabled()) {
	            PlayerStatsHandler playerStats = statsMap.get(player.getUniqueId());
                if (playerStats != null) {

                    playerStats.setDeaths(playerStats.getDeaths() + 1);

                EntityDamageEvent lastDamage = player.getLastDamageCause();
                EntityDamageEvent.DamageCause lastDamageCause = lastDamage == null ? null : lastDamage.getCause();

                    if (player.getKiller() != null) {
                        playerStats.setPlayerDeaths(playerStats.getPlayerDeaths() + 1);
                    } else if (lastDamageCause == WORLD_BORDER) {
                        playerStats.setBorderDeaths(playerStats.getBorderDeaths() + 1);
                    } else {
                        playerStats.setEnvironmentDeaths(playerStats.getEnvironmentDeaths() + 1);
                    }

                    playerStats.setDirty();
                }
            }

        } else {
            setSpawnHandler.removePlayerFromSpawnPoint(player, world);
            worldPlayersWaiting.remove(player);
        }

        ParticipationHandler.leave(player, world);
        removeBossBar(player);

        signHandler.setSignContent();

        if (configHandler.getPluginSettings().getBoolean("spectating")) {
            if (gameStarted.getOrDefault(world.getName(), false)) {
                player.setGameMode(GameMode.SPECTATOR);
                player.sendTitle("", langHandler.getMessage(player, "spectate.spectating-player"), 5, 20, 10);
                player.sendMessage(langHandler.getMessage(player, "spectate.message"));
                Map<Player, Location> worldDeathLocations = deathLocations.computeIfAbsent(world.getName(), k -> new HashMap<>());
                worldDeathLocations.put(player, player.getLocation());
            }
        }

        Player killer = event.getEntity().getKiller();

        for (Player damager: playerDamagers.computeIfAbsent(player, k -> new HashSet<>())) {
            if (damager != killer) {
                if (plugin.isDatabaseEnabled()) {
	                PlayerStatsHandler playerStats = statsMap.get(damager.getUniqueId());
                    if (playerStats != null) {
                        playerStats.setKillAssists(playerStats.getKillAssists() + 1);

                        playerStats.setDirty();
                    }
                }
            }
        }

        if (killer != null) {
            Map<Player, Integer> worldPlayerKills = playerKills.computeIfAbsent(world.getName(), k -> new HashMap<>());
            worldPlayerKills.put(killer, worldPlayerKills.getOrDefault(killer, 0) + 1);
            List<Map<?, ?>> effectMaps = configHandler.getWorldConfig(world).getMapList("killer-effects");
            for (Map<?, ?> effectMap : effectMaps) {
                String effectName = (String) effectMap.get("effect");
                int duration = (int) effectMap.get("duration");
                int level = (int) effectMap.get("level");
                PotionEffectType effectType = PotionCompatibilityHandler.resolveEffectType(effectName);
                if (effectType != null) {
                    killer.addPotionEffect(new PotionEffect(effectType, duration, level));
                }
            }

            if (plugin.isDatabaseEnabled()) {
	            PlayerStatsHandler playerStats = statsMap.get(killer.getUniqueId());
                if (playerStats != null) {

                    playerStats.setKills(playerStats.getKills() + 1);

                    playerStats.setDirty();
                }
            }
        }

        Location location = player.getLocation();
        world.spawnParticle(Particle.EXPLOSION, player.getLocation(), 10);
        world.spawnParticle(Particle.DUST, location, 50, new Particle.DustOptions(Color.RED, 10f));
        world.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.25f, 0.8f);

        if (gameStarted.getOrDefault(world.getName(), false)) {
            for (Player p : world.getPlayers()) {
                langHandler.getLangConfig(p);
                if (killer != null) {
	                p.sendMessage(langHandler.getMessage(player, "game.killed-message", player.getName(), killer.getName()));
                } else {
                    p.sendMessage(langHandler.getMessage(player, "game.death-message", player.getName()));
                }
            }
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        String pendingArena = respawnArenas.remove(player.getUniqueId());
        String arenaName = pendingArena != null ? pendingArena : player.getWorld().getName();
        if (isInIgnoredWorld(arenaName)) return;
        Map<Player, Location> worldDeathLocations = deathLocations.computeIfAbsent(arenaName, k -> new HashMap<>());
        boolean eliminated = pendingArena != null || worldDeathLocations.containsKey(player);
        boolean active = gameStarted.getOrDefault(arenaName, false);
        if (eliminated && (!active || !configHandler.getPluginSettings().getBoolean("spectating"))) {
            World lobby = plugin.getServer().getWorld(configHandler.getPluginSettings().getString("lobby-world", "world"));
            if (lobby != null) {
                worldDeathLocations.remove(player);
                event.setRespawnLocation(lobby.getSpawnLocation());
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    resetPlayerHandler.resetPlayer(player);
                    player.teleport(lobby.getSpawnLocation());
                    new ScoreBoardHandler(plugin, langHandler).removeScoreboard(player);
                });
                return;
            }
            plugin.getLogger().warning("Cannot return eliminated player to lobby: configured lobby world is not loaded.");
        }

        if (worldDeathLocations.containsKey(player)) {
            event.setRespawnLocation(worldDeathLocations.get(player));
            worldDeathLocations.remove(player);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && gameStarted.getOrDefault(arenaName, false)) {
                    player.setGameMode(GameMode.SPECTATOR);
                }
            });
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

	    if (isInIgnoredWorld(player.getWorld().getName())) return;

        if (player.getGameMode() == GameMode.SPECTATOR) {
            if (event.getClickedBlock() != null) {
                Material blockType = event.getClickedBlock().getType();
                if (blockType == Material.CHEST || blockType == Material.TRAPPED_CHEST || blockType == Material.BARREL || blockType == Material.RED_SHULKER_BOX) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (hgWorldNames.contains(event.getBlock().getWorld().getName())
                && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPortableCrafting(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!gameStarted.getOrDefault(player.getWorld().getName(), false)
                || celebratingWorlds.contains(player.getWorld().getName())
                || !playersAlive.getOrDefault(player.getWorld().getName(), Collections.emptyList()).contains(player)) return;
        if (event.getItem() != null && event.getItem().getType() == Material.CRAFTING_TABLE
                && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            player.openWorkbench(null, true);
        }
    }

    @EventHandler
    public void onArenaExit(PlayerChangedWorldEvent event) {
        if (!hgWorldNames.contains(event.getFrom().getName())) return;
        ParticipationHandler.leave(event.getPlayer(), event.getFrom());
        setSpawnHandler.removePlayerFromSpawnPoint(event.getPlayer(), event.getFrom());
        setSpawnHandler.checkEnoughPlayers(event.getFrom());
        new ScoreBoardHandler(plugin, langHandler).removeScoreboard(event.getPlayer());
        removeBossBar(event.getPlayer(), event.getFrom());
        signHandler.setSignContent();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBusyArenaTeleport(PlayerTeleportEvent event) {
        Location target = event.getTo();
        if (target != null && target.getWorld() != null && WorldResetHandler.busyWorlds.contains(target.getWorld().getName())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§eLa arena se está preparando. Esperá unos segundos.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEnderLoot(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.ENDER_CHEST || !hgWorldNames.contains(block.getWorld().getName())) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND
                || player.getGameMode() == GameMode.SPECTATOR || !gameStarted.getOrDefault(block.getWorld().getName(), false)
                || !playersAlive.getOrDefault(block.getWorld().getName(), List.of()).contains(player)) return;
        if (event.getItem() != null && event.getItem().getType() == Material.CRAFTING_TABLE) return;
        org.bukkit.inventory.Inventory loot = EnderLootHandler.existingAt(block.getLocation());
        if (loot != null) player.openInventory(loot);
        else player.sendMessage("§eEste cofre no está registrado. Un administrador debe ejecutar /hg scanarena.");
    }

    @EventHandler
    public void onSpectatorInventory(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && hgWorldNames.contains(player.getWorld().getName())
                && player.getGameMode() == GameMode.SPECTATOR) event.setCancelled(true);
    }

    @EventHandler
    public void onSpectatorDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && hgWorldNames.contains(player.getWorld().getName())
                && player.getGameMode() == GameMode.SPECTATOR) event.setCancelled(true);
    }

    @EventHandler
    public void onCelebrationDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player && (celebratingWorlds.contains(event.getEntity().getWorld().getName())
                || preparingDeathmatchWorlds.contains(event.getEntity().getWorld().getName()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        Entity damaged = event.getEntity();
        if (damaged instanceof Player && (celebratingWorlds.contains(damaged.getWorld().getName())
                || preparingDeathmatchWorlds.contains(damaged.getWorld().getName()))) {
            event.setCancelled(true);
            return;
        }

	    if (isInIgnoredWorld(damager.getWorld().getName())) return;

        if (damager instanceof Arrow arrow) {
            if (arrow.getShooter() instanceof Player) {
                damager = (Player) arrow.getShooter();
            }
        } else if (damager instanceof Trident trident) {
            if (trident.getShooter() instanceof Player) {
                damager = (Player) trident.getShooter();
            }
        } else if (damager instanceof SpectralArrow spectralArrow) {
            if (spectralArrow.getShooter() instanceof Player) {
                damager = (Player) spectralArrow.getShooter();
            }
        } else if (damager instanceof Firework firework) {
            if (firework.getShooter() instanceof Player) {
                damager = (Player) firework.getShooter();
            }
        }

        if (damager instanceof Player && damaged instanceof LivingEntity) {
            if (plugin.isDatabaseEnabled()) {
	            PlayerStatsHandler playerStats = statsMap.get(damager.getUniqueId());
                if (playerStats != null) {

                    playerStats.setDamageDealt(playerStats.getDamageDealt() + event.getDamage());

                    if (event.getCause() == PROJECTILE) {
                        playerStats.setProjectileDamageDealt(playerStats.getProjectileDamageDealt() + event.getDamage());
                    }

                    playerStats.setDirty();
                }
            }
        }

        if (!(damaged instanceof Player player)) {
            return;
        }

        if (plugin.isDatabaseEnabled()) {
	        PlayerStatsHandler playerStats = statsMap.get(player.getUniqueId());
            if (playerStats != null) {

                playerStats.setDamageTaken(playerStats.getDamageTaken() + event.getDamage());

                if (event.getCause() == PROJECTILE) {
                    playerStats.setProjectileDamageTaken(playerStats.getProjectileDamageTaken() + event.getDamage());
                }

                playerStats.setDirty();
            }
        }

        ItemStack itemInMainHand = player.getInventory().getItemInMainHand();
        ItemStack itemInOffHand = player.getInventory().getItemInOffHand();

        if ((itemInMainHand.getType() == Material.SHIELD || itemInOffHand.getType() == Material.SHIELD) && player.isBlocking()) {
            if (plugin.isDatabaseEnabled()) {
	            PlayerStatsHandler playerStats = statsMap.get(player.getUniqueId());
                if (playerStats != null) {
                    playerStats.setAttacksBlocked(playerStats.getAttacksBlocked() + 1);

                    playerStats.setDirty();
                }
            }
        }

        if (damager instanceof Player damagerPlayer) {
            List<List<Player>> worldTeams = teams.computeIfAbsent(damager.getWorld().getName(), k -> new ArrayList<>());

            if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK || event.getCause() == PROJECTILE) {
                for (List<Player> team : worldTeams) {
                    if (team.contains(damagerPlayer) && team.contains(player)) {
                        event.setCancelled(true);
                        break;
                    }
                }
                playerDamagers.computeIfAbsent(player, k -> new HashSet<>()).add(damagerPlayer);
            }
        }
    }

    @EventHandler
    public void onChestOpen(PlayerInteractEvent event) {
        Player player = event.getPlayer();

	    if (isInIgnoredWorld(player.getWorld().getName())) return;

        Block block = event.getClickedBlock();

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (block != null && (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST || block.getType() == Material.BARREL)) {
                if (plugin.isDatabaseEnabled()) {
	                PlayerStatsHandler playerStats = statsMap.get(player.getUniqueId());
                    if (playerStats != null) {

                        playerStats.setChestsOpened(playerStats.getChestsOpened() + 1);

                        playerStats.setDirty();
                    }
                }
            } else if (block != null && (block.getType() == Material.RED_SHULKER_BOX)) {
                if (plugin.isDatabaseEnabled()) {
	                PlayerStatsHandler playerStats = statsMap.get(player.getUniqueId());
                    if (playerStats != null) {

                        playerStats.setSupplyDropsOpened(playerStats.getSupplyDropsOpened() + 1);

                        playerStats.setDirty();
                    }
                }
            }
        }
    }

    @EventHandler
    public void onProjectileShot(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();

	    if (isInIgnoredWorld(projectile.getWorld().getName())) return;

	    if (!(projectile instanceof Arrow|| projectile instanceof Firework)) {
            return;
        }

        if (!(projectile.getShooter() instanceof Player player)) {
            return;
        }


        if (plugin.isDatabaseEnabled()) {
	        PlayerStatsHandler playerStats = statsMap.get(player.getUniqueId());
            if (playerStats != null) {

                if (projectile instanceof Arrow) {
                    playerStats.setArrowsShot(playerStats.getArrowsShot() + 1);
                } else {
                    playerStats.setFireworksShot(playerStats.getFireworksShot() + 1);
                }

                playerStats.setDirty();
            }
        }
    }

    @EventHandler
    public void onProjectileLanded(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();

	    if (isInIgnoredWorld(projectile.getWorld().getName())) return;


	    if (!(projectile instanceof Arrow || projectile instanceof SpectralArrow || projectile instanceof Firework)) {
            return;
        }

        if (!(projectile.getShooter() instanceof Player player)) {
            return;
        }

        if(!(event.getHitEntity() instanceof LivingEntity)) {
            return;
        }

        if (plugin.isDatabaseEnabled()) {
	        PlayerStatsHandler playerStats = statsMap.get(player.getUniqueId());
            if (playerStats != null) {

                if (projectile instanceof Arrow || projectile instanceof SpectralArrow) {
                    playerStats.setArrowsLanded(playerStats.getArrowsLanded() + 1);
                } else {
                    playerStats.setFireworksLanded(playerStats.getFireworksLanded() + 1);
                }

                playerStats.setDirty();
            }
        }
    }

    @EventHandler
    public void onEntityRegenerateHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

	    if (isInIgnoredWorld(player.getWorld().getName())) return;

	    double healthRegenerated = event.getAmount();

        if (plugin.isDatabaseEnabled()) {
	        PlayerStatsHandler playerStats = statsMap.get(player.getUniqueId());
            if (playerStats != null) {

                playerStats.setHealthRegenerated(playerStats.getHealthRegenerated() + healthRegenerated);

                playerStats.setDirty();
            }
        }
    }

    @EventHandler
    public void onPlayerConsumeItem(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack consumedItem = event.getItem();

	    if (isInIgnoredWorld(player.getWorld().getName())) return;

	    if (plugin.isDatabaseEnabled()) {
	        PlayerStatsHandler playerStats = statsMap.get(player.getUniqueId());
            if (playerStats != null) {

                if (consumedItem.getType() == Material.POTION) {
                    playerStats.setPotionsUsed(playerStats.getPotionsUsed() + 1);
                } else {
                    playerStats.setFoodConsumed(playerStats.getFoodConsumed() + 1);
                }

                playerStats.setDirty();
            }
        }
    }

    @EventHandler
    public void onPotionSplash(PotionSplashEvent event) {
        if (!(event.getPotion().getShooter() instanceof Player player)) {
            return;
        }

	    if (isInIgnoredWorld(player.getWorld().getName())) return;

	    if (plugin.isDatabaseEnabled()) {
	        PlayerStatsHandler playerStats = statsMap.get(player.getUniqueId());
            if (playerStats != null) {

                playerStats.setPotionsUsed(playerStats.getPotionsUsed() + 1);

                playerStats.setDirty();
            }
        }
    }

    @EventHandler
    public void onLingeringPotionSplash(LingeringPotionSplashEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) {
            return;
        }

	    if (isInIgnoredWorld(player.getWorld().getName())) return;

        if (plugin.isDatabaseEnabled()) {
	        PlayerStatsHandler playerStats = statsMap.get(player.getUniqueId());
            if (playerStats != null) {

                playerStats.setPotionsUsed(playerStats.getPotionsUsed() + 1);

                playerStats.setDirty();
            }
        }
    }

    @EventHandler
    public void onTotemPopped(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

	    if (isInIgnoredWorld(player.getWorld().getName())) return;

	    if (event.isCancelled()) {
            return;
        }

        if (plugin.isDatabaseEnabled()) {
	        PlayerStatsHandler playerStats = statsMap.get(player.getUniqueId());
            if (playerStats != null) {

                playerStats.setTotemsPopped(playerStats.getTotemsPopped() + 1);

                playerStats.setDirty();
            }
        }
    }

	private boolean isInIgnoredWorld(String worldName) {
		return configHandler.getPluginSettings().getStringList("ignored-worlds").contains(worldName)
				|| worldName.equals(configHandler.getPluginSettings().getString("lobby-world"));
	}
}
