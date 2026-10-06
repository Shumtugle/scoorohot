package com.shumtugle.scoorohot;

import java.util.ArrayList;
import java.util.Random;

/**
 * One run: the car, the traffic, the clock and the flow meter.
 * The game is a free ride. Now and then it offers a rally, which starts a
 * fresh road of its own: a time rally is five kilometres to a finish gate,
 * timed; a distance rally is three minutes, measured in kilometres. A rally
 * is a clean race: no saucers, no holes, no puddles, and touching the police
 * is a disqualification rather than a chase.
 *
 * The car wears. Every knock takes something off its condition, by how hard
 * it was: a scraped rail a little, a head-on crash a third. Below 60% it
 * smokes, below 30% it coughs, tops out at 220 and pulls to one side; at zero
 * the engine dies and the car rolls to a stop. Gas stations repair it, or
 * give you another car. Heavy vehicles take less harm.
 *
 * The tank holds about five kilometres of brisk driving. Gas stations lie off
 * the road every couple of kilometres; run dry and the car rolls to a stop,
 * and that is the end. Out among the stars nothing is burnt.
 *
 * A finger on the screen is the throttle: the driver drives. Lift it and the
 * car coasts. Drag down to brake; brake hard at very high speed and the car
 * spins like a top. Steer hard into a crossroads at low speed to turn.
 */
final class Race {
    static final float ROAD_W = 1100f;   // half width of the road in world units
    static final float CAR_W = 560f;
    static final float DEPTH = 0.84f;    // camera depth for a field of view of about 100 degrees
    static final float FILL = 1.1f;      // the road at the car is a little wider than the screen
    static final float PLAYER_Z = DEPTH * ROAD_W / FILL;
    static final float KM_PER_SEG = 0.0015f;
    static final float VMAX_KMH = 290f;
    static final float VMAX = VMAX_KMH / 3.6f / (KM_PER_SEG * 1000f) * Track.SEG;
    static final float CENTRIFUGAL = 0.3f;
    static final float ACCEL = 0.32f * VMAX;
    static final float BOOST = 1.25f;   // top speed in a rainbow, as a share of VMAX
    static final float BRAKE = 0.60f * VMAX;
    static final float START_TIME = 50f;

    static final int COUNT = 0, RUN = 1, TIMEUP = 2, DONE = 3;
    static final int FREE = 0, RALLY_TIME = 1, RALLY_DIST = 2;
    static final float RALLY_KM = 5f, RALLY_SECONDS = 180f;
    // sound cues raised by the race and collected by the view
    static final int CUE_BEEP = 1, CUE_GO = 2, CUE_CHIME = 4, CUE_CRASH = 8, CUE_PASS = 16, CUE_BUMP = 32, CUE_SPLASH = 64,
            CUE_SKID = 128, CUE_WARP = 256, CUE_SPARKLE = 512;
    static final float HARD_BRAKE = 1.5f * VMAX;
    static final float CROSS_HALF = 0.55f;  // half the length of a crossing car, in road half-widths
    static final int CROSS_KINDS = 4;       // crossing cars come from the first traffic models
    static final float CHASE_PULL = 7f * Track.SEG;
    static final float CHASE_GAP = 5f; // where the police start, in segments; any farther and they leave the screen
    static final float WATER_SPEED = 0.42f; // a ford is taken at this share of top speed, unless you fly
    static final float MAX_ROLL = 11f;

    static final class Car {
        double z;
        float offset, speed; // speed as a share of VMAX
        float v;             // what it actually did this frame, also a share of VMAX: zero when queueing
        float lift;          // height above the road while a beam carries it off
        boolean washed;      // swept off by a wave, on its way to the sea
        int branchSide;      // taking the other road of a fork: its side, or 0
        Track.Fork forkSeen; // the fork this car has already chosen at
        int kind, lane, dir = 1; // lane counted from the right; dir -1 for oncoming
        boolean passed;
    }

    Track track;
    final Atmos atmos;
    boolean inSpace, alien, flood; // flood: inSpace as well, but the drowned city and the wave
    Track floodTrack;
    static final int WAVE_LEN = 12;
    boolean waveOn;
    int waveSeg, waveSide;
    float waveFront, waveT, washTime;
    private float nextWave = 110f;
    Track spaceTrack;          // the rabbit hole, laid out lazily like any road
    static final float BEAM_HALF = 0.45f;
    boolean ufoActive, beamOn;
    float ufoT, ufoRel, ufoAlt, beamX, abductTime;
    double ufoZ;
    private float nextUfo = 100f, nextSticky, stickyTime, shotTimer, branchHold, exitHold;
    private float prevGap = -1f, prevExit = -1f;
    float twist;               // the rabbit hole's turn of the whole world, added to the camera roll
    boolean hangarOpen, onExit;   // hangarOpen: in a gas station, the tank filling, a car to choose
    float fuel = 1f, fuelBefore;
    boolean outOfFuel;
    float health = 1f;        // the car's condition, 1 new, 0 dead
    float toughness = 1f;     // how much of each knock this vehicle takes
    boolean wrecked;
    private float pull;       // which way a badly damaged car drifts
    static final float REPAIR_TIME = 6f;
    // wings: a flight of FLIGHT seconds, up to ALTITUDE over the road
    static final float FLIGHT = 18f, ALTITUDE = 2600f;
    float altitude, flightT;
    boolean distant;          // a chase whose police are far behind after a flight
    private float distantT, stoppedT, nextHeart = 60f, nextWings = 150f;
    private boolean closing, stopDecided;
    private boolean lowWarned;
    static final float PIT_STOP = 4f;
    // a turn of the camera: the frame sweeps sideways and, at its blur, the road ahead is laid anew
    static final float PAN = 0.6f;
    static final int PAN_BRANCH = 4, PAN_GARAGE = 5;
    float pan, panDir;
    private int panAction, panArg;
    private boolean panDone;
    private float prevBranch = -1f;
    boolean onBranch;         // on the other road of a fork, before it is decided
    boolean garageIntent;
    // a pit stop asked for with the on-screen button: the driver takes over near the station
    boolean pitIntent, pitAuto;
    int pitStation = -1;
    /** Rainbow dashes the saucer fires: z, lateral position, speed. They never hit. */
    final ArrayList<float[]> shots = new ArrayList<float[]>();
    private Track earthTrack;
    private double earthPos;
    private float earthX;
    private final ArrayList<Car> earthCars = new ArrayList<Car>();
    final boolean demo;
    final ArrayList<Car> cars = new ArrayList<Car>();
    final ArrayList<Car> oncoming = new ArrayList<Car>();
    private final Random rnd;
    private final int carKinds;
    private final float[] carWidths;

    int state;
    float sens = 1f;
    float steerInput;

    double position;
    float speed, playerX, steer, flow = 0.35f, peakFlow;
    float time = START_TIME, countdown = 3f;
    float distanceKm;
    int checkpoints, overtakes;
    int gear = 1;
    boolean braking;
    float bgCloud, bgFar, bgMid;
    float hitFlash;
    String message = "";
    float messageTime;
    int buzz;                 // vibration request in milliseconds, consumed by the view
    int mode = FREE;
    int finishSeg = -1;       // the finish line of a time rally
    boolean dnf, finished;    // a rally ended by the police, or across the line
    float topSpeed;
    float points, elapsed;
    int cues;                 // pending sound cues, consumed by the view
    float roll;               // camera roll in degrees
    int vehicle;              // index into the garage
    float vehicleW = CAR_W;   // world width of the player's vehicle
    float animPhase;          // distance-driven animation frame, for things that roll
    boolean hovers;           // floats above the road: water does not slow it
    boolean inWater;
    boolean touching;         // a finger is on the screen: the throttle
    float brake;              // how far the finger has been dragged down, 0 to 1
    private float turnHold;
    boolean stopping;         // braking hard for a red light
    float spin, spinDir, spinAngle;
    boolean chase, busted;
    float chaseTime, chaseLength, policeGap, policeX;
    float rainbowTime;
    boolean neon;
    boolean offroad, onOncoming;
    float badgeTime, chaseBlend, warpFlash;   // the badge pause, and how far the camera has fallen back
    String chaseReason = "";
    private boolean inHole;
    private float nextPuddle = 20f, nextHole = 50f, bustTimer, steerPeak, steerSign;
    private boolean wasTouching;
    float clock;

    private float prevInput, shiftTimer, hitCooldown, doneTimer;
    private int nextCheckpoint = Track.CHECK_SEGS;
    private float demoLane;
    private int lastCount = 4;

