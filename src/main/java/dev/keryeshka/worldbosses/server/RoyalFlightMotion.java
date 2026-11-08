package dev.keryeshka.worldbosses.server;

import net.minecraft.util.Mth;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The same cheap, stateful idle-flight steering for the offloaded King and
 * the real King. Never probes terrain or loads chunks. A turn's remaining
 * ticks, heading and altitude survive both sides of the handoff.
 */
public final class RoyalFlightMotion {
    public static final double WANDER_SPEED = 0.85D;
    public static final double CHASE_SPEED = 1.20D;
    public static final double MAX_VERTICAL_SPEED = 0.14D;
    public static final double WORLD_EDGE = 29_999_744.0D;

    private RoyalFlightMotion() {}

    public static final class State {
        private float heading;
        private int remaining;
        private double rise;
        private double altitude;

        public State(float heading, int remaining, double rise, double altitude) {
            this.heading = heading;
            this.remaining = remaining;
            this.rise = rise;
            this.altitude = altitude;
        }

        public static State begin(float yaw, double altitude) {
            return new State(yaw, 220, 0.0D, altitude);
        }

        public State copy() {
            return new State(heading, remaining, rise, altitude);
        }

        public float heading() { return heading; }
        public int remaining() { return remaining; }
        public double rise() { return rise; }
        public double altitude() { return altitude; }

        public void aimAt(float yaw, double currentAltitude) {
            heading = yaw;
            remaining = 240;
            rise = 0.0D;
            altitude = currentAltitude;
        }

        public void turnAround(float yaw) {
            heading = Mth.wrapDegrees(yaw + 180.0F);
            remaining = 80;
        }
    }

    public record Step(float yaw, double vx, double vy, double vz) {}

    public static Step advanceWander(
            State state, float currentYaw, double x, double y, double z,
            int minBuildHeight, int maxBuildHeight
    ) {
        if (--state.remaining <= 0) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            state.heading = Mth.wrapDegrees(currentYaw
                    + random.nextFloat() * 160.0F - 80.0F);
            state.remaining = 240 + random.nextInt(320);
            state.rise = random.nextDouble(-0.10D, 0.10D);
        }
        float yaw = Mth.approachDegrees(currentYaw, state.heading, 9.0F);
        double radians = yaw * Mth.DEG_TO_RAD;
        double vx = -Math.sin(radians) * WANDER_SPEED;
        double vz = Math.cos(radians) * WANDER_SPEED;
        double floor = minBuildHeight + 28.0D;
        double ceiling = maxBuildHeight - 50.0D;
        double wantedY = Mth.clamp(state.altitude + state.rise * 70.0D, floor, ceiling);
        double vy = Mth.clamp((wantedY - y) * 0.025D,
                -MAX_VERTICAL_SPEED, MAX_VERTICAL_SPEED);
        if (Math.abs(x + vx) >= WORLD_EDGE || Math.abs(z + vz) >= WORLD_EDGE) {
            state.turnAround(yaw);
        }
        return new Step(yaw, vx, vy, vz);
    }
}
