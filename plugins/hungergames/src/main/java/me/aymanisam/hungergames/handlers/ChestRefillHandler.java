package me.aymanisam.hungergames.handlers;

import me.aymanisam.hungergames.HungerGames;
import org.bukkit.*;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class ChestRefillHandler {
    private final HungerGames plugin;
    private final ConfigHandler configHandler;
    private final LangHandler langHandler;

    public ChestRefillHandler(HungerGames plugin, LangHandler langHandler) {
        this.plugin = plugin;
        this.configHandler = plugin.getConfigHandler();
        this.langHandler = langHandler;
    }

    public void refillChests(World world) {
        YamlConfiguration itemsConfig = configHandler.loadItemsConfig(world);
        if (itemsConfig == null) {
            plugin.getLogger().info("Items config is null");
            return;
        }

        File worldFolder = new File(plugin.getDataFolder() + File.separator + world.getName());
        File chestLocationsFile = new File(worldFolder, "chest-locations.yml");
        FileConfiguration chestLocationsConfig = YamlConfiguration.loadConfiguration(chestLocationsFile);

        List<Location> chestLocations = deserializeLocations(chestLocationsConfig, "chest-locations", world);
        List<Location> barrelLocations = deserializeLocations(chestLocationsConfig, "barrel-locations", world);
        List<Location> trappedChestLocations = deserializeLocations(chestLocationsConfig, "trapped-chests-locations", world);

        int minChestContent = configHandler.getWorldConfig(world).getInt("min-chest-content");
        int maxChestContent = configHandler.getWorldConfig(world).getInt("max-chest-content");

        int minBarrelContent = configHandler.getWorldConfig(world).getInt("min-barrel-content");
        int maxBarrelContent = configHandler.getWorldConfig(world).getInt("max-barrel-content");

        int minTrappedChestContent = configHandler.getWorldConfig(world).getInt("min-trapped-chest-content");
        int maxTrappedChestContent = configHandler.getWorldConfig(world).getInt("max-trapped-chest-content");

        refillInventory(chestLocations, "chest-items", itemsConfig, minChestContent, maxChestContent);
        refillInventory(barrelLocations, "barrel-items", itemsConfig, minBarrelContent, maxBarrelContent);
        refillInventory(trappedChestLocations, "trapped-chest-items", itemsConfig, minTrappedChestContent, maxTrappedChestContent);
        refillInventory(deserializeLocations(chestLocationsConfig, "ender-chests-locations", world),
                itemsConfig.contains("ender-chest-items") ? "ender-chest-items" : "trapped-chest-items",
                itemsConfig, minTrappedChestContent, maxTrappedChestContent);

        for (Player player : world.getPlayers()) {
            player.sendMessage(langHandler.getMessage(player, "chestrefill.refilled"));
        }
    }

    @SuppressWarnings("unchecked")
    private List<Location> deserializeLocations(FileConfiguration config, String key, World world) {
        List<Location> locations = new ArrayList<>();
        for (Map<?, ?> entry : config.getMapList(key)) {
            try {
                Location location = Location.deserialize((Map<String, Object>) entry);
                if (!world.equals(location.getWorld())) throw new IllegalArgumentException("Location belongs to another world");
                locations.add(location);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Arena " + world.getName() + ": invalid " + key + " entry " + entry + ": " + ex.getMessage());
            }
        }
        return locations;
    }

    @SuppressWarnings("unchecked")
    public void refillInventory(List<Location> locations, String itemKey, YamlConfiguration itemsConfig, int minContent, int maxContent) {
        refillInventory(locations, itemKey, itemsConfig, minContent, maxContent, new Random());
    }

    @SuppressWarnings("unchecked")
    void refillInventory(List<Location> locations, String itemKey, YamlConfiguration itemsConfig, int minContent, int maxContent, Random rand) {
        if (minContent < 0 || maxContent < minContent) throw new IllegalArgumentException("Invalid loot range for " + itemKey);
        List<Map<?, ?>> itemsMapList = itemsConfig.getMapList(itemKey);
        if (itemsMapList.isEmpty() && !locations.isEmpty()) {
            plugin.getLogger().warning("No loot configured for " + itemKey + "; existing inventories preserved.");
            return;
        }
        Set<Map<?, ?>> invalidEntries = new HashSet<>();
        Set<ChestIdentity> filledChests = new HashSet<>();
	    for (Location location : locations) {
            Block block = location.getBlock();
            Inventory blockInventory;

            if (block.getType() == Material.ENDER_CHEST) {
                blockInventory = EnderLootHandler.inventoryAt(location);
            } else if (block.getState() instanceof Chest chest) {
                if (!filledChests.add(ChestIdentity.of(chest, location))) continue;
                blockInventory = chest.getInventory();
            } else if (block.getState() instanceof Barrel barrel) {
                blockInventory = barrel.getInventory();
            } else if (block.getState() instanceof ShulkerBox shulkerBox) {
                blockInventory = shulkerBox.getInventory();
            } else {
                continue;
            }

            List<ItemStack> items = new ArrayList<>();
            List<ItemStack> bonuses = new ArrayList<>();
            boolean hasValidEntry = false;
            for (Map<?, ?> itemMap : itemsMapList) {
                if (invalidEntries.contains(itemMap)) continue;
                try {
                    ItemStack item = createItem(itemMap, rand);
                    if (itemMap.containsKey("chance")) {
                        if (rollChance(itemMap, rand)) bonuses.add(item);
                    } else {
                        int weight = itemMap.get("weight") instanceof Number value ? value.intValue() : 1;
                        if (weight < 0 || weight > 10000) throw new IllegalArgumentException("Weight must be between 0 and 10000");
                        items.addAll(Collections.nCopies(weight, item));
                    }
                    hasValidEntry = true;
                } catch (RuntimeException ex) {
                    invalidEntries.add(itemMap);
                    plugin.getLogger().warning("Invalid loot in " + itemKey + " (" + itemMap + "): " + ex.getMessage());
                }
            }
            if (!hasValidEntry) continue;

            int inventorySize = rand.nextInt(maxContent - minContent + 1) + minContent;
            Collections.shuffle(items, rand);

            inventorySize = Math.min(inventorySize, items.size());
            items = "chest-items".equals(itemKey) && itemsConfig.getBoolean("ensure-weapon-and-food", true)
                    ? balancedSelection(items, inventorySize) : new ArrayList<>(items.subList(0, inventorySize));
            // Percent chances are rolled once per container, independently of weighted loot.
            items.addAll(bonuses);
            inventorySize = Math.min(items.size(), blockInventory.getSize());
            blockInventory.clear();

            int addedItems = 0;
            int totalSlots = blockInventory.getSize();

            while (addedItems < inventorySize) {
                int randomSlot = rand.nextInt(totalSlots);
                if (blockInventory.getItem(randomSlot) == null) {
                    blockInventory.setItem(randomSlot, items.get(addedItems));
                    addedItems++;
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    ItemStack createItem(Map<?, ?> itemMap, Random rand) {
        String type = Objects.requireNonNull((String) itemMap.get("type"), "Missing item type").toUpperCase(Locale.ROOT);
        Material configuredMaterial = Material.getMaterial(type);
        if (configuredMaterial == null || !configuredMaterial.isItem() || configuredMaterial.isAir()) {
            throw new IllegalArgumentException("Unknown item material: " + type);
        }

        int amount = rollAmount(itemMap, rand);
        if (amount < 1 || amount > 64) throw new IllegalArgumentException("Item amount must be between 1 and 64");

        ItemStack item;

        if (type.equals("POTION") || type.equals("SPLASH_POTION") || type.equals("LINGERING_POTION") || type.equals("TIPPED_ARROW")) {
            item = new ItemStack(Objects.requireNonNull(Material.getMaterial(type)), amount);
            PotionMeta potionMeta = (PotionMeta) item.getItemMeta();
            String potionType = (String) itemMap.get("potion-type");
            int level = itemMap.get("level") instanceof Number value ? value.intValue() : 1;
            boolean extended = itemMap.containsKey("extended") && (boolean) itemMap.get("extended");
            assert potionMeta != null;
            potionMeta.setBasePotionType(resolvePotionType(potionType, extended, level));
            item.setItemMeta(potionMeta);
        } else if (itemMap.containsKey("enchantments") || itemMap.containsKey("random-enchantments")) {
            Material material = Material.getMaterial(type);
            assert material != null;
            item = new ItemStack(material, amount);
            Object enchantsObj = itemMap.get("enchantments");
            if (itemMap.get("random-enchantments") instanceof List<?> options && !options.isEmpty()) {
                enchantsObj = List.of(options.get(rand.nextInt(options.size())));
            }
            if (enchantsObj instanceof List<?> enchantList) {
                for (Object enchantObj : enchantList) {
                    if (enchantObj instanceof Map<?, ?> enchantMap) {
                        String enchantmentType = (String) enchantMap.get("type");
                        int level = (int) enchantMap.get("level");
                        Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(enchantmentType.toLowerCase(java.util.Locale.ROOT)));
                        if (enchantment != null) {
                            if (material == Material.ENCHANTED_BOOK) {
                                EnchantmentStorageMeta enchantmentStorageMeta = (EnchantmentStorageMeta) item.getItemMeta();
                                assert enchantmentStorageMeta != null;
                                enchantmentStorageMeta.addStoredEnchant(enchantment, level, true);
                                item.setItemMeta(enchantmentStorageMeta);
                            } else {
                                item.addUnsafeEnchantment(enchantment, level);
                            }
                        }
                    }
                }
            }
        } else if (type.equals("FIREWORK_ROCKET")) {
            item = new ItemStack(Material.FIREWORK_ROCKET, amount);
            FireworkMeta fireworkMeta = (FireworkMeta) item.getItemMeta();
            assert fireworkMeta != null;
            fireworkMeta.setPower((Integer) itemMap.get("power"));

            List<Map<?, ?>> effectsList = (List<Map<?, ?>>) itemMap.get("effects");
            for (Map<?, ?> effectMap : effectsList) {
                FireworkEffect.Type effectType = FireworkEffect.Type.valueOf((String) effectMap.get("type"));
                List<Color> colors = ((List<String>) effectMap.get("colors")).stream().map(this::getColorByName).collect(Collectors.toList());
                List<Color> fadeColors = ((List<String>) effectMap.get("fade-colors")).stream().map(this::getColorByName).collect(Collectors.toList());
                boolean flicker = (boolean) effectMap.get("flicker");
                boolean trail = (boolean) effectMap.get("trail");

                FireworkEffect effect = FireworkEffect.builder()
                        .with(effectType)
                        .withColor(colors)
                        .withFade(fadeColors)
                        .flicker(flicker)
                        .trail(trail)
                        .build();

                fireworkMeta.addEffect(effect);
            }

            item.setItemMeta(fireworkMeta);
        } else {
            Material material = Material.getMaterial(type);
            assert material != null;
            item = new ItemStack(material, amount);
        }

        if (itemMap.containsKey("nbt")) {
            Map<String, Object> nbtData = (Map<String, Object>) itemMap.get("nbt");
            ItemMeta itemMeta = item.getItemMeta();
            assert itemMeta != null;
            PersistentDataContainer dataContainer = itemMeta.getPersistentDataContainer();

            for (Map.Entry<String, Object> entry : nbtData.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                NamespacedKey namespacedKey = new NamespacedKey(plugin, key);

                if (value instanceof String) {
                    dataContainer.set(namespacedKey, PersistentDataType.STRING, (String) value);
                } else if (value instanceof Integer) {
                    dataContainer.set(namespacedKey, PersistentDataType.INTEGER, (Integer) value);
                } else if (value instanceof Double) {
                    dataContainer.set(namespacedKey, PersistentDataType.DOUBLE, (Double) value);
                } else if (value instanceof Byte) {
                    dataContainer.set(namespacedKey, PersistentDataType.BYTE, (Byte) value);
                } else if (value instanceof Long) {
                    dataContainer.set(namespacedKey, PersistentDataType.LONG, (Long) value);
                } else if (value instanceof Float) {
                    dataContainer.set(namespacedKey, PersistentDataType.FLOAT, (Float) value);
                } else if (value instanceof Short) {
                    dataContainer.set(namespacedKey, PersistentDataType.SHORT, (Short) value);
                } else if (value instanceof byte[]) {
                    dataContainer.set(namespacedKey, PersistentDataType.BYTE_ARRAY, (byte[]) value);
                } else if (value instanceof int[]) {
                    dataContainer.set(namespacedKey, PersistentDataType.INTEGER_ARRAY, (int[]) value);
                } else if (value instanceof long[]) {
                    dataContainer.set(namespacedKey, PersistentDataType.LONG_ARRAY, (long[]) value);
                } else if (value instanceof List) {
                    dataContainer.set(namespacedKey, PersistentDataType.STRING, value.toString());
                }
            }

            item.setItemMeta(itemMeta);
        }

        return item;
    }

    // Keep migrated items.yml files working with the potion names used before 1.20.5.
    static PotionType resolvePotionType(String name, boolean extended, int level) {
        String type = Objects.requireNonNull(name, "Missing potion-type").toUpperCase(Locale.ROOT);
        type = switch (type) {
            case "SPEED" -> "SWIFTNESS";
            case "JUMP" -> "LEAPING";
            case "INSTANT_HEAL" -> "HEALING";
            case "INSTANT_DAMAGE" -> "HARMING";
            case "REGEN" -> "REGENERATION";
            default -> type;
        };
        if (!type.startsWith("LONG_") && !type.startsWith("STRONG_")) {
            if (extended && level > 1) throw new IllegalArgumentException("Potion cannot be both extended and upgraded");
            if (extended) type = "LONG_" + type;
            else if (level > 1) type = "STRONG_" + type;
        }
        return PotionType.valueOf(type);
    }

    static int rollAmount(Map<?, ?> itemMap, Random random) {
        Object amount = itemMap.get("amount");
        if (amount instanceof Map<?, ?> range) {
            int min = ((Number) range.get("min")).intValue();
            int max = ((Number) range.get("max")).intValue();
            return min + random.nextInt(max - min + 1);
        }
        return amount instanceof Number number ? number.intValue() : 1;
    }

    static List<ItemStack> balancedSelection(List<ItemStack> available, int count) {
        List<ItemStack> selected = new ArrayList<>();
        if (count == 0) return selected;
        ItemStack weapon = available.stream().filter(item -> item.getType().name().endsWith("_SWORD")
                || item.getType().name().endsWith("_AXE")).findFirst().orElse(null);
        ItemStack food = available.stream().filter(item -> item.getType().isEdible()).findFirst().orElse(null);
        if (weapon != null) selected.add(weapon);
        if (food != null && selected.size() < count) selected.add(food);
        for (ItemStack item : available) {
            if (selected.size() >= count) break;
            if (item == weapon || item == food) continue;
            selected.add(item);
        }
        // Weighted copies can refer to the same stack; preserve the requested amount.
        for (ItemStack item : available) { if (selected.size() >= count) break; selected.add(item); }
        return selected;
    }

    static boolean rollChance(Map<?, ?> itemMap, Random random) {
        double chance = ((Number) itemMap.get("chance")).doubleValue();
        return random.nextDouble() * 100.0 < chance;
    }

    public Color getColorByName(String colorName) {
        return switch (colorName.toUpperCase(java.util.Locale.ROOT)) {
            case "ORANGE" -> Color.ORANGE;
            case "MAGENTA", "PINK" -> Color.FUCHSIA;
            case "LIGHT_BLUE" -> Color.AQUA;
            case "YELLOW" -> Color.YELLOW;
            case "LIME" -> Color.LIME;
            case "GRAY" -> Color.GRAY;
            case "LIGHT_GRAY" -> Color.SILVER;
            case "CYAN" -> Color.TEAL;
            case "PURPLE" -> Color.PURPLE;
            case "BLUE" -> Color.BLUE;
            case "BROWN" -> Color.MAROON;
            case "GREEN" -> Color.GREEN;
            case "RED" -> Color.RED;
            case "BLACK" -> Color.BLACK;
            default -> Color.WHITE;
        };
    }
}