    Race(Track track, float[] carWidths, boolean demo, long seed) {
        this.track = track;
        this.demo = demo;
        this.carWidths = carWidths;
        this.carKinds = carWidths.length;
        this.rnd = new Random(seed);
        this.atmos = new Atmos(seed);
        state = demo ? RUN : COUNT;
        if (demo) {
            speed = VMAX * 0.5f;
            flow = 0.7f;
        }
        nextPuddle = 20f + rnd.nextFloat() * 30f;
        nextUfo = 70f + rnd.nextFloat() * 100f;
        nextHole = 45f + rnd.nextFloat() * 60f;
        int n = demo ? 6 : 8;
        double z = PLAYER_Z + 40 * Track.SEG;
        for (int i = 0; i < n; i++) {
            Car c = new Car();
            c.z = z;
            int lanes = track.get((int) (z / Track.SEG)).lanes;
            c.lane = rnd.nextInt(lanes);
            c.offset = laneX(lanes, c.lane);
            c.speed = 0.30f + rnd.nextFloat() * 0.25f;
            c.v = c.speed;
            c.kind = pickKind();
            cars.add(c);
            z += (22 + rnd.nextInt(30)) * Track.SEG;
        }
    }

    int playerSegment() {
        return (int) ((position + PLAYER_Z) / Track.SEG);
    }

    float playerPercent() {
        double p = (position + PLAYER_Z) / Track.SEG;
        return (float) (p - Math.floor(p));
    }

    void setMode(int m) {
        mode = m;
        time = m == RALLY_DIST ? RALLY_SECONDS : 0f;
        if (m == RALLY_TIME) {
            finishSeg = (int) (RALLY_KM / KM_PER_SEG);
            track.finish(finishSeg);
        }
    }

    boolean rally() {
        return mode != FREE;
    }

    /** Points multiplier: one at no flow, three at full flow. */
    float multiplier() {
        return 1f + 2f * flow;
    }

    void say(String text, float seconds) {
        message = text;
        messageTime = seconds;
    }

    void update(float dt) {
        clock += dt;
        if (chase) chaseBlend = badgeTime > 0 ? 0f : Math.min(1f, chaseBlend + dt / 1.2f);
        else chaseBlend = Math.max(0f, chaseBlend - dt / 1.2f);
        atmos.update(dt, track.get(playerSegment()).theme);
        if (washTime > 0) {
            // carried off toward the sea: the world holds still until the car lands on the board
            washTime -= dt;
            if (washTime <= 0) enterDream(floodTrack, true);
            return;
        }
        if (abductTime > 0) {
            // carried up the beam: the world holds still until the car is aboard
            abductTime -= dt;
            if (abductTime <= 0) enterDream(spaceTrack, false);
            return;
        }
        if (hangarOpen) return; // in the hangar, choosing a car
        if (badgeTime > 0) {
            // the world holds still while the badge says what happened
            badgeTime -= dt;
            return;
        }
        if (messageTime > 0) messageTime -= dt;
        if (hitFlash > 0) hitFlash -= dt;
        if (warpFlash > 0) warpFlash -= dt;
        if (hitCooldown > 0) hitCooldown -= dt;
        if (state == COUNT) {
            countdown -= dt;
            int n = (int) Math.ceil(countdown);
            if (n < lastCount && n > 0) {
                lastCount = n;
                cues |= CUE_BEEP;
            }
            if (countdown <= 0) {
                state = RUN;
                cues |= CUE_GO;
                say("GO!", 0.9f);
            }
        }

        pitStop();
        float in = demo ? autopilot() : pitAuto ? pitSteer() : steerInput;
        if (spin > 0 || busted) in = 0f;
        if (Math.abs(steerInput) > 0.3f) steerSign = Math.signum(steerInput);
        steerPeak = Math.max(steerPeak - dt * 3f, Math.abs(steerInput));
        float rate = Math.abs(in - prevInput) / Math.max(dt, 0.001f);
        prevInput = in;
        steer += (in - steer) * Math.min(1f, dt * 14f);

        int pIdx = playerSegment();
        Track.Seg ps = track.get(pIdx);
        float pct = playerPercent();
        float width = ps.width1 + (ps.width2 - ps.width1) * pct;
        float bank = ps.bank1 + (ps.bank2 - ps.bank1) * pct;
        float sp = speed / VMAX;
        float prevX = playerX;
        // lateral motion is in world terms, so it covers less of a wide road
        float dx = dt * 2f * sp / width;
        boolean boost = boosting();
        boolean aloft = altitude > 100f;
        playerX += dx * steer * sens * (boost ? 1.5f : aloft ? 1.3f : 1f);
        // in the air the road's bends do not pull at the car
        if (!aloft) playerX -= dx * sp * ps.curve * ps.hold * CENTRIFUGAL / (inSpace ? 1f : atmos.grip()) / (boost ? 2f : 1f);
        float leftmost = Track.leftOf(ps);
        // far out in the fields is allowed: getting back to the road is the player's business
        if (playerX < leftmost - 12f) playerX = leftmost - 12f;
        if (playerX > 14f) playerX = 14f;

        // stamping on the brake at very high speed sends the car spinning like a top
        if (!demo && state == RUN && spin <= 0 && brake > 0.7f && speed > VMAX * 0.82f)
            startSpin(steerSign != 0 ? steerSign : (rnd.nextBoolean() ? 1f : -1f));
        wasTouching = touching;
        if (spin > 0) {
            spin -= dt;
            spinAngle += spinDir * dt * 460f;
            speed *= (float) Math.exp(-dt * 2.4f);
            playerX += spinDir * dt * 0.6f * (speed / VMAX + 0.2f);
            if (spin <= 0) spinAngle = 0f;
        } else if (busted) {
            speed = Math.max(0f, speed - HARD_BRAKE * dt);
        } else if (!demo && state == RUN && brake > 0.05f) {
            braking = true;
            stopping = brake > 0.6f;
            speed = Math.max(0f, speed - HARD_BRAKE * brake * dt);
        } else if (!demo && state == RUN && (!touching || fuel <= 0f || health <= 0f)) {
            // no finger, no throttle: the car rolls on and slows
            braking = false;
            speed = Math.max(0f, speed - VMAX * (offroad ? 0.4f : 0.12f) * dt);
        } else {
            drive(dt, AutoThrottle.target(this, pIdx, playerPercent()), ps);
        }

        // the camera takes up most of the bank, so the horizon tilts and the
        // road under the car still shows its slant; speed adds a little lean
        float level = (float) Math.toDegrees(Math.atan(bank / (ROAD_W * width)));
        float rollTarget = -level * 0.6f - ps.curve * sp * 0.9f;
        Track.Seg nx = track.get(pIdx + 1);
        twist = ps.twist + (nx.twist - ps.twist) * pct;
        panning(dt);
        if (rollTarget > MAX_ROLL) rollTarget = MAX_ROLL;
        if (rollTarget < -MAX_ROLL) rollTarget = -MAX_ROLL;
        roll += (rollTarget - roll) * Math.min(1f, dt * 2.5f);

        // the engine drinks by throttle and speed; nothing burns in space or in the attract mode
        if (!demo && !inSpace && state == RUN && fuel > 0f) {
            fuel = Math.max(0f, fuel - dt * (0.0015f + 0.0041f * sp * (touching ? 1f : 0.3f)));
            if (fuel < 0.2f && !lowWarned) {
                lowWarned = true;
                say("LOW FUEL", 1.6f);
            }
            if (fuel <= 0f) say("TANK EMPTY", 2f);
        }
        if (!demo && state == RUN && fuel <= 0f && speed < VMAX * 0.01f) {
            outOfFuel = true;
            state = DONE;
            say("OUT OF FUEL", 3f);
        }
        if (!demo && state == RUN && health <= 0f && speed < VMAX * 0.01f) {
            wrecked = true;
            state = DONE;
            say("WRECKED", 3f);
        }
        // a badly damaged car pulls to one side
        if (!demo && health < 0.3f && health > 0f && !inSpace) playerX += pull * dt * 0.06f * (speed / VMAX);
        double before = position;
        position += speed * dt;
        animPhase += speed * dt / 190f;
        float segs = (float) ((position - before) / Track.SEG);
        bgCloud += 0.0003f * ps.curve * segs;
        bgFar += 0.0009f * ps.curve * segs;
        bgMid += 0.0018f * ps.curve * segs;
        distanceKm += segs * KM_PER_SEG;
        if (state == RUN && !demo) points += segs * KM_PER_SEG * 100f * multiplier();

        boolean wet = ps.water && !hovers;
        if (wet && !inWater) cues |= CUE_SPLASH;
        inWater = wet;
        if (ps.bridge && Math.abs(playerX) > 0.97f && altitude < 100f) {
            // the rails hold the car on the bridge, at a price
            playerX = Math.signum(playerX) * 0.97f;
            if (hitCooldown <= 0) {
                speed *= 0.9f;
                hit(0.15f, 30, CUE_BUMP);
                harm(0.025f);
                say("SCRAPE", 0.6f);
            }
        }
        // across the center line is the oncoming carriageway: asphalt, not grass, but not yours
        float gapP = ps.gap1 + (ps.gap2 - ps.gap1) * pct;
        onOncoming = ps.onc > 0 && playerX < -1f && playerX >= leftmost - 0.1f && playerX <= -1f - gapP + 0.1f;
        // a car on the other carriageway moves with it when it swings in or out
        if (onOncoming && prevGap >= 0) playerX -= gapP - prevGap;
        prevGap = gapP;
        // stay on it while it runs parallel on its way out, and it is your road now: one way, everything coming at you
        if (!demo && onOncoming && ps.oncPar) {
            relabel(track.takeOncoming(pIdx));
            prevGap = -1f;
            say("WRONG WAY!", 2f);
        }
        // a wrong-way road joined from the right: you are on the oncoming side of a proper road again
        if (!demo && ps.mergeEnd && ps.reverse) {
            relabel(track.endReverse(pIdx));
            say("ROADS JOIN", 1.4f);
        }
        // the other road of a fork: ride along with it as it parts; take it while it runs parallel
        float bg = ps.branch ? (ps.forkL >= 0 ? ps.forkL : ps.forkR) : -1f;
        int bside = ps.forkL >= 0 ? -1 : 1;
        onBranch = bg >= 0 && (bside < 0 ? playerX <= -1f - bg + 0.1f && playerX >= -3f - bg - 0.1f
                : playerX >= 1f + bg - 0.1f && playerX <= 3f + bg + 0.1f);
        if (onBranch && prevBranch >= 0) playerX += bside * (bg - prevBranch);
        prevBranch = bg;
        Track.Fork f = ps.fork;
        if (!demo && f != null && !f.decided) {
            int step = pIdx - f.start;
            if (step >= Track.DIV + 4 && onBranch) {
                relabel(track.takeBranch(pIdx));
                prevBranch = -1f;
                say(Track.NAMES[f.other.id] + (f.side < 0 ? " <" : " >"), 1.6f);
            } else if (step >= Track.DIV + Track.PAR - 2) {
                track.choose(f, false);
                say(Track.NAMES[f.straight.id], 1.4f);
            }
        }
        // the slip road to a hangar
        float exitAt = ps.forkR >= 0 && ps.exit ? 1f + 1f / 5.5f + ps.forkR + 0.35f : 99f;
        onExit = !demo && !inSpace && exitAt < 90f && playerX > 1.05f && Math.abs(playerX - exitAt) < 0.6f;
        if (onExit && prevExit >= 0) playerX += ps.forkR - prevExit;
        prevExit = ps.forkR >= 0 ? ps.forkR : -1f;
        // the garage: steering right while its slip road is coming up is enough
        boolean exitNear = false;
        for (int k = 0; k < 120 && !exitNear; k++) {
            Track.Seg e = track.get(pIdx + k);
            if (e.exit && e.forkR < 1.5f) exitNear = true;
        }
        exitHold = !demo && !chase && !inSpace && (exitNear || onExit) && playerX > 0.2f && steerInput > 0.45f ? exitHold + dt : 0f;
        if (exitHold > 0.15f && pan <= 0 && !garageIntent) {
            garageIntent = true;
            say("TO THE PUMPS", 1.2f);
            startPan(1f, PAN_GARAGE, 0);
            exitHold = 0f;
        }
        boolean off = altitude > 100f ? false : inSpace ? Math.abs(playerX) > 1.3f : !onBranch && ((playerX > 1f && !onExit) || (playerX < -1f && !onOncoming));
        offroad = off;
        roadside(off, width);
        traffic(dt, width);
        oncomingTraffic(dt, width);
        crossroads(dt, pIdx, width);
        chase(dt);
        flight(dt);
        if (!inSpace) finds(dt, pIdx, width);
        if (inSpace) sticky(dt, pIdx, width);
        else wonders(dt, pIdx, width);
        ufo(dt, width);
        wave(dt, pIdx);
        updateFlow(dt, rate, off, prevX);
        clockAndGates(dt);
    }

