package com.paladin173.microbes.simulation;

import java.nio.FloatBuffer;
import java.util.Random;

public final class MicrobeWorld {
    public static final int MAX_COUNT = 300;
    public static final int INITIAL_COUNT = 30;
    public static final int FOOD_CAPACITY = 600;
    public static final int CORPSE_CAPACITY = 80;
    public static final int DECORATION_CAPACITY = 60;

    private static final int INITIAL_FOOD_COUNT = 50;
    private static final int MOTION_CAPACITY = 15;
    private static final int FOOD_PER_TAP = 5;
    private static final float INVALID = -10f;
    private static final float TWO_PI = 6.2831855f;
    private static final float NEIGHBOR_DISTANCE = 0.038f;
    private static final float FOOD_ATTRACTION_DISTANCE = 0.10f;
    private static final float FOOD_EAT_DISTANCE = 0.014f;
    private static final float MOTION_DURATION_SECONDS = 0.5f;
    private static final float FOOD_RESPAWN_SECONDS = 0.2f;
    private static final float HIGH_ENERGY_LEVEL = 0.75f;
    private static final float BREED_RESET = 0.7f;
    private static final float BREED_THRESHOLD = 1.2f;
    private static final float CORPSE_SINK_RATE = 10f / 800f;
    static final float[][] ORIGINAL_TYPES = {
            {0.83203125f, 0.19531f, 0.14453f, 0.68f},
            {0.9296875f, 0.6953125f, 0.066406f, 0.68f},
            {0.05078125f, 0.5976525f, 0.22266f, 0.68f},
            {0.19921875f, 0.41015625f, 0.90625f, 0.68f}
    };

    private final Random random;
    private final Microbe[] microbes = new Microbe[MAX_COUNT];
    private final Corpse[] corpses = new Corpse[CORPSE_CAPACITY];
    private final float[] foodX = new float[FOOD_CAPACITY];
    private final float[] foodY = new float[FOOD_CAPACITY];
    private final float[] foodPhase = new float[FOOD_CAPACITY];
    private final float[] motionX = new float[MOTION_CAPACITY];
    private final float[] motionY = new float[MOTION_CAPACITY];
    private final float[] motionExpiry = new float[MOTION_CAPACITY];
    private final float[] decorationX = new float[DECORATION_CAPACITY];
    private final float[] decorationY = new float[DECORATION_CAPACITY];
    private final float[] decorationDepth = new float[DECORATION_CAPACITY];
    private final boolean ambientFoodEnabled;

    private float elapsedSeconds;
    private float foodRespawnTimer;
    private float movementScale = 0.6f;
    private float lifecycleScale = 1f;
    private float currentAspect = 1f;
    private float expandedAspect = 1f;
    private long largestViewportArea;
    private boolean decorationsEnabled = true;
    private int activeCount;
    private int foodCount;
    private int consumedFoodCount;
    private int reproductionCount;
    private int corpseCount;
    private int deathCount;
    private int peakCorpseCount;

    public MicrobeWorld() {
        this(System.nanoTime());
    }

    MicrobeWorld(long seed) {
        this(seed, true);
    }

    MicrobeWorld(long seed, boolean ambientFoodEnabled) {
        random = new Random(seed);
        this.ambientFoodEnabled = ambientFoodEnabled;
        for (int index = 0; index < MAX_COUNT; index++) {
            microbes[index] = new Microbe(random);
        }
        for (int index = 0; index < CORPSE_CAPACITY; index++) {
            corpses[index] = new Corpse();
        }
        for (int index = 0; index < FOOD_CAPACITY; index++) {
            foodX[index] = INVALID;
            foodPhase[index] = random.nextFloat();
        }
        for (int index = 0; index < INITIAL_COUNT; index++) {
            activateMicrobe(index, random.nextFloat(), random.nextFloat());
        }
        if (ambientFoodEnabled) {
            for (int index = 0; index < INITIAL_FOOD_COUNT; index++) {
                placeFood(random.nextFloat(), random.nextFloat());
            }
        }
        for (int index = 0; index < DECORATION_CAPACITY; index++) {
            decorationX[index] = random.nextFloat();
            decorationY[index] = random.nextFloat();
            decorationDepth[index] = random.nextFloat();
        }
    }

