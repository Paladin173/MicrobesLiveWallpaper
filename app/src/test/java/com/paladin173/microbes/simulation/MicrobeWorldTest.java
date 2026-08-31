package com.paladin173.microbes.simulation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import org.junit.Test;

public final class MicrobeWorldTest {
    @Test
    public void startsWithOriginalActivePopulationAndFood() {
        MicrobeWorld world = new MicrobeWorld(123L);

        assertEquals(MicrobeWorld.INITIAL_COUNT, world.getCount());
        assertEquals(50, world.getFoodCount());
        assertTrue(world.getCount() < MicrobeWorld.MAX_COUNT);
        assertEquals("all four original types should be present", 0b1111, world.getActiveTypeMask());
    }

    @Test
    public void decodedTypeTableMatchesOriginalApk() {
        float[][] expected = {
                {0.83203125f, 0.19531f, 0.14453f, 0.68f},
                {0.9296875f, 0.6953125f, 0.066406f, 0.68f},
                {0.05078125f, 0.5976525f, 0.22266f, 0.68f},
                {0.19921875f, 0.41015625f, 0.90625f, 0.68f}
        };

        for (int type = 0; type < expected.length; type++) {
            assertEquals(expected[type][0], MicrobeWorld.ORIGINAL_TYPES[type][0], 0f);
            assertEquals(expected[type][1], MicrobeWorld.ORIGINAL_TYPES[type][1], 0f);
            assertEquals(expected[type][2], MicrobeWorld.ORIGINAL_TYPES[type][2], 0f);
            assertEquals(expected[type][3], MicrobeWorld.ORIGINAL_TYPES[type][3], 0f);
        }
        assertEquals(
                MicrobeWorld.ORIGINAL_TYPES[0][3],
                MicrobeWorld.ORIGINAL_TYPES[3][3],
                0f
        );
    }

    @Test
    public void narrowViewportCenterCropsAndUnfoldRestoresExpandedWorld() {
        MicrobeWorld world = new MicrobeWorld(123L);
        FloatBuffer positions = allocate(MicrobeWorld.MAX_COUNT * 4);
        FloatBuffer colors = allocate(MicrobeWorld.MAX_COUNT * 4);

        world.setViewport(2000, 2000);
        world.writeMicrobeRenderData(positions, colors, 2000);
        float expandedX = positions.get(0);

        world.setViewport(1000, 2000);
        world.writeMicrobeRenderData(positions, colors, 2000);
        float coverX = positions.get(0);

        world.setViewport(2000, 2000);
        world.writeMicrobeRenderData(positions, colors, 2000);
        float unfoldedX = positions.get(0);

        assertEquals(expandedX * 2f, coverX, 0.0001f);
        assertEquals(expandedX, unfoldedX, 0.0001f);
        world.setViewport(1000, 2000);
        assertEquals(0.25f, world.screenToWorldX(0f), 0.0001f);
        assertEquals(0.75f, world.screenToWorldX(1f), 0.0001f);
    }

    @Test
    public void sameAreaRotationDoesNotPermanentlyCropWorld() {
        MicrobeWorld world = new MicrobeWorld(123L);
        FloatBuffer positions = allocate(MicrobeWorld.MAX_COUNT * 4);
        FloatBuffer colors = allocate(MicrobeWorld.MAX_COUNT * 4);

        world.setViewport(2000, 1000);
        world.writeMicrobeRenderData(positions, colors, 1000);
        float landscapeX = positions.get(0);

        world.setViewport(1000, 2000);
        world.writeMicrobeRenderData(positions, colors, 2000);

        assertEquals(landscapeX, positions.get(0), 0.0001f);
        assertEquals(0f, world.screenToWorldX(0f), 0.0001f);
        assertEquals(1f, world.screenToWorldX(1f), 0.0001f);
    }

    @Test
    public void tapDepositsFiveFoodParticles() {
        MicrobeWorld world = new MicrobeWorld(123L);
        int initialFood = world.getFoodCount();

        world.feed(0.5f, 0.5f);

        assertEquals(initialFood + 5, world.getFoodCount());
    }

    @Test
    public void microbesConsumeFood() {
        MicrobeWorld world = new MicrobeWorld(123L);

        for (int frame = 0; frame < 60 * 30; frame++) {
            world.update(1f / 60f);
        }

        assertTrue(world.getConsumedFoodCount() > 0);
    }