    // ---------------------------------------------------------------- drive

    private void drive(float dt, float target, Track.Seg ps) {
        float sp = speed / VMAX;
        int g = 1 + Math.min(4, (int) (sp * 5f));
        if (g > gear) shiftTimer = 0.2f;
        gear = g;
        float slope = (ps.y2 - ps.y1) / Track.SEG;
        if (speed < target) {
            braking = false;
            if (shiftTimer > 0) {
                shiftTimer -= dt;
            } else {
                boolean boost = boosting();
                float acc = ACCEL * (1.12f * (boost ? BOOST : 1f) - sp) * (boost ? 1.6f : 1f) - slope * VMAX * 0.35f;
                if (acc < ACCEL * 0.05f) acc = ACCEL * 0.05f;
                speed = Math.min(target, speed + acc * dt);
            }
        } else {
            float excess = speed - target;
            if (state == TIMEUP || state == DONE) {
                braking = false;
                speed = Math.max(0f, speed - VMAX * 0.22f * dt);
            } else {
                braking = excess > VMAX * 0.015f;
                float dec = braking ? (stopping ? HARD_BRAKE : BRAKE) : VMAX * 0.2f;
                speed = Math.max(target, speed - dec * dt);
            }
        }
        if (speed < 0) speed = 0;
    }

    // ---------------------------------------------------------- collisions

    private void roadside(boolean off, float width) {
        if (!off || hitCooldown > 0) return;
        float half = vehicleW / (ROAD_W * width) * 0.5f;
        int p = playerSegment();
        for (int k = p - 1; k <= p; k++) {
            Track.Seg s = track.get(k);
            for (int i = 0; i < s.nProps; i++) {
                Track.Prop pr = s.props[i];
                if (!pr.solid) continue;
                float w = pr.worldW / (ROAD_W * width);
                float l = Track.propX(s, pr) + pr.anchor * w + w * 0.2f;
                float rr = l + w * 0.6f;
                if (playerX + half > l && playerX - half < rr) {
                    harm(0.06f + 0.09f * Math.min(1f, speed / VMAX));
                    speed = Math.min(speed, VMAX * 0.12f);
                    hit(0.4f, 80, CUE_CRASH);
                    say("CRASH!", 0.8f);
                    return;
                }
            }
        }
    }

    private void hit(float flowLoss, int ms, int cue) {
        flow = Math.max(0f, flow - flowLoss);
        if (chase) policeGap -= 1.5f; // every bump lets them gain
        hitFlash = 0.3f;
        hitCooldown = 0.7f;
        if (!demo) {
            buzz = ms;
            cues |= cue;
        }
    }

    private void traffic(float dt, float width) {
        double pz = position + PLAYER_Z;

        for (int i = 0; i < cars.size(); i++) {
            Car c = cars.get(i);
            if (c.washed) {
                c.offset += waveSide * dt * 3f;
                if (Math.abs(c.offset) > 7f) {
                    c.washed = false;
                    respawn(c, pz);
                }
                continue;
            }
            if (c.lift > 0) {
                c.lift += dt * 2400f;
                if (c.lift > 4200f) {
                    c.lift = 0f;
                    respawn(c, pz);
                }
                continue;
            }
            Track.Seg cs = track.get((int) (c.z / Track.SEG));
            // at a fork some of the traffic takes the other road, so it is not empty
            if (cs.fork != null && c.forkSeen != cs.fork && !cs.fork.decided) {
                c.forkSeen = cs.fork;
                if (rnd.nextFloat() < 0.4f) c.branchSide = cs.fork.side;
            }
            float bg = c.branchSide < 0 ? cs.forkL : c.branchSide > 0 ? cs.forkR : 0f;
            if (c.branchSide != 0 && (cs.fork == null || bg < 0)) {
                // the other road has gone its way, and the car with it
                c.branchSide = 0;
                respawn(c, pz);
                continue;
            }
            // where lanes run out, a car merges into the nearest one left
            if (c.lane >= cs.lanes) c.lane = cs.lanes - 1;
            float want = c.branchSide != 0 ? c.branchSide * (2f + bg) + ((c.lane & 1) == 0 ? 0.5f : -0.5f) : laneX(cs.lanes, c.lane);
            c.offset += (want - c.offset) * Math.min(1f, dt * 1.5f);
            float v = c.speed;
            if (cs.water) v = Math.min(v, WATER_SPEED * 0.8f);
            double next = c.z + v * VMAX * dt;
            // cars wait at a red light, and never close up on the car ahead in their lane
            double line = holdLine(c.z);
            for (int k = 0; k < cars.size(); k++) {
                Car o = cars.get(k);
                if (o != c && Math.abs(o.offset - c.offset) < 0.3f && o.z > c.z && o.z - c.z < 8 * Track.SEG)
                    line = Math.min(line, o.z - Track.SEG * 1.7f);
            }
            if (next > line) next = Math.max(c.z, Math.min(next, line));
            // a car coming up behind the player's car waits behind it too
            double pzNow = position + PLAYER_Z;
            if (c.z < pzNow && pzNow - c.z < 3 * Track.SEG && Math.abs(c.offset - playerX) < 0.45f)
                next = Math.max(c.z, Math.min(next, pzNow - Track.SEG * 1.7f));
            c.v = (float) ((next - c.z) / Math.max(dt, 1e-4f) / VMAX);
            c.z = next;
        }
        for (int i = 0; i < cars.size(); i++) {
            Car a = cars.get(i);
            for (int j = 0; j < cars.size(); j++) {
                Car b = cars.get(j);
                if (a == b || Math.abs(a.offset - b.offset) > 0.3f) continue;
                if (b.z > a.z && b.z - a.z < 8 * Track.SEG && a.speed > b.speed) a.speed = b.speed;
            }
        }
        for (int i = 0; i < cars.size(); i++) {
            Car c = cars.get(i);
            double dz = c.z - pz;
            if (dz < -12 * Track.SEG) {
                respawn(c, pz);
                continue;
            }
            if (!c.passed && dz < -Track.SEG * 0.4f) {
                c.passed = true;
                if (!demo) {
                    overtakes++;
                    points += 50f * multiplier();
                    if (Math.abs(c.offset - playerX) < 0.62f && hitCooldown <= 0) {
                        flow = Math.min(1f, flow + 0.05f);
                        say("CLOSE PASS", 0.6f);
                        points += 150f * multiplier();
                        cues |= CUE_PASS;
                    }
                }
            }
            float reach = (vehicleW + carWidths[c.kind]) * 0.5f / (ROAD_W * width) * (boosting() ? 0.45f : 0.8f);
            if (hitCooldown <= 0 && altitude < 150f && dz > -Track.SEG * 0.6f && dz < Track.SEG * 0.7f
                    && Math.abs(c.offset - playerX) < reach) {
                if (c.kind == Garage.T_POLICE) startChase("YOU HIT A POLICE CAR");
                if (dz > Track.SEG * 0.25f && speed > c.v * VMAX + VMAX * 0.02f) {
                    harm(0.05f + 0.07f * Math.min(1f, (speed - c.v * VMAX) / (VMAX * 0.5f)));
                    speed = c.v * VMAX * 0.6f;
                    position = c.z - PLAYER_Z - Track.SEG * 0.9f;
                    hit(0.35f, 70, CUE_CRASH);
                } else {
                    playerX = c.offset + (playerX < c.offset ? -reach - 0.02f : reach + 0.02f);
                    speed *= 0.85f;
                    hit(0.25f, 40, CUE_BUMP);
                    harm(0.04f);
                }
            }
        }
    }

