package me.aymanisam.hungergames.handlers;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeathLootTest {
    @Test void deathDropsStorageArmorAndOffhandOnceEvenWhenInventoryWasKept() {
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(event.getEntity()).thenReturn(player);
        when(player.getInventory()).thenReturn(inventory);
        ItemStack sword = new ItemStack(Material.STONE_SWORD);
        ItemStack apples = new ItemStack(Material.APPLE, 2);
        ItemStack armor = new ItemStack(Material.IRON_CHESTPLATE);
        ItemStack shield = new ItemStack(Material.SHIELD);
        ItemStack[] contents = new ItemStack[41];
        contents[0] = sword; contents[10] = apples; contents[38] = armor; contents[40] = shield;
        contents[20] = new ItemStack(Material.AIR);
        when(inventory.getContents()).thenReturn(contents);
        List<ItemStack> drops = new ArrayList<>(List.of(sword));
        when(event.getDrops()).thenReturn(drops);
        DeathLootHandler.dropInventory(event);
        verify(event).setKeepInventory(false);
        assertEquals(List.of(Material.STONE_SWORD, Material.APPLE, Material.IRON_CHESTPLATE, Material.SHIELD),
                drops.stream().map(ItemStack::getType).toList());
        assertEquals(2, drops.get(1).getAmount());
        assertNotSame(sword, drops.get(0));
        verify(inventory, never()).clear();
    }
}