    @Test
    public void wellFedMicrobesGrowThenSplitIntoSmallOffspring() {
        MicrobeWorld world = new MicrobeWorld(123L, false);
        world.setLifecycleScale(2f);
        float birthScale = world.getLargestGrowthScale();
        float largestObservedScale = birthScale;
        FloatBuffer positions = allocate(MicrobeWorld.MAX_COUNT * 4);
        FloatBuffer colors = allocate(MicrobeWorld.MAX_COUNT * 4);

        for (int frame = 0; frame < 60 * 120 && world.getReproductionCount() == 0; frame++) {
            if (frame % 15 == 0) {
                world.writeMicrobeRenderData(positions, colors, 1000);
                float firstX = (positions.get(0) + 1f) * 0.5f;
                float firstY = (1f - positions.get(1)) * 0.5f;
                world.feed(firstX, firstY);
            }
            world.update(1f / 60f);
            largestObservedScale = Math.max(largestObservedScale, world.getLargestGrowthScale());
        }

        assertEquals(0.55f, birthScale, 0.0001f);
        assertTrue("explicitly placed food was never consumed", world.getConsumedFoodCount() > 0);
        assertTrue("well-fed microbes never grew", largestObservedScale > birthScale + 0.2f);
        assertTrue("well-fed microbes never reproduced", world.getReproductionCount() > 0);
        assertEquals(0.55f, world.getSmallestGrowthScale(), 0.0001f);
    }

    @Test
    public void microbesCannotGrowOrReproduceWithoutFood() {
        MicrobeWorld world = new MicrobeWorld(123L, false);

        for (int frame = 0; frame < 60 * 120; frame++) {
            world.update(1f / 60f);
        }

        assertEquals(0, world.getConsumedFoodCount());
        assertEquals(0, world.getReproductionCount());
        assertEquals(0.55f, world.getLargestGrowthScale(), 0.0001f);
    }

    @Test
    public void starvationCreatesSinkingCorpses() {
        MicrobeWorld world = new MicrobeWorld(123L, false);
        world.setLifecycleScale(2f);

        for (int frame = 0; frame < 60 * 60; frame++) {
            world.update(1f / 60f);
        }

        assertTrue("no microbes completed the death lifecycle", world.getDeathCount() > 0);
        assertTrue("death never produced a corpse", world.getPeakCorpseCount() > 0);
        assertTrue(world.getCount() >= MicrobeWorld.INITIAL_COUNT);
    }

    @Test
    public void decorationLayerUsesOriginalSlotCount() {
        MicrobeWorld world = new MicrobeWorld(123L);
        FloatBuffer positions = allocate(MicrobeWorld.DECORATION_CAPACITY * 3);

        int count = world.writeDecorationRenderData(positions);

        assertEquals(60, count);
        assertEquals(count * 3, positions.remaining());
    }

    @Test
    public void decorationLayerCanBeDisabled() {
        MicrobeWorld world = new MicrobeWorld(123L);
        FloatBuffer positions = allocate(MicrobeWorld.DECORATION_CAPACITY * 3);

        world.setDecorationsEnabled(false);
        int count = world.writeDecorationRenderData(positions);

        assertEquals(0, count);
        assertEquals(0, positions.remaining());
        assertTrue(!world.areDecorationsEnabled());
    }

    @Test
    public void speedSettingsAreClamped() {
        MicrobeWorld world = new MicrobeWorld(123L);

        world.setMovementScale(0f);
        world.setLifecycleScale(10f);

        assertEquals(0.25f, world.getMovementScale(), 0f);
        assertEquals(2f, world.getLifecycleScale(), 0f);
    }

    @Test
    public void longRunningSceneRemainsDistributedAcrossViewport() {
        MicrobeWorld world = new MicrobeWorld(123L);

        for (int frame = 0; frame < 60 * 60 * 5; frame++) {
            world.update(1f / 60f);
        }

        FloatBuffer positions = allocate(MicrobeWorld.MAX_COUNT * 4);
        FloatBuffer colors = allocate(MicrobeWorld.MAX_COUNT * 4);
        int count = world.writeMicrobeRenderData(positions, colors, 1000);
        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (int index = 0; index < count; index++) {
            minX = Math.min(minX, positions.get(index * 4));
            maxX = Math.max(maxX, positions.get(index * 4));
            minY = Math.min(minY, positions.get(index * 4 + 1));
            maxY = Math.max(maxY, positions.get(index * 4 + 1));
        }

        assertTrue("microbes collapsed horizontally", maxX - minX > 1f);
        assertTrue("microbes collapsed vertically", maxY - minY > 1f);
    }

    @Test
    public void populationCanEquilibrateWithoutSaturatingAllSlots() {
        MicrobeWorld world = new MicrobeWorld(123L);

        for (int step = 0; step < 60 * 60 * 2 / 0.05f; step++) {
            world.update(0.05f);
        }

        assertTrue(world.getCount() >= MicrobeWorld.INITIAL_COUNT);
        assertTrue("population saturated every slot", world.getCount() < MicrobeWorld.MAX_COUNT);
        assertTrue("food ecosystem was exhausted", world.getFoodCount() > 0);
    }

    private static FloatBuffer allocate(int floatCount) {
        return ByteBuffer.allocateDirect(floatCount * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
    }
}