    /** On earth a rainbow puddle is the opposite of the sticky patches among the stars: fast, and slippery past things. */
    boolean boosting() {
        return rainbowTime > 0 && !inSpace;
    }

    /** Mostly cars; vans now and then; a bus is rare, and a patrol car rarer, never two at once. */
    private int pickKind() {
        float r = rnd.nextFloat();
        if (r < 0.015f && !neon && !policeAround()) return Garage.T_POLICE;
        if (r < 0.10f) return Garage.T_BUS;
        if (r < 0.25f) return Garage.T_VAN + rnd.nextInt(2);
        return rnd.nextInt(Garage.T_CARS);
    }

    private void respawn(Car c, double pz) {
        double z = pz + (Renderer.DRAW + 10 + rnd.nextInt(120)) * Track.SEG;
        int lanes = track.get((int) (z / Track.SEG)).lanes;
        int lane = rnd.nextInt(lanes);
        for (int attempt = 0; attempt < 4; attempt++) {
            boolean clear = true;
            for (int i = 0; i < cars.size(); i++) {
                Car o = cars.get(i);
                if (o != c && o.lane == lane && Math.abs(o.z - z) < 25 * Track.SEG) clear = false;
            }
            if (clear) break;
            lane = rnd.nextInt(lanes);
            z += 15 * Track.SEG;
        }
        // nobody drives your way on a one-way road you are taking the wrong way: park the car past its end
        while (track.get((int) (z / Track.SEG)).reverse) z += 60 * Track.SEG;
        c.z = z;
        c.lane = lane;
        c.offset = laneX(track.get((int) (z / Track.SEG)).lanes, Math.min(lane, track.get((int) (z / Track.SEG)).lanes - 1));
        c.speed = 0.30f + rnd.nextFloat() * 0.25f;
        c.v = c.speed;
        c.kind = pickKind();
        c.passed = false;
    }

    // ----------------------------------------------------------------- flow

    /**
     * Flow is the driver's trust. Holding a steady line on the asphalt raises it;
     * twitching the wheel, the grass and every hit lower it. A clean close pass
     * adds a little. The throttle reads it when it judges corners.
     */
    private void updateFlow(float dt, float rate, boolean off, float prevX) {
        if (state != RUN) return;
        float sp = speed / VMAX;
        float lateral = Math.abs(playerX - prevX) / Math.max(dt, 0.001f);
        if (off) flow -= dt * 0.3f;
        else if (sp > 0.25f) flow += dt * 0.045f * Math.max(0f, 1f - lateral * 1.2f);
        if (rate > 6f) flow -= dt * 0.05f * Math.min(4f, (rate - 6f) / 6f);
        if (flow < 0) flow = 0;
        if (flow > 1) flow = 1;
        if (flow > peakFlow) peakFlow = flow;
    }

    private void clockAndGates(float dt) {
        if (demo) return;
        if (chase) return; // the clock stops while the police are behind you
        topSpeed = Math.max(topSpeed, speed);
        if (state == RUN) {
            if (playerSegment() >= nextCheckpoint) {
                checkpoints++;
                nextCheckpoint += Track.CHECK_SEGS;
                say(mode == RALLY_TIME ? "SPLIT " + clockText(elapsed) : "CHECKPOINT", 1.4f);
                buzz = 25;
                cues |= CUE_CHIME;
            }
            elapsed += dt;
            if (mode == RALLY_TIME && playerSegment() >= finishSeg) {
                finished = true;
                state = DONE;
                say("FINISH", 3f);
                cues |= CUE_CHIME;
            } else if (mode == RALLY_DIST) {
                time -= dt;
                if (time <= 0) {
                    time = 0;
                    state = TIMEUP;
                    say("TIME UP", 3f);
                }
            }
        } else if (state == TIMEUP && speed < VMAX * 0.02f) {
            doneTimer += dt;
            if (doneTimer > 0.8f) {
                finished = true;
                state = DONE;
            }
        }
    }

    /** Engine pitch: climbs through each gear and drops on every upshift. */
    float engineHz() {
        if (speed < 1f) return 38f;
        float sp = speed / VMAX;
        float f = Math.max(0f, Math.min(1f, sp * 5f - (gear - 1)));
        float hz = 44f + 70f * f + 5f * gear;
        return shiftTimer > 0 ? hz * 0.85f : hz;
    }

    /** Minutes, seconds and tenths, as a stopwatch shows them. */
    static String clockText(float t) {
        int tenths = (int) (t * 10f);
        int m = tenths / 600, sec = (tenths / 10) % 60, d = tenths % 10;
        return m + ":" + (sec < 10 ? "0" : "") + sec + "." + d;
    }

    // ------------------------------------------------------------ oncoming

    /** Lane centers counted from the right edge of the player's carriageway. */
    static float laneX(int lanes, int lane) {
        return 1f - (2f * lane + 1f) / lanes;
    }

    /** Lane k of the oncoming carriageway, counted outward from the center line. */
    static float oncomingX(Track.Seg s, int lane) {
        return -1f - s.gap1 - (2f * lane + 1f) / s.lanes;
    }

