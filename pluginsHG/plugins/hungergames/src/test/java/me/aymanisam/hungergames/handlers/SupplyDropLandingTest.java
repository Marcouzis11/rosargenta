package me.aymanisam.hungergames.handlers;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SupplyDropLandingTest {
    @Test void squareBorderCornerOutsideDomeIsRejected() {
        World world = column(Map.of(25, Material.GRASS_BLOCK), 25, -64, 320);
        assertNull(SupplyDropHandler.findLandingY(world, 5, 6, true));
        assertEquals(25, SupplyDropHandler.findLandingY(world, 5, 6, false));
    }
    @Test void groundUnderDomeIsAcceptedInDomeMode() {
        World world = column(Map.of(100, Material.GLASS, 25, Material.GRASS_BLOCK), 100, -64, 320);
        assertTrue(SupplyDropHandler.hasGlassRoof(world, 5, 6));
        assertEquals(25, SupplyDropHandler.findLandingY(world, 5, 6, true));
    }
    @Test void groundAboveGlassCannotBeUsedInDomeMode() {
        World world = column(Map.of(120, Material.GRASS_BLOCK, 100, Material.GLASS, 25, Material.GRASS_BLOCK), 120, -64, 320);
        assertEquals(25, SupplyDropHandler.findLandingY(world, 5, 6, true));
    }
    @Test void domeAtWorldHeightLimitIsStillDetected() {
        World world = column(Map.of(319, Material.TINTED_GLASS, 25, Material.GRASS_BLOCK), 319, -64, 320);
        assertTrue(SupplyDropHandler.hasGlassRoof(world, 5, 6));
        assertEquals(25, SupplyDropHandler.findLandingY(world, 5, 6, true));
    }
    private World column(Map<Integer, Material> blocks, int top, int min, int max) {
        World world = mock(World.class);
        when(world.getHighestBlockYAt(5, 6)).thenReturn(top);
        when(world.getMinHeight()).thenReturn(min); when(world.getMaxHeight()).thenReturn(max);
        when(world.getBlockAt(eq(5), anyInt(), eq(6))).thenAnswer(call -> {
            Block block = mock(Block.class);
            when(block.getType()).thenReturn(blocks.getOrDefault(call.getArgument(1), Material.AIR));
            return block;
        });
        return world;
    }
    @Test void glassDomeIsSkippedAndDropFitsAboveGroundBelowIt() {
        World world = column(Map.of(100, Material.GLASS, 25, Material.GRASS_BLOCK), 100, -64, 320);
        assertEquals(25, SupplyDropHandler.findLandingY(world, 5, 6));
    }
    @Test void stainedAndTintedGlassAreAlsoSkipped() {
        World world = column(Map.of(100, Material.RED_STAINED_GLASS, 80, Material.TINTED_GLASS, 25, Material.GRASS_BLOCK), 100, -64, 320);
        assertEquals(25, SupplyDropHandler.findLandingY(world, 5, 6));
    }
    @Test void lowRoofCannotBeOverwrittenAndUnsupportedColumnIsRejected() {
        World world = column(Map.of(27, Material.GLASS, 25, Material.GRASS_BLOCK), 27, -64, 320);
        assertNull(SupplyDropHandler.findLandingY(world, 5, 6));
    }
    @Test void customWorldGroundBelowMinusSixtyIsSupported() {
        World world = column(Map.of(-90, Material.GRASS_BLOCK), -90, -128, 512);
        assertEquals(-90, SupplyDropHandler.findLandingY(world, 5, 6));
    }
    @Test void existingContainersAreNotUsedAsGroundOrOverwritten() {
        World world = column(Map.of(28, Material.RED_SHULKER_BOX, 27, Material.END_GATEWAY, 26, Material.GRASS_BLOCK), 28, -64, 320);
        assertNull(SupplyDropHandler.findLandingY(world, 5, 6));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = Material.class, names = {
            "STONE", "OAK_PLANKS", "BRICKS", "DIRT", "MOSS_BLOCK", "DIRT_PATH"})
    void roofsAndOtherNonGrassSurfacesAreRejected(Material surface) {
        World world = column(Map.of(100, Material.GLASS, 25, surface), 100, -64, 320);
        assertNull(SupplyDropHandler.findLandingY(world, 5, 6, true));
        assertNull(SupplyDropHandler.findLandingY(world, 5, 6, false));
    }

    @Test void grassBelowHouseRoofIsRejectedEvenWithDomeAboveTheHouse() {
        World world = column(Map.of(100, Material.GLASS, 50, Material.OAK_PLANKS, 25, Material.GRASS_BLOCK), 100, -64, 320);
        assertNull(SupplyDropHandler.findLandingY(world, 5, 6, true));
        assertNull(SupplyDropHandler.findLandingY(world, 5, 6, false));
    }

    @Test void grassWithObstructedDropSpaceIsRejected() {
        World world = column(Map.of(100, Material.GLASS, 27, Material.OAK_PLANKS, 25, Material.GRASS_BLOCK), 100, -64, 320);
        assertNull(SupplyDropHandler.findLandingY(world, 5, 6, true));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = Material.class, names = {
            "STONE", "DIRT", "OAK_LEAVES", "WATER", "OAK_PLANKS"})
    void anyObstructionBetweenGrassAndGlassRejectsCaveOrCoveredGround(Material ceiling) {
        World world = column(Map.of(100, Material.GLASS, 60, ceiling, 25, Material.GRASS_BLOCK), 100, -64, 320);
        assertNull(SupplyDropHandler.findLandingY(world, 5, 6, true));
        assertNull(SupplyDropHandler.findLandingY(world, 5, 6, false));
    }

    @Test void surfaceGrassIsChosenInsteadOfGrassOnCaveFloor() {
        World world = column(Map.of(100, Material.GLASS, 60, Material.GRASS_BLOCK, 55, Material.STONE, 25, Material.GRASS_BLOCK), 100, -64, 320);
        assertEquals(60, SupplyDropHandler.findLandingY(world, 5, 6, true));
    }

    @Test void openWorldCaveFloorIsRejectedWithoutGlass() {
        World world = column(Map.of(60, Material.STONE, 25, Material.GRASS_BLOCK), 60, -64, 320);
        assertNull(SupplyDropHandler.findLandingY(world, 5, 6, false));
    }
}