    public synchronized void setMovementScale(float movementScale) {
        this.movementScale = clamp(movementScale, 0.25f, 1.5f);
    }

    public synchronized void setLifecycleScale(float lifecycleScale) {
        this.lifecycleScale = clamp(lifecycleScale, 0.5f, 2f);
    }

    public synchronized void setDecorationsEnabled(boolean decorationsEnabled) {
        this.decorationsEnabled = decorationsEnabled;
    }

    public synchronized void setViewport(int width, int height) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        long area = (long) safeWidth * safeHeight;
        float orientationIndependentAspect =
                (float) Math.min(safeWidth, safeHeight) / Math.max(safeWidth, safeHeight);
        boolean coverSized = largestViewportArea > 0L
                && area < largestViewportArea * 0.8f;
        currentAspect = orientationIndependentAspect;
        if (!coverSized) {
            largestViewportArea = Math.max(largestViewportArea, area);
            expandedAspect = orientationIndependentAspect;
        }
    }

    public synchronized float screenToWorldX(float screenX) {
        float visibleFraction = Math.min(1f, currentAspect / expandedAspect);
        return clamp(0.5f + (screenX - 0.5f) * visibleFraction, 0f, 1f);
    }

    public synchronized void motion(float x, float y) {
        int slot = 0;
        for (int index = 0; index < MOTION_CAPACITY; index++) {
            if (motionExpiry[index] <= elapsedSeconds) {
                slot = index;
                break;
            }
        }
        motionX[slot] = clamp(x, 0f, 1f);
        motionY[slot] = clamp(y, 0f, 1f);
        motionExpiry[slot] = elapsedSeconds + MOTION_DURATION_SECONDS;
    }

    public synchronized void feed(float x, float y) {
        for (int index = 0; index < FOOD_PER_TAP; index++) {
            float foodX = x + (random.nextFloat() * 2f - 1f) * 0.044f;
            float foodY = y + (random.nextFloat() * 2f - 1f) * 0.044f;
            if (!placeFood(clamp(foodX, 0f, 1f), clamp(foodY, 0f, 1f))) {
                break;
            }
        }
        motion(x, y);
    }

    public synchronized void update(float deltaSeconds) {
        float dt = Math.min(0.05f, Math.max(0f, deltaSeconds));
        elapsedSeconds += dt;
        float motionDt = dt * movementScale;
        float lifeDt = dt * lifecycleScale;

        updateWandering(motionDt);
        avoidNeighbors(motionDt);
        chaseAndEatFood(motionDt);
        followTouchMotion(motionDt);
        integrateMicrobes(dt, lifeDt);
        updateFood(dt);
        updateCorpses(lifeDt);
        updateLifecycle(lifeDt);
    }

    public synchronized int getCount() {
        return activeCount;
    }

    synchronized int getFoodCount() {
        return foodCount;
    }

    synchronized int getConsumedFoodCount() {
        return consumedFoodCount;
    }

    synchronized int getReproductionCount() {
        return reproductionCount;
    }

    synchronized int getDeathCount() {
        return deathCount;
    }

    synchronized int getPeakCorpseCount() {
        return peakCorpseCount;
    }

    synchronized float getLargestGrowthScale() {
        float largest = 0f;
        for (Microbe microbe : microbes) {
            if (microbe.active) {
                largest = Math.max(largest, growthScale(microbe));
            }
        }
        return largest;
    }

    synchronized float getSmallestGrowthScale() {
        float smallest = Float.POSITIVE_INFINITY;
        for (Microbe microbe : microbes) {
            if (microbe.active) {
                smallest = Math.min(smallest, growthScale(microbe));
            }
        }
        return smallest;
    }

    synchronized float getMovementScale() {
        return movementScale;
    }

    synchronized float getLifecycleScale() {
        return lifecycleScale;
    }

    synchronized boolean areDecorationsEnabled() {
        return decorationsEnabled;
    }

    synchronized int getActiveTypeMask() {
        int mask = 0;
        for (Microbe microbe : microbes) {
            if (microbe.active) {
                mask |= 1 << microbe.type;
            }
        }
        return mask;
    }

    public synchronized int writeMicrobeRenderData(
            FloatBuffer positions,
            FloatBuffer colors,
            int viewportHeight
    ) {
        positions.clear();
        colors.clear();
        float sizeScale = viewportHeight / 800f;
        int rendered = 0;
        for (Microbe microbe : microbes) {
            if (!microbe.active) {
                continue;
            }
            positions.put(worldToNdcX(microbe.x));
            positions.put(1f - microbe.y * 2f);
            positions.put(microbe.angle);
            positions.put(30f * microbe.typeScale * growthScale(microbe) * sizeScale);

            float pulseAge = elapsedSeconds - microbe.pulseTime;
            float pulse = pulseAge >= 0f && pulseAge < 1f
                    ? Math.min(pulseAge * 2f, (1f - pulseAge) * 0.5f)
                    : 0f;
            colors.put(Math.min(1f, microbe.red * 1.1f + pulse));
            colors.put(Math.min(1f, microbe.green * 1.1f + pulse));
            colors.put(Math.min(1f, microbe.blue * 1.1f + pulse));
            colors.put(clamp(microbe.energy, 0f, 1f));
            rendered++;
        }
        positions.flip();
        colors.flip();
        return rendered;
    }

    public synchronized int writeFoodRenderData(FloatBuffer positions) {
        positions.clear();
        int rendered = 0;
        for (int index = 0; index < FOOD_CAPACITY; index++) {
            if (foodX[index] == INVALID) {
                continue;
            }
            positions.put(worldToNdcX(foodX[index]));
            positions.put(1f - foodY[index] * 2f);
            positions.put(foodPhase[index]);
            rendered++;
        }
        positions.flip();
        return rendered;
    }

    public synchronized int writeCorpseRenderData(
            FloatBuffer positions,
            int viewportHeight
    ) {
        positions.clear();
        float sizeScale = viewportHeight / 800f;
        int rendered = 0;
        for (Corpse corpse : corpses) {
            if (!corpse.active) {
                continue;
            }
            positions.put(worldToNdcX(corpse.x));
            positions.put(1f - corpse.y * 2f);
            positions.put(corpse.angle);
            float sinkProgress = clamp(
                    (corpse.y - corpse.startY) / Math.max(0.001f, 1.15f - corpse.startY),
                    0f,
                    1f
            );
            positions.put(corpse.size * (0.72f - sinkProgress * 0.22f) * sizeScale);
            rendered++;
        }
        positions.flip();
        return rendered;
    }

    public synchronized int writeDecorationRenderData(FloatBuffer positions) {
        positions.clear();
        if (!decorationsEnabled) {
            positions.flip();
            return 0;
        }
        for (int index = 0; index < DECORATION_CAPACITY; index++) {
            positions.put(worldToNdcX(decorationX[index]));
            positions.put(1f - decorationY[index] * 2f);
            positions.put(decorationDepth[index]);
        }
        positions.flip();
        return DECORATION_CAPACITY;
    }

    private void updateWandering(float dt) {
        for (Microbe microbe : microbes) {
            if (!microbe.active) {
                continue;
            }
            float boundaryX = boundaryForce(microbe.x);
            float boundaryY = boundaryForce(microbe.y);
            boundaryX += cropReturnForce(microbe.x);
            float turn = (float) Math.cos(elapsedSeconds * 0.3f + microbe.phase * 30f) * 0.01f
                    + (float) Math.cos(elapsedSeconds + microbe.phase * 30f) * 0.03f;
            microbe.angle += turn;
            microbe.velocityX = boundaryX + (float) Math.cos(microbe.angle) * 0.025f;
            microbe.velocityY = boundaryY + (float) Math.sin(microbe.angle) * 0.025f;
            microbe.velocityX *= movementScale;
            microbe.velocityY *= movementScale;
        }
    }

    private void avoidNeighbors(float dt) {
        float distanceSquaredLimit = NEIGHBOR_DISTANCE * NEIGHBOR_DISTANCE;
        for (int first = 0; first < MAX_COUNT; first++) {
            Microbe a = microbes[first];
            if (!a.active) {
                continue;
            }
            int neighbors = 0;
            for (int second = first + 1; second < MAX_COUNT && neighbors < 4; second++) {
                Microbe b = microbes[second];
                if (!b.active) {
                    continue;
                }
                float dx = b.x - a.x;
                float dy = b.y - a.y;
                float distanceSquared = dx * dx + dy * dy;
                if (distanceSquared <= 0.000001f || distanceSquared >= distanceSquaredLimit) {
                    continue;
                }
                float distance = (float) Math.sqrt(distanceSquared);
                float push = (NEIGHBOR_DISTANCE - distance) * 0.8f * movementScale;
                float pushX = dx / distance * push;
                float pushY = dy / distance * push;
                a.velocityX -= pushX;
                a.velocityY -= pushY;
                b.velocityX += pushX;
                b.velocityY += pushY;
                neighbors++;
            }
        }
    }

    private void chaseAndEatFood(float dt) {
        float attractionSquared = FOOD_ATTRACTION_DISTANCE * FOOD_ATTRACTION_DISTANCE;
        float eatSquared = FOOD_EAT_DISTANCE * FOOD_EAT_DISTANCE;
        for (Microbe microbe : microbes) {
            if (!microbe.active) {
                continue;
            }
            if (microbe.energy > 1f) {
                continue;
            }
            int nearbyFood = 0;
            for (int food = 0; food < FOOD_CAPACITY && nearbyFood < 4; food++) {
                if (foodX[food] == INVALID) {
                    continue;
                }
                float dx = foodX[food] - microbe.x;
                float dy = foodY[food] - microbe.y;
                float distanceSquared = dx * dx + dy * dy;
                if (distanceSquared >= attractionSquared) {
                    continue;
                }
                nearbyFood++;
                if (distanceSquared <= eatSquared) {
                    microbe.energy = Math.min(1.2f, microbe.energy
                            + (microbe.energy <= 0.25f ? 0.375f : 0.125f));
                    removeFood(food);
                    consumedFoodCount++;
                    microbe.pulseTime = elapsedSeconds;
                    continue;
                }
                float distance = (float) Math.sqrt(Math.max(distanceSquared, 0.000001f));
                float attraction = distance < 0.025f ? 0.055f : distance < 0.05f ? 0.025f : 0.008f;
                microbe.velocityX += dx / distance * attraction * movementScale;
                microbe.velocityY += dy / distance * attraction * movementScale;
            }
        }
    }

    private void followTouchMotion(float dt) {
        float attractionSquared = FOOD_ATTRACTION_DISTANCE * FOOD_ATTRACTION_DISTANCE;
        for (Microbe microbe : microbes) {
            if (!microbe.active) {
                continue;
            }
            for (int motion = 0; motion < MOTION_CAPACITY; motion++) {
                if (motionExpiry[motion] <= elapsedSeconds) {
                    continue;
                }
                float dx = motionX[motion] - microbe.x;
                float dy = motionY[motion] - microbe.y;
                float distanceSquared = dx * dx + dy * dy;
                if (distanceSquared <= 0.000001f || distanceSquared >= attractionSquared) {
                    continue;
                }
                float distance = (float) Math.sqrt(distanceSquared);
                float attraction = distance < 0.025f ? 0.08f : distance < 0.05f ? 0.045f : 0.018f;
                microbe.velocityX += dx / distance * attraction * movementScale;
                microbe.velocityY += dy / distance * attraction * movementScale;
            }
        }
    }

    private void integrateMicrobes(float dt, float lifeDt) {
        for (Microbe microbe : microbes) {
            if (!microbe.active) {
                continue;
            }
            float speed = (float) Math.sqrt(
                    microbe.velocityX * microbe.velocityX
                            + microbe.velocityY * microbe.velocityY
            );
            float maxSpeed = 0.10f * movementScale;
            if (speed > maxSpeed) {
                microbe.velocityX *= maxSpeed / speed;
                microbe.velocityY *= maxSpeed / speed;
            }
            microbe.x = clamp(microbe.x + microbe.velocityX * dt, 0f, 1f);
            microbe.y = clamp(microbe.y + microbe.velocityY * dt, 0f, 1f);
            if (speed > 0.0001f) {
                microbe.angle = (float) Math.atan2(microbe.velocityY, microbe.velocityX);
            }
            microbe.energy -= 0.0125f * lifeDt;
            if (microbe.energy > HIGH_ENERGY_LEVEL) {
                microbe.energy -= 0.025f * lifeDt;
                microbe.breed += 0.02f * lifeDt;
            }
        }
    }

    private void updateFood(float dt) {
        for (int index = 0; index < FOOD_CAPACITY; index++) {
            if (foodX[index] == INVALID) {
                continue;
            }
            float phase = foodPhase[index];
            float noise = elapsedSeconds + phase * 1000f;
            float direction = (float) Math.sin(noise * 0.1f)
                    + noise * (phase - 0.5f)
                    + (float) Math.sin(phase + noise * 0.01f);
            foodX[index] = clamp(foodX[index] + (float) Math.cos(direction) * dt * 0.004f, 0f, 1f);
            foodY[index] = clamp(foodY[index] + (float) Math.sin(direction) * dt * 0.004f, 0f, 1f);
        }

        foodRespawnTimer += dt * lifecycleScale;
        if (!ambientFoodEnabled) {
            return;
        }
        while (foodRespawnTimer >= FOOD_RESPAWN_SECONDS) {
            foodRespawnTimer -= FOOD_RESPAWN_SECONDS;
            if (!placeFood(random.nextFloat(), random.nextFloat())) {
                break;
            }
        }
    }

    private void updateCorpses(float dt) {
        for (Corpse corpse : corpses) {
            if (!corpse.active) {
                continue;
            }
            corpse.y += CORPSE_SINK_RATE * dt;
            if (corpse.y > 1.15f) {
                corpse.active = false;
                corpseCount--;
            }
        }
    }

    private void updateLifecycle(float dt) {
        for (int index = 0; index < MAX_COUNT; index++) {
            Microbe microbe = microbes[index];
            if (!microbe.active) {
                continue;
            }
            if (microbe.breed >= BREED_THRESHOLD) {
                int child = findInactiveMicrobe();
                if (child >= 0) {
                    reproduce(microbe, microbes[child]);
                }
            }
            if (microbe.energy < 0f) {
                createCorpse(microbe);
                microbe.active = false;
                activeCount--;
                deathCount++;
            }
        }

        while (activeCount < INITIAL_COUNT) {
            int replacement = findInactiveMicrobe();
            if (replacement < 0) {
                break;
            }
            activateMicrobe(replacement, random.nextFloat(), random.nextFloat());
        }
    }

    private void createCorpse(Microbe microbe) {
        Corpse corpse = null;
        for (Corpse candidate : corpses) {
            if (!candidate.active) {
                corpse = candidate;
                break;
            }
            if (corpse == null || candidate.y > corpse.y) {
                corpse = candidate;
            }
        }
        if (!corpse.active) {
            corpseCount++;
            peakCorpseCount = Math.max(peakCorpseCount, corpseCount);
        }
        corpse.active = true;
        corpse.x = microbe.x;
        corpse.y = microbe.y;
        corpse.startY = microbe.y;
        corpse.angle = microbe.angle;
        corpse.size = 30f * microbe.typeScale * growthScale(microbe);
    }

    private void reproduce(Microbe parent, Microbe child) {
        float offsetX = (float) Math.cos(parent.angle) * 0.003f;
        float offsetY = (float) Math.sin(parent.angle) * 0.003f;
        child.active = true;
        child.x = clamp(parent.x - offsetX, 0f, 1f);
        child.y = clamp(parent.y - offsetY, 0f, 1f);
        child.angle = parent.angle + 3.1415927f;
        child.phase = random.nextFloat();
        child.energy = birthEnergy();
        child.breed = BREED_RESET;
        child.type = parent.type;
        child.typeScale = parent.typeScale;
        child.red = parent.red;
        child.green = parent.green;
        child.blue = parent.blue;
        parent.x = clamp(parent.x + offsetX, 0f, 1f);
        parent.y = clamp(parent.y + offsetY, 0f, 1f);
        parent.breed = BREED_RESET;
        activeCount++;
        reproductionCount++;
    }

    private void activateMicrobe(int index, float x, float y) {
        Microbe microbe = microbes[index];
        if (!microbe.active) {
            activeCount++;
        }
        microbe.active = true;
        microbe.x = x;
        microbe.y = y;
        microbe.angle = random.nextFloat() * TWO_PI;
        microbe.phase = random.nextFloat();
        microbe.energy = birthEnergy();
        microbe.breed = BREED_RESET;
        assignOriginalType(microbe, random.nextInt(ORIGINAL_TYPES.length));
    }

    private static void assignOriginalType(Microbe microbe, int type) {
        float[] values = ORIGINAL_TYPES[type];
        microbe.type = type;
        microbe.red = values[0];
        microbe.green = values[1];
        microbe.blue = values[2];
        microbe.typeScale = values[3];
    }

    private boolean placeFood(float x, float y) {
        for (int index = 0; index < FOOD_CAPACITY; index++) {
            if (foodX[index] == INVALID) {
                foodX[index] = x;
                foodY[index] = y;
                foodCount++;
                return true;
            }
        }
        return false;
    }

    private void removeFood(int index) {
        foodX[index] = INVALID;
        foodCount--;
    }

    private int findInactiveMicrobe() {
        for (int index = 0; index < MAX_COUNT; index++) {
            if (!microbes[index].active) {
                return index;
            }
        }
        return -1;
    }

    private static float boundaryForce(float position) {
        if (position < 0.10f) {
            return (0.10f - position) * 0.10f;
        }

        if (position > 0.90f) {
            return (0.90f - position) * 0.10f;
        }
        return 0f;
    }

    private float cropReturnForce(float position) {
        float visibleHalfWidth = Math.min(0.5f, 0.5f * currentAspect / expandedAspect);
        float visibleMinimum = 0.5f - visibleHalfWidth;
        float visibleMaximum = 0.5f + visibleHalfWidth;
        if (position < visibleMinimum) {
            return (visibleMinimum - position) * 0.008f;
        }
        if (position > visibleMaximum) {
            return (visibleMaximum - position) * 0.008f;
        }
        return 0f;
    }

    private float worldToNdcX(float worldX) {
        return (worldX - 0.5f) * 2f * expandedAspect / currentAspect;
    }

    private float birthEnergy() {
        return 0.5f + random.nextFloat() * 0.2f;
    }

    private static float growthScale(Microbe microbe) {
        float progress = clamp(
                (microbe.breed - BREED_RESET) / (BREED_THRESHOLD - BREED_RESET),
                0f,
                1f
        );
        return 0.55f + progress * 0.75f;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class Microbe {
        boolean active;
        float x;
        float y;
        float velocityX;
        float velocityY;
        float angle;
        float phase;
        float typeScale;
        float energy;
        float breed;
        float pulseTime;
        float red;
        float green;
        float blue;
        int type;

        Microbe(Random random) {
            phase = random.nextFloat();
        }
    }

    private static final class Corpse {
        boolean active;
        float x;
        float y;
        float startY;
        float angle;
        float size;
    }
}