    /**
     * Oncoming cars come toward the player in their own lanes, nose first.
     * Crossing the center line into them is a head-on crash; missing one
     * narrowly is a close call, worth points.
     */
    private void oncomingTraffic(float dt, float width) {
        double pz = position + PLAYER_Z;
        int want = demo ? 4 : 6;
        Track.Seg spawnAt = track.get((int) (pz / Track.SEG) + Renderer.DRAW + 12);
        int lanesThere = spawnAt.reverse ? spawnAt.lanes : spawnAt.onc;
        if (oncoming.size() < want + (spawnAt.reverse ? 3 : 0) && lanesThere > 0 && rnd.nextFloat() < dt * 1.5f) {
            Car c = new Car();
            c.dir = -1;
            c.lane = rnd.nextInt(lanesThere);
            c.z = (spawnAt.index + rnd.nextInt(30)) * (double) Track.SEG;
            c.offset = spawnAt.reverse ? laneX(spawnAt.lanes, c.lane) : oncomingX(spawnAt, c.lane);
            c.speed = c.v = 0.35f + rnd.nextFloat() * 0.2f;
            c.kind = pickKind();
            boolean clear = true;
            for (int i = 0; i < oncoming.size(); i++) {
                Car o = oncoming.get(i);
                if (o.lane == c.lane && Math.abs(o.z - c.z) < 20 * Track.SEG) clear = false;
            }
            if (clear) oncoming.add(c);
        }
        for (int i = oncoming.size() - 1; i >= 0; i--) {
            Car c = oncoming.get(i);
            if (c.washed) {
                c.offset += waveSide * dt * 3f;
                if (Math.abs(c.offset) > 7f) oncoming.remove(i);
                continue;
            }
            if (c.lift > 0) {
                c.lift += dt * 2400f;
                if (c.lift > 4200f) oncoming.remove(i);
                continue;
            }
            Track.Seg s = track.get((int) (c.z / Track.SEG));
            int lanesHere = s.reverse ? s.lanes : s.onc;
            if (lanesHere == 0 || c.z < position - 2 * Track.SEG) {
                oncoming.remove(i);
                continue;
            }
            if (c.lane >= lanesHere) c.lane = lanesHere - 1;
            float v = s.water ? Math.min(c.speed, WATER_SPEED * 0.8f) : c.speed;
            c.v = v;
            c.z -= v * VMAX * dt;
            float want2 = s.reverse ? laneX(s.lanes, c.lane) : oncomingX(s, c.lane);
            c.offset += (want2 - c.offset) * Math.min(1f, dt * 2f);
            if (demo) continue;
            double dz = c.z - pz;
            float reach = (vehicleW + carWidths[c.kind]) * 0.5f / (ROAD_W * width) * (boosting() ? 0.45f : 0.8f);
            if (hitCooldown <= 0 && altitude < 150f && Math.abs(dz) < Track.SEG * 0.7f && Math.abs(c.offset - playerX) < reach) {
                speed = Math.min(speed, VMAX * 0.05f);
                c.speed = 0f;
                hit(0.6f, 160, CUE_CRASH);
                harm(0.30f + 0.10f * rnd.nextFloat());
                say("HEAD ON!", 1.4f);
                if (c.kind == Garage.T_POLICE) startChase("YOU HIT A POLICE CAR");
                continue;
            }
            if (!c.passed && dz < -Track.SEG * 0.3f) {
                c.passed = true;
                if (Math.abs(c.offset - playerX) < reach + 0.45f && hitCooldown <= 0 && state == RUN) {
                    flow = Math.min(1f, flow + 0.04f);
                    points += 200f * multiplier();
                    say("CLOSE CALL", 0.7f);
                    cues |= CUE_PASS;
                }
            }
        }
    }

    // ------------------------------------------------------------ crossroads

    /**
     * Lights turn red as the car approaches most crossroads; while red, cross
     * traffic runs both ways. A car that has stopped at the line gets green
     * after a short wait. Hitting a crossing car starts a police chase.
     */
    private void crossroads(float dt, int pSeg, float width) {
        double front = position + PLAYER_Z;
        float frontSeg = (float) (front / Track.SEG);
        for (int i = 0; i < track.junctions.size(); i++) {
            Track.Junction j = track.junctions.get(i);
            if (j.done) continue;
            if (j.stopSeg - pSeg > 220) break;
            float dist = j.stopSeg - frontSeg;
            if (!j.decided && dist < 150) {
                j.decided = true;
                j.willRed = rnd.nextFloat() < 0.7f;
            }
            if (j.willRed && j.light == Track.GREEN && dist < 75 && dist > 0) {
                j.light = Track.YELLOW;
                j.timer = 1.4f;
            }
            if (j.light == Track.YELLOW) {
                j.timer -= dt;
                if (j.timer <= 0) {
                    j.light = Track.RED;
                    j.wait = 0;
                    j.spawn = 0.2f;
                }
            }
            if (j.light == Track.RED) {
                j.spawn -= dt;
                if (j.spawn <= 0) {
                    spawnCross(j);
                    j.spawn = 0.7f + rnd.nextFloat() * 1.2f;
                }
                if (dist > -0.3f && speed < VMAX * 0.03f) {
                    j.wait += dt;
                    if (j.wait > 2.8f) {
                        j.light = Track.GREEN;
                        j.willRed = false;
                    }
                }
            }
            for (int k = j.cross.size() - 1; k >= 0; k--) {
                Track.CrossCar c = j.cross.get(k);
                if (j.light == Track.GREEN && c.speed > 0) c.speed = Math.max(c.speed, 4.5f); // clear the junction
                c.x += c.dir * c.speed * dt;
                if (Math.abs(c.x) > 7f) {
                    j.cross.remove(k);
                    continue;
                }
                if (demo || chase || hitCooldown > 0 || state != RUN) continue;
                // the player's car against a car crossing in front of it
                if (Math.abs(front - c.z) < Track.SEG * 1.2) {
                    float me = playerX * width, half = vehicleW / ROAD_W * 0.5f + CROSS_HALF;
                    if (Math.abs(me - c.x) < half && altitude < 150f) {
                        speed *= 0.35f;
                        c.speed = 0f;
                        hit(0.4f, 140, CUE_CRASH);
                        harm(0.2f);
                        startChase("YOU RAN A RED LIGHT");
                    }
                }
            }
            if (pSeg > j.crossLast + 30) {
                j.done = true;
                j.cross.clear();
            }
        }
    }

    private void spawnCross(Track.Junction j) {
        int dir = rnd.nextBoolean() ? 1 : -1;
        float x0 = -dir * 6.5f;
        for (int i = 0; i < j.cross.size(); i++) {
            Track.CrossCar o = j.cross.get(i);
            if (o.dir == dir && Math.abs(o.x - x0) < 2.4f) return;
        }
        Track.CrossCar c = new Track.CrossCar();
        c.dir = dir;
        c.x = x0;
        c.speed = 2.4f + rnd.nextFloat() * 0.9f;
        c.kind = rnd.nextInt(CROSS_KINDS);
        c.z = j.laneZ(dir);
        j.cross.add(c);
    }

    /** Where a car going forward at z has to stop for a red light, or a large number if it does not. */
    private double holdLine(double z) {
        Track.Junction j = track.junctionAhead((int) (z / Track.SEG));
        if (j == null || j.light == Track.GREEN) return Double.MAX_VALUE;
        double line = j.stopSeg * (double) Track.SEG - Track.SEG * 0.6;
        return z <= line + 1 ? line : Double.MAX_VALUE;
    }

    // ----------------------------------------------------------------- turns

    // ------------------------------------------------------------------- pan

    private void startPan(float dir, int action, int arg) {
        pan = PAN;
        panDir = dir;
        panAction = action;
        panArg = arg;
        panDone = false;
    }

    /** The sweep: the sky turns a quarter round, and halfway through the road ahead is replaced. */
    private void panning(float dt) {
        if (pan <= 0) return;
        pan -= dt;
        float quarter = 0.25f * dt / PAN * panDir;
        bgFar += quarter;
        bgMid += quarter * 1.6f;
        bgCloud += quarter * 0.5f;
        if (!panDone && pan <= PAN / 2) {
            panDone = true;
            int p = playerSegment();
            switch (panAction) {
                case PAN_BRANCH:
                    track.veer(p + 3);
                    playerX = 0f;
                    say("NEW BRANCH", 1.2f);
                    break;
                default:
                    // into the station: the tank fills; in a timed rally the stop costs seconds
                    hangarOpen = true;
                    fuelBefore = fuel;
                    fuel = 1f;
                    lowWarned = false;
                    if (mode == RALLY_TIME) elapsed += PIT_STOP;
                    pan = 0f;
                    break;
            }
        }
    }

    /**
     * Another road has become the reference: the car, the traffic and the
     * pursuer move by shift so nothing moves in the world. Traffic that is now
     * off the road is on the road left behind, and is sent on ahead.
     */
    private void relabel(float shift) {
        playerX -= shift;
        policeX -= shift;
        double pz = position + PLAYER_Z;
        int side = shift < 0 ? -1 : 1;
        for (int i = 0; i < cars.size(); i++) {
            Car c = cars.get(i);
            c.offset -= shift;
            // those that took the same road as the player are on the reference now
            if (c.branchSide == side) c.branchSide = 0;
            else c.branchSide = 0;
            if (Math.abs(c.offset) > 1.2f) respawn(c, pz);
        }
        oncoming.clear();
    }

    /** Cars of the road left behind are gone; the new road fills with its own. */
    private void clearTraffic() {
        double pz = position + PLAYER_Z;
        for (int i = 0; i < cars.size(); i++) respawn(cars.get(i), pz);
        oncoming.clear();
    }

    // ------------------------------------------------------------------ spin

    /** Releasing the screen with the wheel turned throws the car into a spin. */
    private void startSpin(float dir) {
        spin = 2.0f;
        spinDir = dir;
        spinAngle = 0f;
        flow = Math.max(0f, flow - 0.35f);
        say("SPIN!", 1.2f);
        cues |= CUE_SKID;
        if (!demo) buzz = 90;
    }

    // ----------------------------------------------------------------- chase

