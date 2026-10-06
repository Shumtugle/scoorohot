package com.shumtugle.scoorohot;

/**
 * The player never touches the pedals. This driver reads the road ahead and
 * picks the speed by itself; the player only earns its trust.
 *
 * 1. Corners. A bend of curvature c pushes the car outward in proportion to
 *    speed squared times c, and the wheel pushes back in proportion to speed
 *    times steering lock. So for every bend there is a speed at which holding
 *    the line needs exactly a given share of the lock. The driver allows only
 *    part of the lock, and that part grows with flow: a calm, smooth player is
 *    trusted with up to nine tenths, a nervous one with half.
 * 2. Braking points. Each bend in sight is turned into a speed limit at the
 *    car's current position by running braking backwards from the bend:
 *    v^2 = vCorner^2 + 2 * brake * distance. The lowest limit wins.
 *    A banked bend holds the car better and earns more speed; a bend that leans
 *    the wrong way is taken slower.
 *    Water counts as a bend of its own: a ford is braked for well ahead.
 * 3. Blind crests. Climbing toward a summit with a drop behind it, the driver
 *    lifts a little, less so when flow is high.
 * 4. Edges. Close to the curb the driver gets nervous and slows down;
 *    on the grass it gives up and crawls.
 * 5. Red lights. The driver never stops for a light on its own: braking is
 *    the player's, a drag down. Only the attract-mode driver stops by itself.
 * 6. Traffic. A slower car in the same lane is followed, never rammed from
 *    behind; the gap closes gently. Overtaking is the player's job.
 */
final class AutoThrottle {
    static final int LOOK = 72;

    private AutoThrottle() {}

    static float target(Race r, int pSeg, float pPct) {
        r.stopping = false;
        if (r.state != Race.RUN) return 0f;
        if (r.altitude > 100f) return Race.VMAX * 1.05f; // nothing to slow for in the air
        // a rainbow puddle: more top speed, and tyres that hold twice as well
        boolean boost = r.boosting();
        float vmax = boost ? Race.VMAX * Race.BOOST : Race.VMAX;
        // wet and snowy roads hold less, so the driver trusts the wheel less
        float share = (0.5f + 0.4f * r.flow) * (r.inSpace ? 1f : r.atmos.grip()) * (boost ? 2f : 1f);
        float target = vmax;

        for (int i = 0; i < LOOK; i++) {
            Track.Seg s = r.track.get(pSeg + i);
            float c = Math.abs(s.curve) * s.hold;
            float vc = vmax;
            if (c >= 0.05f) vc = Math.min(1f, share * r.sens / (Race.CENTRIFUGAL * c)) * vmax;
            if (s.water && !r.hovers) vc = Math.min(vc, Race.WATER_SPEED * vmax);
            if (vc >= vmax) continue;
            float dist = Math.max(0f, (i - pPct) * Track.SEG);
            float allowed = (float) Math.sqrt(vc * vc + 2f * Race.BRAKE * dist);
            if (allowed < target) target = allowed;
        }

        Track.Seg here = r.track.get(pSeg);
        Track.Seg ahead = r.track.get(pSeg + 18);
        float slopeHere = (here.y2 - here.y1) / Track.SEG;
        float slopeAhead = (ahead.y2 - ahead.y1) / Track.SEG;
        if (slopeHere > 0.08f && slopeAhead < slopeHere - 0.12f) target *= 0.86f + 0.12f * r.flow;

        // nervous near the curb; uneasy on the wrong side; crawling on the grass
        if (!r.inSpace && r.playerX > 0.8f && !r.onExit) target *= Math.max(0.55f, 1f - (r.playerX - 0.8f) * 1.5f);
        if (!r.inSpace && r.playerX < -0.8f && !r.onOncoming) target *= Math.max(0.55f, 1f - (-r.playerX - 0.8f) * 1.5f);
        if (r.onOncoming) target *= 0.85f;
        if (r.offroad) target = Math.min(target, 0.33f * vmax);
        if (r.pitAuto) target = Math.min(target, 0.3f * vmax); // rolling in for gas
        if (r.health < 0.3f && !r.inSpace) target = Math.min(target, Race.VMAX * 0.76f); // a sick engine tops out at 220

        double pz = r.position + Race.PLAYER_Z;
        if (r.demo) {
            Track.Junction j = r.track.junctionAhead(pSeg);
            if (j != null && j.light != Track.GREEN) {
                double line = j.stopSeg * (double) Track.SEG - Track.SEG * 0.6;
                double dist = line - pz;
                if (dist > -Track.SEG * 0.5) {
                    float stop = (float) Math.sqrt(2 * Race.HARD_BRAKE * Math.max(0, dist));
                    if (stop < target) {
                        target = stop;
                        r.stopping = true;
                    }
                }
            }
        }
        for (int i = 0; i < r.cars.size(); i++) {
            Race.Car car = r.cars.get(i);
            double gap = car.z - pz;
            if (gap < 0 || gap > 30 * Track.SEG) continue;
            if (Math.abs(car.offset - r.playerX) > 0.5f) continue;
            // follow what the car is doing now, so a car stopped at a light is stopped behind
            float follow = car.v * vmax + (float) Math.max(0, gap - 5 * Track.SEG) * 1.4f;
            if (follow < target) target = follow;
        }
        return target;
    }
}