    /**
     * The police appear behind and close in. The chase can run long, but it
     * ends one way only: they always catch you.
     */
    void startChase(String reason) {
        if (chase || demo || state != RUN || neon) return; // nobody to chase you in the black world
        if (rally()) {
            // in a rally the police do not chase: you are out
            dnf = true;
            state = DONE;
            chaseReason = reason;
            say("DNF", 3f);
            cues |= CUE_CRASH;
            return;
        }
        chase = true;
        chaseReason = reason;
        badgeTime = 2.2f;
        // half the time a violet hole opens somewhere ahead: a way out, not a sure one
        if (rnd.nextFloat() < 0.6f) nextHole = Math.min(nextHole, 10f + rnd.nextFloat() * 15f);
        chaseTime = 0f;
        chaseLength = 35f + rnd.nextFloat() * 40f;
        policeGap = CHASE_GAP;
        policeX = playerX;
    }

    private void chase(float dt) {
        if (!chase) return;
        if (busted) {
            bustTimer += dt;
            if (bustTimer > 2.2f) state = DONE;
            return;
        }
        if (flightT > 0) {
            // the police below are no match for wings
            policeGap += dt * 2.5f;
            return;
        }
        if (distant) {
            // far behind: they gain only if you stand still a long time, and then not always
            distantT += dt;
            if (speed < VMAX * 0.15f) {
                stoppedT += dt;
                if (stoppedT > 4f && !stopDecided) {
                    stopDecided = true;
                    closing = rnd.nextFloat() < 0.5f;
                    if (closing) say("SIRENS BEHIND", 1.4f);
                }
            } else {
                stoppedT = 0f;
                stopDecided = false;
            }
            if (closing) {
                policeGap -= dt * 1.5f;
                if (policeGap <= CHASE_GAP) {
                    distant = false;
                    closing = false;
                    say("THEY CAUGHT UP", 1.6f);
                }
            } else if (distantT > 45f) {
                chase = false;
                distant = false;
                say("LOST THEM", 2f);
            }
            return;
        }
        chaseTime += dt;
        float t = chaseTime / chaseLength;
        if ((alien || flood) && t >= 1f) {
            returnToEarth();
            return;
        }
        // the police always close in; the saucer hangs back and only gains on sticky ground
        float target = alien || flood ? 4f + 0.7f * (float) Math.sin(chaseTime * 0.8f)
                : Math.max(0f, CHASE_GAP * (1f - t)) + 0.6f * (float) Math.sin(chaseTime * 0.9f);
        policeGap += (target - policeGap) * dt * (flood ? 0.8f : 0.6f);
        policeX += (playerX - policeX) * Math.min(1f, dt * 1.5f);
        if (policeGap < 0.6f || (!alien && !flood && t > 1.08f)) {
            busted = true;
            bustTimer = 0f;
            policeGap = 0.6f;
            say(flood ? "WIPEOUT" : alien ? "ABDUCTED" : "BUSTED", 3f);
            cues |= CUE_CRASH;
            if (!demo) buzz = 200;
        }
    }

    // ---------------------------------------------------------------- wonders

    /**
     * Now and then a rainbow puddle lies in one lane: drive through it and the
     * world goes rainbow for a while. Rarer is a violet hole across the whole
     * road: nothing goes around it, and on the other side it is neon night,
     * until an ordinary black hole turns up and swallows the night again.
     */
    private void wonders(float dt, int pSeg, float width) {
        if (demo || state != RUN || rally()) return;
        if (rainbowTime > 0) rainbowTime -= dt;
        // at night nothing strange is laid on the road: it would be too hard to see
        boolean night = atmos.daylight() < 0.5f;
        nextPuddle -= dt;
        if (nextPuddle <= 0 && rainbowTime <= 0 && !night && flightT <= 0) {
            placePuddle(pSeg + 170);
            nextPuddle = 35f + rnd.nextFloat() * 50f;
        }
        nextHole -= dt;
        if (nextHole <= 0 && !night && flightT <= 0) {
            placeHole(pSeg + 190, 1);
            nextHole = neon ? 20f + rnd.nextFloat() * 40f : 70f + rnd.nextFloat() * 90f;
        }
        Track.Seg ps = track.get(pSeg);
        float half = vehicleW / (ROAD_W * width) * 0.5f;
        if (!hovers && ps.puddleX < 9f && rainbowTime <= 0 && Math.abs(playerX - ps.puddleX) < ps.puddleW + half) {
            rainbowTime = 10f;
            say("RAINBOW BOOST!", 1.4f);
            cues |= CUE_SPARKLE;
        }
        boolean entering = ps.hole != 0 && !inHole && altitude < 150f;
        inHole = ps.hole != 0;
        if (entering) warpFlash = 0.6f; // passing through a hole is always felt, whatever it changes
        if (entering) {
            // one kind of hole, and it flips the world: into the black world, or back out of it
            neon = !neon;
            cues |= CUE_WARP;
            if (neon) {
                // there are no police in the black world: a chase ends here, and patrol cars are gone
                noPolice();
                if (chase && !busted) {
                    chase = false;
                    say("NO POLICE HERE", 2f);
                } else {
                    say("BLACK WORLD", 1.6f);
                }
            } else {
                say("BACK AGAIN", 1.4f);
            }
        }
    }

    /** Nothing special may share a stretch of road with a wonder. */
    private boolean plain(int from, int count) {
        for (int i = from; i < from + count; i++) {
            Track.Seg s = track.get(i);
            if (s.water || s.bridge || s.cross || s.stopLine || s.zebra || s.checkpoint || s.hole != 0 || s.puddleX < 9f) return false;
        }
        return true;
    }

    private void placePuddle(int at) {
        for (int tries = 0; tries < 10 && !plain(at, 6); tries++) at += 12;
        if (!plain(at, 6)) return;
        int lanes = track.get(at).lanes;
        float lane = laneX(lanes, rnd.nextInt(lanes));
        for (int i = 0; i < 6; i++) {
            Track.Seg s = track.get(at + i);
            s.puddleX = lane;
            s.puddleW = 0.36f * (float) Math.sin(Math.PI * (i + 0.5) / 6);
        }
    }

    private void placeHole(int at, int kind) {
        for (int tries = 0; tries < 10 && !plain(at, 7); tries++) at += 12;
        if (!plain(at, 7)) return;
        for (int i = 0; i < 7; i++) {
            Track.Seg s = track.get(at + i);
            s.hole = kind;
            s.holeRing = Math.abs(i - 3);
        }
    }

    /** Which skin the world is seen through right now. */
    int skin() {
        if (inSpace) return flood ? Skin.DUSK : Skin.ARCADE;
        if (rainbowTime > 0) return Skin.RAINBOW; // a puddle colors even a chase, though it is no way out
        if (chase) return Skin.ARCADE;
        if (neon) return Skin.NEON;
        return Skin.DUSK;
    }

    // ------------------------------------------------------------------- ufo

    /**
     * Now and then a great saucer comes down over the road and sweeps it with a
     * beam, lifting cars out of their lanes. It drifts back over the player:
     * be anywhere but under the beam when it passes. Caught, the car is taken
     * up and the race goes on in a rabbit hole between the stars, the saucer
     * close behind. It hardly ever catches you there, unless the sticky
     * rainbow patches slow you down. When the hole ends you are back on earth.
     */
    private void ufo(float dt, float width) {
        if (demo || inSpace || state != RUN || rally()) return;
        if (!ufoActive) {
            if (chase || waveOn || flightT > 0) return;
            nextUfo -= dt;
            if (nextUfo <= 0) {
                ufoActive = true;
                ufoT = 0f;
                ufoRel = 42f;
                say("UFO!", 1.5f);
            }
            return;
        }
        ufoT += dt;
        Track.Seg s = track.get(playerSegment());
        float left = Track.leftOf(s);
        if (ufoT < 3f) {
            ufoAlt = 9000f - 5800f * ufoT / 3f;
            beamOn = false;
        } else if (ufoT < 17f) {
            ufoAlt = 3200f;
            beamOn = true;
            ufoRel = 42f - 49f * (ufoT - 3f) / 14f;
            // the beam sweeps from the far edge of the oncoming lanes to the right curb
            beamX = (left + 1f) / 2f + (1f - left) / 2f * 0.85f * (float) Math.sin(ufoT * 0.9f);
        } else if (ufoT < 20f) {
            beamOn = false;
            ufoAlt += 5000f * dt;
        } else {
            ufoActive = false;
            nextUfo = 90f + rnd.nextFloat() * 110f;
            return;
        }
        ufoZ = position + PLAYER_Z + ufoRel * Track.SEG;
        if (!beamOn) return;
        for (int pass = 0; pass < 2; pass++) {
            ArrayList<Car> list = pass == 0 ? cars : oncoming;
            for (int i = 0; i < list.size(); i++) {
                Car c = list.get(i);
                if (c.lift <= 0 && Math.abs(c.z - ufoZ) < Track.SEG * 1.6 && Math.abs(c.offset - beamX) < BEAM_HALF) c.lift = 1f;
            }
        }
        if (Math.abs(ufoRel) < 1.4f && Math.abs(playerX - beamX) < BEAM_HALF && hitCooldown <= 0) {
            abductTime = 1.6f;
            cues |= CUE_WARP;
            say("ABDUCTED!", 1.6f);
        }
    }

    /** The car is up in the saucer: the earth is put away and the rabbit hole begins. */
    private void enterDream(Track dream, boolean water) {
        if (dream == null) return;
        earthTrack = track;
        earthPos = position;
        earthX = playerX;
        earthCars.clear();
        earthCars.addAll(cars);
        cars.clear();
        oncoming.clear();
        track = dream;
        position = 0;
        playerX = 0;
        speed = VMAX * 0.5f;
        inSpace = true;
        ufoActive = false;
        alien = !water;
        flood = water;
        waveOn = false;
        chase = true;
        chaseReason = water ? "SWEPT INTO THE FLOOD" : "TAKEN BY A UFO";
        badgeTime = 2.2f;
        chaseTime = 0f;
        chaseLength = 30f + rnd.nextFloat() * 20f;
        policeGap = 4f;
        policeX = 0f;
        nextSticky = 3f;
        inHole = false;
    }

    /** Out of the rabbit hole and back where the car was taken. */
    private void returnToEarth() {
        shots.clear();
        neon = false;
        twist = 0f;
        track = earthTrack;
        position = earthPos + 4 * Track.SEG;
        playerX = Math.max(-0.9f, Math.min(0.9f, earthX));
        cars.clear();
        cars.addAll(earthCars);
        for (int i = 0; i < cars.size(); i++) cars.get(i).lift = 0f;
        inSpace = false;
        boolean wasFlood = flood;
        alien = false;
        flood = false;
        chase = false;
        warpFlash = 0.8f;
        if (wasFlood) {
            say("BACK ON THE ROAD", 2f);
            nextWave = 150f + rnd.nextFloat() * 150f;
            return;
        }
        nextUfo = 120f + rnd.nextFloat() * 120f;
        say("BACK ON EARTH", 2f);
    }

    /** A knock: how hard it was, as a share of a new car; heavy vehicles take less of it. */
    private void harm(float amount) {
        if (demo || inSpace || state != RUN) return;
        float before = health;
        health = Math.max(0f, health - amount * toughness);
        if (before >= 0.6f && health < 0.6f) say("SMOKE UNDER THE HOOD", 1.4f);
        if (before >= 0.3f && health < 0.3f) {
            pull = rnd.nextBoolean() ? 1f : -1f;
            say("ENGINE TROUBLE", 1.6f);
        }
        if (before > 0f && health <= 0f) say("ENGINE DEAD", 2f);
    }

    /** At the pumps: the same car, put right; in a timed rally it costs seconds. */
    void repair() {
        if (health >= 0.995f) return;
        health = 1f;
        if (mode == RALLY_TIME) elapsed += REPAIR_TIME;
    }

    /** The player has tapped for the station whose slip road begins at segment st. */
    void pit(int st) {
        pitIntent = true;
        pitStation = st;
    }

    /**
     * A pit stop asked for: nothing happens until 200 m before the slip road;
     * then the driver takes the wheel, moves to the right lane, slows, takes
     * the slip road and rolls up to the pumps. Pass the station and the
     * request lapses.
     */
    private void pitStop() {
        if (!pitIntent || demo) return;
        int p = playerSegment();
        if (p > pitStation + Track.EXIT_LEN + 5 || chase || inSpace) {
            pitIntent = false;
            pitAuto = false;
            return;
        }
        if (!pitAuto && pitStation - p < 140) {
            pitAuto = true;
            say("PULLING IN", 1.4f);
        }
        if (pitAuto) {
            touching = true;
            brake = 0f;
            Track.Seg s = track.get(p);
            if (onExit && s.exit && s.forkR >= 2.6f && pan <= 0 && !garageIntent) {
                garageIntent = true;
                startPan(1f, PAN_GARAGE, 0);
            }
        }
    }

    /** The driver's hands for a pit stop: keep right, then follow the slip road. */
    private float pitSteer() {
        Track.Seg s = track.get(playerSegment());
        // hug the right edge before the slip road, then ride its middle
        float target = s.exit && s.forkR >= 0 ? 1f + 1f / 5.5f + s.forkR + 0.35f : Math.max(laneX(s.lanes, 0), 0.85f);
        float sp = speed / VMAX;
        float lean = sp * track.get(playerSegment() + 3).curve * 0.3f;
        return Math.max(-1f, Math.min(1f, (target - playerX) * 2.5f + lean));
    }

    /** Out of the hangar in whatever was chosen, back on the road just past the slip road. */
    void leaveHangar() {
        hangarOpen = false;
        garageIntent = false;
        pitIntent = false;
        pitAuto = false;
        pitStation = -1;
        playerX = 0.66f;
        prevExit = -1f;
        speed = Math.min(speed, VMAX * 0.3f);
        say(mode == RALLY_TIME ? "PIT STOP +" + (int) PIT_STOP + ".0" : "FULL TANK", 1.4f);
    }

    /** Every patrol car on the road becomes an ordinary car. */
    private void noPolice() {
        for (int i = 0; i < cars.size(); i++) if (cars.get(i).kind == Garage.T_POLICE) cars.get(i).kind = rnd.nextInt(Garage.T_CARS);
        for (int i = 0; i < oncoming.size(); i++) if (oncoming.get(i).kind == Garage.T_POLICE) oncoming.get(i).kind = rnd.nextInt(Garage.T_CARS);
    }

    private boolean policeAround() {
        for (int i = 0; i < cars.size(); i++) if (cars.get(i).kind == Garage.T_POLICE) return true;
        for (int i = 0; i < oncoming.size(); i++) if (oncoming.get(i).kind == Garage.T_POLICE) return true;
        return false;
    }

    /** In the rabbit hole: rainbow patches stick to the walls, and the saucer gains. */
    private void sticky(float dt, int pSeg, float width) {
        if (flood) {
            floodRide(dt, pSeg, width);
            return;
        }
        // branches: steer into one while it is still close
        Track.Seg here = track.get(pSeg), ahead = track.get(pSeg + 2);
        int side = ahead.forkL >= 0 ? -1 : ahead.forkR >= 0 ? 1 : 0;
        float fg = side < 0 ? here.forkL : here.forkR;
        boolean able = side != 0 && fg >= 0.1f && fg < 3.5f && steerInput * side > 0.55f;
        branchHold = able ? branchHold + dt : 0f;
        if (branchHold > 0.15f && pan <= 0) {
            startPan(side, PAN_BRANCH, 0);
            branchHold = 0f;
        }
        // the saucer fires rainbow dashes past the car; they always miss
        shotTimer -= dt;
        if (shotTimer <= 0 && !busted) {
            float off = (rnd.nextBoolean() ? 1f : -1f) * (0.6f + rnd.nextFloat() * 0.6f);
            shots.add(new float[]{(float) ((position + PLAYER_Z) - policeGap * Track.SEG), playerX + off, speed + VMAX * 0.9f});
            shotTimer = 0.8f + rnd.nextFloat() * 0.9f;
        }
        double pzs = position + PLAYER_Z;
        for (int i = shots.size() - 1; i >= 0; i--) {
            float[] sh = shots.get(i);
            sh[0] += sh[2] * dt;
            if (sh[0] > pzs + 70 * Track.SEG) shots.remove(i);
        }
        if (stickyTime > 0) stickyTime -= dt;
        nextSticky -= dt;
        if (nextSticky <= 0) {
            int at = pSeg + 120;
            float wall = rnd.nextBoolean() ? 1f : -1f;
            for (int i = 0; i < 5; i++) {
                Track.Seg s = track.get(at + i);
                s.puddleX = wall * (0.9f + rnd.nextFloat() * 0.3f);
                s.puddleW = 0.34f * (float) Math.sin(Math.PI * (i + 0.5) / 5);
            }
            nextSticky = 3f + rnd.nextFloat() * 4f;
        }
        Track.Seg ps = track.get(pSeg);
        float half = vehicleW / (ROAD_W * width) * 0.5f;
        if (ps.puddleX < 9f && stickyTime <= 0 && Math.abs(playerX - ps.puddleX) < ps.puddleW + half) {
            stickyTime = 1.6f;
            policeGap -= 1.7f;
            say("STICKY!", 1f);
            cues |= CUE_BUMP;
        }
        if (stickyTime > 0) speed = Math.min(speed, VMAX * 0.35f);
    }

    // ------------------------------------------------------------------ wave

    /**
     * Where the sea is beside the road, now and then a wave comes over it. The
     * sea draws back, then a wall of water sweeps across a short stretch from
     * the sea side and takes whatever it covers. Get through before it, stop
     * short of it, or be on the far side when it is weak at the end. Caught,
     * the car is carried off, lands on a torn-down billboard, and rides it
     * down a drowned city with the wave behind.
     */
    private void wave(float dt, int pIdx) {
        if (!waveOn) {
            if (demo || rally() || inSpace || chase || ufoActive || flightT > 0 || state != RUN || floodTrack == null) return;
            nextWave -= dt;
            if (nextWave > 0) return;
            int at = pIdx + 75;
            Track.Seg s = track.get(at);
            int side = s.theme.id == Theme.COAST || s.sideR == Track.SIDE_BEACH ? 1 : s.sideL == Track.SIDE_BEACH ? -1 : 0;
            boolean clear = side != 0;
            for (int i = 0; i < WAVE_LEN && clear; i++) {
                Track.Seg w = track.get(at + i);
                clear = !w.cross && !w.bridge && !w.water && w.fork == null && w.forkR < 0 && !w.reverse;
            }
            if (!clear) {
                nextWave = 8f;
                return;
            }
            waveOn = true;
            waveSeg = at;
            waveSide = side;
            waveT = 0f;
            waveFront = side * 7f;
            say("WAVE!", 1.6f);
            cues |= CUE_SPLASH;
            return;
        }
        waveT += dt;
        float far = -waveSide * 5f, sea = waveSide * 7f;
        if (waveT < 1f) waveFront = sea;
        else if (waveT < 3.6f) waveFront = sea + (far - sea) * smooth((waveT - 1f) / 2.6f);
        else if (waveT < 5.6f) waveFront = far + (sea - far) * smooth((waveT - 3.6f) / 2f);
        else {
            waveOn = false;
            nextWave = 150f + rnd.nextFloat() * 150f;
            return;
        }
        if (waveT < 1f || waveT > 3.6f) return;
        for (int pass = 0; pass < 2; pass++) {
            ArrayList<Car> list = pass == 0 ? cars : oncoming;
            for (int i = 0; i < list.size(); i++) {
                Car c = list.get(i);
                int cs = (int) (c.z / Track.SEG);
                if (cs >= waveSeg && cs < waveSeg + WAVE_LEN && covered(c.offset)) c.washed = true;
            }
        }
        if (!demo && hitCooldown <= 0 && pIdx >= waveSeg && pIdx < waveSeg + WAVE_LEN && covered(playerX)) {
            washTime = 1.4f;
            say("WASHED AWAY!", 1.6f);
            cues |= CUE_SPLASH;
        }
    }

    /** Whether the water has reached lateral position x. */
    boolean covered(float x) {
        return waveOn && (waveSide > 0 ? x > waveFront : x < waveFront);
    }

    private static float smooth(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3 - 2 * t);
    }

    /** Down the drowned city on the billboard, the wave behind. Debris slows you, and the wave gains. */
    private void floodRide(float dt, int pSeg, float width) {
        Track.Seg s = track.get(pSeg);
        float half = vehicleW / (ROAD_W * width) * 0.5f;
        for (int i = 0; i < s.nProps && hitCooldown <= 0; i++) {
            Track.Prop p = s.props[i];
            if (!p.debris) continue;
            float w = p.worldW / (ROAD_W * width) * 0.5f;
            if (Math.abs(playerX - p.offset) < w + half) {
                // a knock, not a crash: way lost, and the wave a little nearer
                speed *= 0.6f;
                policeGap -= 1f;
                hitCooldown = 0.8f;
                hitFlash = 0.2f;
                cues |= CUE_BUMP;
                if (!demo) buzz = 40;
                say("DEBRIS!", 0.8f);
            }
        }
    }

    // ---------------------------------------------------------- road finds

    /**
     * Two things can lie on the road. A heart on the shoulder, never in a lane:
     * leave the road to take it, and it mends the car to half, or by a quarter
     * more if it is better than that, up to 125%. And, rarely, a pair of wings:
     * only in summer, by day, under a clear sky, out in the fields, the desert
     * or the jungle, and never with anything else strange about. Take them and
     * the car flies for a while, free of the road, then settles back on it.
     */
    private void finds(float dt, int pIdx, float width) {
        if (demo || inSpace || state != RUN) return;
        nextHeart -= dt;
        if (nextHeart <= 0) {
            nextHeart = 70f + rnd.nextFloat() * 70f;
            if (health < 1.25f) {
                int at = pIdx + 160;
                for (int tries = 0; tries < 8 && !plain(at, 2); tries++) at += 15;
                Track.Seg h = track.get(at);
                if (plain(at, 2)) h.heartX = (h.onc > 0 || rnd.nextFloat() < 0.7f ? 1f : -1f) * 1.14f;
            }
        }
        nextWings -= dt;
        if (nextWings <= 0) {
            nextWings = 20f; // the moment is wrong: look again shortly
            int at = pIdx + 170;
            Track.Seg w = track.get(at);
            int id = w.theme.id;
            boolean fair = atmos.season == Atmos.SUMMER && atmos.daylight() > 0.95f && atmos.strength < 0.1f;
            boolean wild = id == Theme.COUNTRY || id == Theme.DESERT || id == Theme.JUNGLE;
            boolean quiet = !rally() && !neon && rainbowTime <= 0 && !ufoActive && !waveOn && flightT <= 0;
            if (fair && wild && quiet && plain(at, 3) && !w.cross) {
                w.wingsX = laneX(w.lanes, rnd.nextInt(w.lanes));
                nextWings = 180f + rnd.nextFloat() * 180f;
            }
        }
        Track.Seg ps = track.get(pIdx);
        float half = vehicleW / (ROAD_W * width) * 0.5f;
        if (ps.heartX < 9f && altitude < 150f && Math.abs(playerX - ps.heartX) < half + 0.12f) {
            ps.heartX = 99f;
            health = health < 0.5f ? 0.5f : Math.min(1.25f, health + 0.25f);
            say("HEART!", 1.2f);
            cues |= CUE_CHIME;
        }
        if (ps.wingsX < 9f && flightT <= 0 && Math.abs(playerX - ps.wingsX) < half + 0.2f) {
            ps.wingsX = 99f;
            flightT = FLIGHT;
            say("WINGS!", 1.6f);
            cues |= CUE_SPARKLE;
        }
    }

    /** The flight: up in a second and a half, a while aloft, then a long glide down to the road. */
    private void flight(float dt) {
        if (flightT <= 0) return;
        flightT -= dt;
        float t = FLIGHT - flightT;
        float a = t < 1.5f ? smooth(t / 1.5f) : t < 7.5f ? 1f : Math.max(0f, 1f - (t - 7.5f) / (FLIGHT - 7.5f));
        altitude = ALTITUDE * a;
        if (flightT <= 0) {
            flightT = 0f;
            altitude = 0f;
            say("TOUCHDOWN", 1.2f);
            // the police are somewhere far behind now; only a long stop lets them catch up
            if (chase && !busted && policeGap > 12f) {
                distant = true;
                distantT = 0f;
                stoppedT = 0f;
                closing = false;
                stopDecided = false;
                policeGap = 40f;
            }
        }
    }

    // ------------------------------------------------------------ autopilot

    /** Steering for the attract-mode previews: hold the bend, dodge slow cars. */
    private float autopilot() {
        float sp = speed / VMAX;
        Track.Seg s = track.get(playerSegment() + 3);
        float need = sp * s.curve * s.hold * CENTRIFUGAL / sens;
        double pz = position + PLAYER_Z;
        for (int i = 0; i < cars.size(); i++) {
            Car c = cars.get(i);
            double dz = c.z - pz;
            if (dz > 0 && dz < 22 * Track.SEG && Math.abs(c.offset - demoLane) < 0.3f) {
                demoLane = freeLane(pz);
                break;
            }
        }
        // keep the chosen lane a real lane when the road narrows or widens
        int lanes = track.get(playerSegment()).lanes;
        float near = laneX(lanes, 0);
        for (int li = 1; li < lanes; li++) if (Math.abs(laneX(lanes, li) - demoLane) < Math.abs(near - demoLane)) near = laneX(lanes, li);
        demoLane = near;
        float v = need + (demoLane - playerX) * 2.2f;
        return Math.max(-1f, Math.min(1f, v));
    }

    private float freeLane(double pz) {
        float best = demoLane;
        float bestDist = 99f;
        int lanes = track.get(playerSegment()).lanes;
        for (int li = 0; li < lanes; li++) {
            float lane = laneX(lanes, li);
            boolean blocked = false;
            for (int i = 0; i < cars.size(); i++) {
                Car c = cars.get(i);
                double dz = c.z - pz;
                if (dz > -2 * Track.SEG && dz < 26 * Track.SEG && Math.abs(c.offset - lane) < 0.35f) blocked = true;
            }
            float d = Math.abs(lane - playerX);
            if (!blocked && d < bestDist) {
                best = lane;
                bestDist = d;
            }
        }
        return best;
    }
}
