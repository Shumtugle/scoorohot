package com.shumtugle.scoorohot;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

/**
 * An endless road, generated section by section as the car approaches.
 * A section is a straight, a bend, an S, a crest or a roller; its size and
 * sharpness grow with the distance already driven, but the order stays random,
 * so a hard bend can come early and an easy stretch can come late.
 * The scenery runs in zones: coast and town take turns, each zone of random length.
 *
 * Zones come in four kinds: coast, town, farmland and jungle. Rivers cross
 * them under bridges with rails on both sides; jungle tracks ford shallow
 * streams; town and farm roads pass under overpasses; farm roads are lined
 * with power poles whose wires the renderer strings from pole to pole.
 *
 * Roads fork. A fork is a slow Y: the two roads part over 160 segments, run
 * side by side for 70, then one leaves for good. A sign over the road says
 * where each goes, and the choice is real: it decides the next country. The
 * choice is read while the roads are parallel, so taking the other one only
 * relabels which road is the reference; nothing on screen moves. The oncoming
 * carriageway swings away on the same profile and can be taken the same way:
 * then it is a one-way road with all traffic coming at you, until a proper
 * carriageway joins it from the right and it becomes the oncoming side again.
 *
 * Every couple of kilometres, in every country, a slip road on the right leads
 * to a gas station: the place to fill the tank and change cars.
 *
 * Town roads have crossroads: a stop line, a zebra, a cross street and a
 * traffic light on each side. The race decides when a light turns red and
 * sends cross traffic through; the track only lays the road out.
 *
 * Roads carry traffic both ways: three lanes each way in town, two on farm
 * roads and highways, one wide lane on dirt tracks. The player's carriageway
 * is the reference: positions are in half-widths of it, and the oncoming
 * carriageway lies to its left. Now and then the oncoming road swings away on
 * its own line and comes back later; some stretches are one-way.
 *
 * Bends may be banked: the outer edge rises, the road holds the car better and
 * the throttle dares more. Most bends are banked a little, some are flat, and a
 * few late ones lean the wrong way. The road also breathes in width from section
 * to section.
 */
final class Track {
    static final int SEG = 200;          // world length of one road segment
    static final int RUMBLE = 3;         // segments per color band
    static final int CHECK_SEGS = 1800;  // distance between checkpoint gates
    static final int FADE = 140;         // segments over which the sky blends into the next zone
    static final float BANK_MAX = Race.ROAD_W * 0.32f; // edge rise at the sharpest, fully banked bend
    static final float BANK_GRIP = 0.3f; // share of the outward push a full bank cancels

    // world widths of roadside props
    private static final float W_BUSH = 900, W_ROCK = 700, W_TREE = 1500, W_CYPRESS = 650, W_LAMP = 330,
            W_BUILDING = 3300, W_CHEVRON = 620, W_BILLBOARD = 1700, W_GATE = Race.ROAD_W * 2.5f,
            W_MILL = 2600, W_BARN = 2600, W_HAY = 520, W_POLE = 420, W_PALM = 1500, W_FERN = 1000,
            W_OVERPASS = Race.ROAD_W * 3.4f, W_RAIL = 300, W_MARKER = 130, W_LIGHT = 420,
            W_SAGUARO = 1100, W_BARREL = 380, W_REDROCK = 1200, W_ROUTE = 520, W_FIR = 1200, W_DRIFT = 900,
            W_BEAR = 1100, W_ACACIA = 2600, W_GIRAFFE = 1300, W_TERMITES = 420, W_UMBRELLA = 700,
            W_RING = Race.ROAD_W * 3.2f, W_STARS = 1500, W_STATION = 4600, W_GAS = 700, W_GAS_BIG = 1000;
    private static final int NORMAL = 0, BRIDGE = 1, FORD = 2, TWIST = 3, FORK = 4;
    static final int EXIT_LEN = 90;      // segments of a slip road off to a gas station: long and gentle
    static final int CROSS_LEN = 8;      // segments of cross street after the zebra
    static final int GREEN = 0, YELLOW = 1, RED = 2;
    static final float GAP_AWAY = 14f;   // gap at which a parting road is gone
    static final float GAP_PAR = 5f;     // gap while two roads run side by side
    static final int DIV = 160, PAR = 70, LEAVE = 120, SPLIT_LEN = DIV + PAR + LEAVE;
    private static final int JOINED = 0, SPLITTING = 1, GONE = 2, JOINING = 3;
    static final String[] NAMES = {"COAST", "CITY", "FARMLAND", "JUNGLE", "DESERT", "TAIGA", "SAVANNA", "SPACE"};
    static final int SIDE_PLAIN = 0, SIDE_PARK = 1, SIDE_BEACH = 2;

    /** A car on the cross street, seen from the side. x is in road half-widths, dir is +1 left to right. */
    static final class CrossCar {
        float x, speed;
        int dir, kind;
        double z;
    }

    /** A crossroads: where it is, what its lights show, and the traffic crossing it. */
    static final class Junction {
        final int stopSeg, crossFirst, crossLast;
        int light = GREEN;
        boolean decided, willRed, done, turned;
        float timer, wait, spawn;
        final ArrayList<CrossCar> cross = new ArrayList<CrossCar>();

        Junction(int stopSeg) {
            this.stopSeg = stopSeg;
            crossFirst = stopSeg + 2;
            crossLast = stopSeg + 1 + CROSS_LEN;
        }

        double laneZ(int dir) {
            return (dir > 0 ? crossFirst + 2 : crossFirst + 6) * (double) SEG;
        }
    }

    final ArrayList<Junction> junctions = new ArrayList<Junction>();

    static final class Prop {
        final Bitmap bmp;
        float offset;        // in road half-widths from the center of the player's carriageway
        final float worldW;
        final float anchor;  // horizontal anchor as a fraction of the drawn width
        final boolean solid;
        final boolean spans;  // stretches with the road width, like a gate over the road
        boolean debris;       // floats on the water in the channel: hit it and you lose way
        float sink;           // how much of it lies below the road, as a share of its height
        Bitmap[] frames;      // animation, if any
        float animRate;       // frames per second
        boolean wire;         // a power pole: wires run from it to the next pole
        Junction junction;    // a traffic light: its frame follows this junction
        int glow;             // lit at night: 1 a street lamp, 2 a traffic light

        Prop(Bitmap bmp, float offset, float worldW, boolean solid) {
            this(bmp, offset, worldW, offset < 0 ? -1f : 0f, solid, false);
        }

        Prop(Bitmap bmp, float offset, float worldW, float anchor, boolean solid, boolean spans) {
            this.bmp = bmp;
            this.offset = offset;
            this.worldW = worldW;
            this.anchor = anchor;
            this.solid = solid;
            this.spans = spans;
        }
    }

    static Prop animated(Bitmap[] frames, float offset, float worldW, float rate) {
        Prop p = new Prop(frames[0], offset, worldW, true);
        p.frames = frames;
        p.animRate = rate;
        return p;
    }

    /** A fork in the road: where it starts, which side the other road leaves by, and where each goes. */
    static final class Fork {
        int start, side;
        Theme straight, other;
        boolean decided;
    }

    static final class Seg {
        int index;
        float curve, y1, y2;
        float bank1, bank2;  // edge rise in world units; positive lifts the left edge
        float width1, width2; // road width as a multiple of the standard width
        float hold = 1f;     // multiplier of the outward push in this segment
        int lanes = 3;       // lanes of the player's carriageway
        int onc;             // lanes of the oncoming carriageway, 0 on a one-way stretch
        float gap1, gap2;    // grass between the carriageways, in half-widths, at both ends
        int sideL, sideR;    // what lies beside the road: plain, park or beach
        boolean bridge;      // over a river, rails on both sides
        boolean water;       // a ford: the road runs under shallow water
        boolean shade;       // under an overpass
        boolean cross, stopLine, zebra, crossMid; // parts of a crossroads
        float twist;         // in the rabbit hole: how far the whole world has turned about the car, degrees
        float forkL = -1f, forkR = -1f; // a branch peeling off to that side: its gap in half-widths; negative is none
        boolean exit;        // a slip road to a hangar runs off the right here
        boolean reverse;     // a one-way road taken the wrong way: all traffic comes at you
        boolean branch;      // forkL or forkR here is a whole road, not a slip road
        boolean oncPar;      // the oncoming carriageway runs parallel here, on its way out: it can be taken
        boolean mergeEnd;    // a wrong-way road has been joined from the right: from here it is the oncoming side
        Fork fork;           // the fork this segment belongs to

        float puddleX = 99f; // a rainbow puddle's center in half-widths; 99 means none
        float heartX = 99f;  // a heart on the shoulder; 99 means none
        float wingsX = 99f;  // a pair of wings in a lane; 99 means none
        float puddleW;       // its half-width here
        int hole;            // 1: a violet hole, 2: an ordinary black one; holeRing counts from its middle
        int holeRing;
        Theme theme, prev;   // prev is the zone left behind, null in the first zone
        int zoneStart;
        boolean checkpoint;
        Prop[] props;
        int nProps;

        void add(Prop p) {
            if (props == null) props = new Prop[3];
            else if (nProps == props.length) props = Arrays.copyOf(props, nProps * 2);
            props[nProps++] = p;
        }
    }

    private final Theme[] themes;
    private final Sprites sp;
    private final Random rnd;
    private final ArrayList<Seg> segs = new ArrayList<Seg>(8192);
    private float lastY, lastBank, lastWidth = 1f, lastGap = GAP_AWAY, secVar = 1f;
    private int lastOnc, secOnc, secSideL, secSideR;
    private int sections;
    private Theme zone, zonePrev;
    private int zoneStart, zoneEnd;
    private int mode = NORMAL, secFirst, secTotal;
    private final boolean space;   // the rabbit hole: rings of stars, no towns, no rivers
    private float wireSide = 1f;
    private float lastTwist, twistStep;
    private int forkSide, exitSegs;
    private int oncState = JOINED, oncStep, reverseLeft, mergeStep = -1;
    private boolean reverseMode, merged;
    private int nextFork = 2200, forkStep = -1;
    private Fork forkNow;
    private Theme pendingZone;
    final ArrayList<Fork> forks = new ArrayList<Fork>();
    private int nextStation = 1500;
    static final int[] GAS_SIGNS = {2000, 667, 200, 30};   // segments before a station: 3 km, 1 km, 300 m, the turn
    final ArrayList<Integer> stations = new ArrayList<Integer>(); // where each slip road to a station begins

    Track(Theme[] themes, Sprites sp, long seed, boolean startGate) {
        this.themes = themes;
        this.sp = sp;
        this.space = themes.length == 1 && (themes[0].id == Theme.SPACE || themes[0].id == Theme.FLOOD);
        this.rnd = new Random(seed);
        zone = themes[rnd.nextInt(themes.length)];
        zoneEnd = zoneLength();
        // the start: the zone's own width, oncoming traffic alongside from the first metre
        lastWidth = zone.lanes * zone.laneScale / 3f;
        secOnc = lastOnc = zone.oncoming;
        lastGap = 0f;
        addRoad(0, 70, 0, 0, 0, 0f, 1f);
        if (startGate) segs.get(9).add(new Prop(sp.gateStart, 0, W_GATE, -0.5f, false, true));
    }

    private int zoneLength() {
        return 900 + rnd.nextInt(900);
    }

    /** The gap at step i of a road parting: a slow ease out, parallel, an ease away; negative once gone. */
    static float split(int i) {
        if (i < 0) return 0f;
        if (i < DIV) return GAP_PAR * smooth(i / (float) DIV);
        if (i < DIV + PAR) return GAP_PAR;
        if (i < SPLIT_LEN) return GAP_PAR + (GAP_AWAY - GAP_PAR) * smooth((i - DIV - PAR) / (float) LEAVE);
        return -1f;
    }

    private static float smooth(float t) {
        return t * t * (3 - 2 * t);
    }

    /** The outer left edge of the paving, in half-widths of the player's carriageway. */
    static float leftOf(Seg s) {
        return s.onc > 0 ? -1f - s.gap1 - 2f * s.onc / s.lanes : -1f;
    }

    /** Where a prop really stands: things on the left move out beyond the oncoming road. */
    static float propX(Seg s, Prop p) {
        return p.spans || p.offset >= 0 ? p.offset : p.offset + leftOf(s) + 1f;
    }

    Seg get(int i) {
        if (i < 0) i = 0;
        while (segs.size() <= i) generate();
        return segs.get(i);
    }

    // ------------------------------------------------------------ generation

    private void generate() {
        float d = Math.min(1f, sections / 24f);
        sections++;
        int ease = 10 + rnd.nextInt(26);
        int hold = 20 + rnd.nextInt(50 + (int) (50 * d));
        float sharp = 2f + 4f * d;
        float curve = (1f + rnd.nextFloat() * (sharp - 1f)) * (rnd.nextBoolean() ? 1 : -1);
        float camber = camber(d);
        float width = width();
        // most stretches carry oncoming traffic; switching on or off makes the other road swing in or away
        secOnc = rnd.nextFloat() < 0.78f ? zone.oncoming : 0;
        if (zone.id == Theme.CITY) {
            float r = rnd.nextFloat();
            int a = r < 0.5f ? SIDE_PLAIN : r < 0.8f ? SIDE_PARK : SIDE_BEACH;
            boolean left = rnd.nextBoolean();
            secSideL = left ? a : SIDE_PLAIN;
            secSideR = left ? SIDE_PLAIN : a;
        } else {
            secSideL = secSideR = SIDE_PLAIN;
        }
        if (space) {
            secOnc = 0;
            if (zone.id == Theme.FLOOD) floodSection();
            else spaceSection(d);
            return;
        }
        if (!space && segs.size() > nextFork - 500) secOnc = 0; // the oncoming road leaves before a fork
        if (reverseMode || forkStep >= 0 || segs.size() > nextFork - 500) {
            // a wrong-way road or a fork: plain bends, nothing that needs crossing traffic
            addRoad(ease, hold, ease, rnd.nextFloat() < 0.6f ? curve * 0.5f : 0f, rnd.nextFloat() < 0.4f ? hill(d) : 0, camber, width);
            return;
        }
        // special stretches first: a river to bridge, a stream to ford
        // nothing special near a planned station, so its slip road starts exactly where the signs say
        boolean nearStation = segs.size() > nextStation - 400 && segs.size() < nextStation + EXIT_LEN + 20;
        if (!space && !nearStation && sections > 2 && rnd.nextFloat() < 0.09f) {
            mode = BRIDGE;
            addRoad(10, 40 + rnd.nextInt(50), 10, rnd.nextFloat() < 0.4f ? curve * 0.4f : 0f, 0, 0f, Math.max(1f, lastWidth));
            mode = NORMAL;
            return;
        }
        if (!nearStation && sections > 2 && zone.id == Theme.JUNGLE && rnd.nextFloat() < 0.22f) {
            mode = FORD;
            addRoad(12, 18 + rnd.nextInt(30), 12, 0, 0, 0f, lastWidth);
            mode = NORMAL;
            return;
        }
        // a town block: a straight with a crossroads in the middle
        if (!nearStation && sections > 1 && zone.id == Theme.CITY && rnd.nextFloat() < 0.33f) {
            int len = 40 + rnd.nextInt(40);
            int at = segs.size() + ease + len / 2;
            addRoad(ease, len, ease, 0, 0, 0f, width);
            junction(at - 4);
            return;
        }
        float roll = rnd.nextFloat();
        if (roll < 0.20f) {
            int at = segs.size() + ease + (hold + 30) / 2;
            addRoad(ease, hold + 30, ease, 0, rnd.nextFloat() < 0.5f ? hill(d) : 0, 0f, width);
            if (zone.id == Theme.CITY && hold + 30 >= 40 && rnd.nextFloat() < 0.65f) junction(at - 4);
            else if ((zone.id == Theme.CITY || zone.id == Theme.COUNTRY) && rnd.nextFloat() < 0.4f) overpass(at);
        } else if (roll < 0.50f) {
            addRoad(ease, hold, ease, curve, rnd.nextFloat() < 0.4f ? hill(d) : 0, camber, width);
        } else if (roll < 0.68f) {
            addRoad(ease, hold / 2, ease, curve, 0, camber, width);
            addRoad(ease, hold / 2, ease, -curve * (0.6f + 0.4f * rnd.nextFloat()), hill(d) * 0.5f, camber, width);
        } else if (roll < 0.84f) {
            int n = 2 + rnd.nextInt(3);
            for (int i = 0; i < n; i++) addRoad(10, 12 + rnd.nextInt(10), 10, 0, hill(d) * 0.6f, 0f, width);
        } else {
            addRoad(ease, hold, ease, curve, hill(d), camber, width);
        }
    }

    /** Lays out a crossroads with its stop line at segment stop: zebra, cross street, two lights. */
    private void junction(int stop) {
        Junction j = new Junction(stop);
        junctions.add(j);
        for (int i = stop - 1; i <= j.crossLast + 1; i++) {
            Seg s = segs.get(i);
            s.nProps = 0; // nothing may stand on a street
        }
        segs.get(stop).stopLine = true;
        segs.get(stop + 1).zebra = true;
        for (int i = j.crossFirst; i <= j.crossLast; i++) segs.get(i).cross = true;
        segs.get(j.crossFirst + 4).crossMid = true;
        for (int side = -1; side <= 1; side += 2) {
            Prop light = new Prop(sp.trafficLight[0], side * 1.25f, W_LIGHT, true);
            light.frames = sp.trafficLight;
            light.junction = j;
            light.glow = 2;
            segs.get(stop - 1).add(light);
        }
    }

    /** Where the next station's slip road begins: one already laid, or the one planned. */
    int nextStationSeg(int seg) {
        int st = stationAhead(seg);
        return st >= 0 ? st : nextStation;
    }

    /** The first station slip road at or after a segment, or -1. */
    int stationAhead(int seg) {
        for (int i = 0; i < stations.size(); i++) if (stations.get(i) + EXIT_LEN >= seg) return stations.get(i);
        return -1;
    }

    /** Puts a finish gate over segment at, clearing anything special there. */
    void finish(int at) {
        Seg s = get(at);
        s.add(new Prop(sp.gateFinish, 0, W_GATE, -0.5f, false, true));
        s.checkpoint = true;
    }

    /** The first crossroads whose far side the given segment has not yet passed. */
    Junction junctionAhead(int seg) {
        for (int i = 0; i < junctions.size(); i++) {
            Junction j = junctions.get(i);
            if (j.crossLast + 2 >= seg) return j;
        }
        return null;
    }

    /**
     * The rabbit hole: no lanes, only a tube of star rings, and it never runs
     * straight for long. Serpentines of tight alternating bends, vortices
     * where the whole world turns round the car, swoops up and down, and
     * branches peeling off to either side that can be taken.
     */
    private void spaceSection(float d) {
        float r = rnd.nextFloat();
        float dir = rnd.nextBoolean() ? 1f : -1f;
        if (r < 0.35f) {
            int n = 3 + rnd.nextInt(3);
            for (int i = 0; i < n; i++) {
                addRoad(6, 10 + rnd.nextInt(10), 6, dir * (5f + rnd.nextFloat() * 3f), (rnd.nextFloat() - 0.5f) * 40f, 1f, 1f);
                dir = -dir;
            }
        } else if (r < 0.55f) {
            mode = TWIST;
            twistStep = dir * 360f / 70f;
            addRoad(10, 50, 10, dir * (1f + rnd.nextFloat() * 2f), (rnd.nextFloat() - 0.5f) * 30f, 0.5f, 1f);
            mode = NORMAL;
        } else if (r < 0.80f) {
            mode = FORK;
            forkSide = rnd.nextBoolean() ? 1 : -1;
            addRoad(8, 44, 8, dir * rnd.nextFloat() * 1.5f, (rnd.nextFloat() - 0.5f) * 20f, 0.5f, 1f);
            mode = NORMAL;
        } else {
            addRoad(14, 30, 14, dir * (3f + rnd.nextFloat() * 3f), (rnd.nextFloat() - 0.5f) * 70f, 1f, 1f);
        }
    }

    /**
     * Everything from segment from on is cut away, and the generator picks up
     * again from the last segment kept. The race only does this behind a turn
     * of the camera, so the new road is never seen appearing.
     */
    private void restart(int from) {
        while (segs.size() > from) segs.remove(segs.size() - 1);
        for (int i = junctions.size() - 1; i >= 0; i--) if (junctions.get(i).stopSeg >= from) junctions.remove(i);
        Seg last = segs.get(from - 1);
        lastY = last.y2;
        lastBank = 0f;
        lastWidth = last.width2;
        lastGap = 0f;
        lastTwist = last.twist;
        mode = NORMAL;
        exitSegs = 0;
    }

    /**
     * The flood: one long swell after another, the channel rolling and
     * heeling hard to both sides, curving between the towers.
     */
    private void floodSection() {
        float dir = rnd.nextBoolean() ? 1f : -1f;
        addRoad(8, 14 + rnd.nextInt(16), 8, dir * (1.5f + rnd.nextFloat() * 3.5f), (rnd.nextBoolean() ? 1 : -1) * (40f + rnd.nextFloat() * 50f), 1f, 1f);
    }

    /** Into a branch of the rabbit hole. */
    void veer(int from) {
        restart(from);
        lastOnc = secOnc = 0;
    }

    /** One segment of a fork: the other road's gap, the sign at its start, and the end of it. */
    private void forkSeg(Seg s) {
        if (forkStep < 0 && !space && s.index >= nextFork && oncState == GONE && exitSegs == 0 && mode == NORMAL
                && Math.abs(s.index - nextStation) > 700) {
            forkNow = new Fork();
            forkNow.start = s.index;
            forkNow.side = rnd.nextBoolean() ? 1 : -1;
            forkNow.straight = pickOther(zone);
            forkNow.other = pickOther(forkNow.straight);
            forks.add(forkNow);
            forkStep = 0;
            String a = "< " + NAMES[(forkNow.side < 0 ? forkNow.other : forkNow.straight).id];
            String b = NAMES[(forkNow.side < 0 ? forkNow.straight : forkNow.other).id] + " >";
            s.add(new Prop(sp.sign(a, b), 0, W_GATE, -0.5f, false, true));
        }
        if (forkStep < 0) return;
        float g = split(forkStep);
        if (g < 0) {
            forkStep = -1;
            nextFork = s.index + 2800 + rnd.nextInt(2400);
            return;
        }
        s.branch = true;
        s.fork = forkNow;
        if (forkNow.side < 0) s.forkL = g;
        else s.forkR = g;
        forkStep++;
    }

    private Theme pickOther(Theme t) {
        Theme n = themes[rnd.nextInt(themes.length)];
        for (int k = 0; k < 8 && (n == t || n.id == Theme.SPACE); k++) n = themes[rnd.nextInt(themes.length)];
        return n;
    }

    /** One segment of a wrong-way road: its own way for a while, then a carriageway joining from the right. */
    private void wrongWaySeg(Seg s) {
        s.reverse = !merged;
        if (s.onc > 0) {
            // the old road, now on the right, carrying on along its way out
            s.forkR = s.gap1;
            s.branch = true;
            s.onc = 0;
        }
        if (merged) {
            // joined: a proper road on the right, waiting for the car to reach the join
            s.forkR = 0f;
            s.branch = true;
            return;
        }
        if (oncState != GONE) return;
        if (reverseLeft > 0) {
            reverseLeft--;
            return;
        }
        if (mergeStep < 0) mergeStep = 0;
        s.forkR = split(SPLIT_LEN - 1 - mergeStep);
        s.branch = true;
        if (++mergeStep >= SPLIT_LEN - 1) {
            s.mergeEnd = true;
            merged = true;
        }
    }

    /** The fork's decision when the player stays on the road ahead: the next country is the straight one. */
    void choose(Fork f, boolean other) {
        f.decided = true;
        pendingZone = other ? f.other : f.straight;
    }

    /**
     * The player has taken the other road of a fork: it becomes the reference
     * from segment from on, and the road left becomes the other road on the
     * far side. Positions move by the returned shift; the road beyond the fork
     * keeps its scenery, which is now the chosen road's.
     */
    float takeBranch(int from) {
        Seg here = get(from);
        Fork f = here.fork;
        int side = f.side;
        float shift = side * (2f + (side < 0 ? here.forkL : here.forkR));
        for (int i = from; i < segs.size(); i++) {
            Seg s = segs.get(i);
            if (s.fork != f) break;
            float g = side < 0 ? s.forkL : s.forkR;
            float sh = side * (2f + g);
            if (side < 0) {
                s.forkR = g;
                s.forkL = -1f;
            } else {
                s.forkL = g;
                s.forkR = -1f;
            }
            for (int k = 0; k < s.nProps; k++) if (!s.props[k].spans) s.props[k].offset -= sh;
        }
        choose(f, true);
        return shift;
    }

    /**
     * The player has stayed on the oncoming carriageway as it parts: it becomes
     * the reference, a one-way road the wrong way, and the old road becomes
     * the other road on the right.
     */
    float takeOncoming(int from) {
        Seg here = get(from);
        float shift = -(1f + here.gap1 + 2f * here.onc / here.lanes - 1f) - 1f;
        reverseMode = true;
        merged = false;
        mergeStep = -1;
        reverseLeft = 300 + rnd.nextInt(300);
        for (int i = from; i < segs.size(); i++) {
            Seg s = segs.get(i);
            float sh = s.onc > 0 ? -(1f + s.gap1 + 1f) : 0f;
            wrongWaySeg(s);
            if (sh != 0) for (int k = 0; k < s.nProps; k++) if (!s.props[k].spans && s.props[k].offset > 0) s.props[k].offset -= sh;
        }
        return shift;
    }

    /** The wrong-way road is joined: from here the joining road is the reference and the car is on its oncoming side. */
    float endReverse(int from) {
        for (int i = from; i < segs.size(); i++) {
            Seg s = segs.get(i);
            s.reverse = false;
            s.onc = s.lanes;
            s.gap1 = s.gap2 = 0f;
            s.forkR = -1f;
            s.branch = false;
        }
        reverseMode = false;
        merged = false;
        oncState = JOINED;
        lastOnc = zone.oncoming;
        return 2f;
    }

    /** An overpass over segment at, with its shadow on the road under it. */    /** An overpass over segment at, with its shadow on the road under it. */
    private void overpass(int at) {
        Seg s = segs.get(at);
        s.add(new Prop(sp.overpass, 0, W_OVERPASS, -0.5f, false, true));
        for (int i = at - 2; i <= at + 1; i++) segs.get(i).shade = true;
    }

    /** How much a section's bends are banked: mostly some, sometimes none, late on now and then the wrong way. */
    private float camber(float d) {
        float r = rnd.nextFloat();
        if (r < 0.62f) return 0.4f + 0.6f * rnd.nextFloat();
        if (r < 0.86f || d < 0.25f) return 0f;
        return -0.15f - 0.25f * rnd.nextFloat();
    }

    /** How a section varies the zone's standard lane width: as is, a little tight, or roomy. */
    private float width() {
        float r = rnd.nextFloat();
        if (r < 0.5f) return 1f;
        if (r < 0.75f) return 0.92f + 0.05f * rnd.nextFloat();
        return 1.08f + 0.12f * rnd.nextFloat();
    }

    /** Height change of a section, in segment lengths; drifts back toward sea level. */
    private float hill(float d) {
        float h = 6f + rnd.nextFloat() * 22f * (0.5f + d);
        int sign;
        if (lastY > 9000) sign = -1;
        else if (lastY < -9000) sign = 1;
        else sign = rnd.nextBoolean() ? 1 : -1;
        return h * sign;
    }

    private void addRoad(int enter, int hold, int leave, float curve, float height, float camber, float width) {
        float startY = lastY;
        float endY = startY + height * SEG;
        float startW = lastWidth;
        secVar = width;
        int total = enter + hold + leave;
        int widen = Math.max(1, Math.min(40, total));
        int first = segs.size();
        secFirst = first;
        secTotal = total;
        for (int i = 0; i < total; i++) {
            float c;
            if (i < enter) c = easeIn(0, curve, i / (float) enter);
            else if (i < enter + hold) c = curve;
            else c = easeInOut(curve, 0, (i - enter - hold) / (float) leave);
            float y = easeInOut(startY, endY, i / (float) total);
            float w = easeInOut(startW, width, Math.min(1f, i / (float) widen));
            addSeg(c, y, camber, w);
        }
        // warning chevrons on the outer side of a real bend; nobody puts up signs among the stars
        if (!space && Math.abs(curve) >= 2.2f) {
            boolean right = curve > 0;
            Bitmap b = right ? sp.chevronRight : sp.chevronLeft;
            float off = right ? -1.3f : 1.3f;
            for (int i = first; i < first + enter + hold / 2; i += 7) segs.get(i).add(new Prop(b, off, W_CHEVRON, true));
        }
    }

    private void addSeg(float curve, float y, float camber, float width) {
        Seg s = new Seg();
        s.index = segs.size();
        if (pendingZone != null && pendingZone == zone) {
            pendingZone = null;
        } else if (pendingZone != null && themes.length > 1) {
            zonePrev = zone;
            zone = pendingZone;
            pendingZone = null;
            wireSide = rnd.nextBoolean() ? 1f : -1f;
            zoneStart = s.index;
            zoneEnd = s.index + zoneLength();
        } else if (s.index >= zoneEnd && themes.length > 1) {
            zonePrev = zone;
            Theme next = zone;
            while (next == zone) next = themes[rnd.nextInt(themes.length)];
            zone = next;
            wireSide = rnd.nextBoolean() ? 1f : -1f;
            zoneStart = s.index;
            zoneEnd = s.index + zoneLength();
        }
        s.theme = zone;
        s.prev = zonePrev;
        s.zoneStart = zoneStart;
        s.curve = curve;
        s.y1 = lastY;
        s.y2 = y;
        lastY = y;
        float bank = curve / 6f * camber * BANK_MAX;
        s.bank1 = lastBank;
        s.bank2 = bank;
        lastBank = bank;
        // the carriageway is as wide as its lanes, eased toward the zone's lane count
        int lanesHere = zone.lanes;
        float target = lanesHere * zone.laneScale / 3f * secVar;
        float wNext = lastWidth + (target - lastWidth) / 30f;
        s.width1 = lastWidth;
        s.width2 = wNext;
        lastWidth = wNext;
        s.lanes = lanesHere;
        // the oncoming carriageway: joined, parting, gone, or coming back
        int want = secOnc > 0 && !reverseMode ? zone.oncoming : 0;
        if (oncState == JOINED && want == 0) {
            oncState = SPLITTING;
            oncStep = 0;
        } else if (oncState == GONE && want > 0) {
            oncState = JOINING;
            oncStep = 0;
        }
        if (oncState == SPLITTING) {
            float g = split(oncStep);
            if (g < 0) {
                oncState = GONE;
            } else {
                s.onc = lastOnc;
                s.gap1 = g;
                float g2 = split(oncStep + 1);
                s.gap2 = g2 < 0 ? GAP_AWAY : g2;
                s.oncPar = oncStep >= DIV && oncStep < DIV + PAR;
                oncStep++;
            }
        } else if (oncState == JOINING) {
            lastOnc = zone.oncoming;
            s.onc = lastOnc;
            s.gap1 = split(SPLIT_LEN - 1 - oncStep);
            s.gap2 = Math.max(0f, split(SPLIT_LEN - 2 - oncStep));
            if (++oncStep >= SPLIT_LEN - 1) oncState = JOINED;
        } else if (oncState == JOINED) {
            lastOnc = zone.oncoming;
            s.onc = lastOnc;
        }
        if (oncState == GONE) s.onc = 0;
        lastGap = s.gap2;
        s.twist = lastTwist;
        if (mode == TWIST) lastTwist += twistStep;
        if (mode == FORK) {
            float g = Math.min(8f, (s.index - secFirst) * 8f / 40f);
            if (forkSide < 0) s.forkL = g;
            else s.forkR = g;
        }
        if (reverseMode) wrongWaySeg(s);
        else forkSeg(s);
        if (!space && exitSegs == 0 && s.index >= nextStation && !reverseMode && forkStep < 0 && s.forkR < 0 && !s.cross) {
            exitSegs = EXIT_LEN;
            stations.add(s.index);
            nextStation = s.index + 2600 + rnd.nextInt(800);
        }
        // signs on the way to the next station, lit at night
        if (!space) {
            int toGo = nextStation - s.index;
            for (int k = 0; k < GAS_SIGNS.length; k++)
                if (toGo == GAS_SIGNS[k]) {
                    Prop g = new Prop(sp.gasSigns[k], 1.4f, k == 3 ? W_GAS_BIG : W_GAS, true);
                    g.glow = 4;
                    s.add(g);
                }
        }
        if (exitSegs > 0) {
            // a slip road peels off to the right and ends at a gas station
            s.exit = true;
            float t = (EXIT_LEN - exitSegs) / (float) EXIT_LEN;
            s.forkR = 4f * t * t * (3 - 2 * t);
            exitSegs--;
            if (exitSegs == 0) {
                Prop st = new Prop(sp.station, 1f + 4f + 0.2f + 0.8f, W_STATION, -0.5f, true, false);
                st.glow = 3;
                s.add(st);
            }
        }
        s.sideL = secSideL;
        s.sideR = secSideR;
        s.hold = 1f - BANK_GRIP * camber;
        int inSec = s.index - secFirst;
        boolean inner = inSec >= 6 && inSec < secTotal - 6;
        if (mode == BRIDGE && inner) {
            s.bridge = true;
            s.add(new Prop(sp.rail, -1.08f, W_RAIL, false));
            s.add(new Prop(sp.rail, 1.08f, W_RAIL, false));
        } else if (mode == FORD && inner) {
            s.water = true;
            if (inSec % 5 == 0) {
                s.add(new Prop(sp.marker, -1.12f, W_MARKER, false));
                s.add(new Prop(sp.marker, 1.12f, W_MARKER, false));
            }
        }
        if (s.index > 0 && s.index % CHECK_SEGS == 0) {
            s.checkpoint = true;
            s.add(new Prop(sp.gateCheckpoint, 0, W_GATE, -0.5f, false, true));
        } else if (s.index > 14 && !s.bridge && !s.water && !s.cross) {
            decorate(s);
            // nothing stands on a slip road, or on the other road of a fork
            boolean clearR = s.exit || exitSegs > 0 || s.forkR >= 0, clearL = s.forkL >= 0;
            if (clearR || clearL) {
                int k = 0;
                for (int i = 0; i < s.nProps; i++) {
                    Prop q = s.props[i];
                    if (q.spans || (q.offset > 0 ? !clearR : !clearL)) s.props[k++] = q;
                }
                s.nProps = k;
            }
        }
        segs.add(s);
    }

    private void decorate(Seg s) {
        int i = s.index;
        if (s.theme.id == Theme.FLOOD) {
            // walls of towers on both sides, and flotsam in the channel
            if (i % 3 == 0) s.add(new Prop(pick(sp.buildings), -(1.75f + rnd.nextFloat() * 0.3f), W_BUILDING, true));
            if (i % 3 == 1) s.add(new Prop(pick(sp.buildings), 1.75f + rnd.nextFloat() * 0.3f, W_BUILDING, true));
            if (i > 60 && rnd.nextFloat() < 0.045f) {
                float r = rnd.nextFloat();
                Prop d = new Prop(r < 0.4f ? sp.barrelFloat : r < 0.75f ? sp.crate : sp.plank, (rnd.nextFloat() - 0.5f) * 1.8f,
                        r < 0.4f ? 520 : r < 0.75f ? 640 : 900, false);
                d.debris = true;
                s.add(d);
            }
        } else if (s.theme.id == Theme.SPACE) {
            if (i % 4 == 0) {
                // whole rings round the tube, each turned a little further: a spiral of stars
                Prop ring = new Prop(sp.starRings[(i / 4) % sp.starRings.length], 0, W_RING, -0.5f, false, true);
                ring.sink = 0.32f;
                s.add(ring);
            }
            if (rnd.nextFloat() < 0.08f) {
                float side = rnd.nextBoolean() ? 1 : -1;
                s.add(new Prop(rnd.nextBoolean() ? sp.constellation : sp.constellationB, side * (1.6f + rnd.nextFloat() * 3f), W_STARS, false));
            }
        } else if (s.theme.id == Theme.COAST) {
            // hillside on the left, open sea on the right
            if (rnd.nextFloat() < 0.20f) {
                float off = -(1.25f + rnd.nextFloat() * 2.6f);
                float k = rnd.nextFloat();
                if (k < 0.42f) s.add(new Prop(sp.bush, off, W_BUSH, false));
                else if (k < 0.68f) s.add(new Prop(sp.treeGreen, off, W_TREE, true));
                else if (k < 0.85f) s.add(new Prop(sp.cypress, off, W_CYPRESS, true));
                else s.add(new Prop(sp.rock, off, W_ROCK, true));
            }
            if (rnd.nextFloat() < 0.04f) {
                float off = 1.25f + rnd.nextFloat() * 1.4f;
                s.add(rnd.nextBoolean() ? new Prop(sp.rock, off, W_ROCK, true) : new Prop(sp.bush, off, W_BUSH, false));
            }
            if (i % 420 == 210) s.add(new Prop(sp.billboard, 1.35f, W_BILLBOARD, true));
        } else if (s.theme.id == Theme.COUNTRY) {
            if (i % 34 == 0) {
                Prop pole = new Prop(sp.pole, wireSide * 1.7f, W_POLE, true);
                pole.wire = true;
                s.add(pole);
            }
            float r = rnd.nextFloat();
            float side = -wireSide;
            if (i % 260 == 130) s.add(Track.animated(sp.windmill, side * (2.6f + rnd.nextFloat() * 1.4f), W_MILL, 5f));
            else if (i % 190 == 60) s.add(new Prop(sp.barn, side * (2.8f + rnd.nextFloat()), W_BARN, true));
            else if (r < 0.05f) s.add(new Prop(sp.hay, (rnd.nextBoolean() ? 1 : -1) * (1.5f + rnd.nextFloat() * 2f), W_HAY, true));
            else if (r < 0.08f) s.add(new Prop(sp.treeGreen, side * (1.5f + rnd.nextFloat() * 2f), W_TREE, true));
        } else if (s.theme.id == Theme.JUNGLE) {
            float r = rnd.nextFloat();
            if (r < 0.22f) {
                float off = (rnd.nextBoolean() ? 1 : -1) * (1.3f + rnd.nextFloat() * 2.2f);
                s.add(new Prop(rnd.nextBoolean() ? sp.palm : sp.palmB, off, W_PALM, true));
            } else if (r < 0.40f) {
                float off = (rnd.nextBoolean() ? 1 : -1) * (1.2f + rnd.nextFloat() * 1.6f);
                s.add(new Prop(sp.fern, off, W_FERN, false));
            } else if (r < 0.43f) {
                s.add(new Prop(sp.rock, (rnd.nextBoolean() ? 1 : -1) * (1.3f + rnd.nextFloat()), W_ROCK, true));
            }
        } else if (s.theme.id == Theme.DESERT) {
            float r = rnd.nextFloat(), side = rnd.nextBoolean() ? 1 : -1;
            if (r < 0.10f) s.add(new Prop(rnd.nextBoolean() ? sp.saguaro : sp.saguaroB, side * (1.4f + rnd.nextFloat() * 2.6f), W_SAGUARO, true));
            else if (r < 0.15f) s.add(new Prop(sp.barrel, side * (1.3f + rnd.nextFloat() * 1.5f), W_BARREL, true));
            else if (r < 0.18f) s.add(new Prop(sp.redRock, side * (1.6f + rnd.nextFloat() * 2.5f), W_REDROCK, true));
            if (i % 400 == 200) s.add(new Prop(sp.routeSign, 1.3f, W_ROUTE, true));
        } else if (s.theme.id == Theme.TAIGA) {
            float r = rnd.nextFloat(), side = rnd.nextBoolean() ? 1 : -1;
            if (r < 0.26f) s.add(new Prop(sp.snowFir, side * (1.4f + rnd.nextFloat() * 2.6f), W_FIR, true));
            else if (r < 0.31f) s.add(new Prop(sp.drift, side * (1.25f + rnd.nextFloat()), W_DRIFT, false));
            if (i % 150 == 75) s.add(new Prop(sp.bear, side * (2.2f + rnd.nextFloat() * 1.6f), W_BEAR, true));
        } else if (s.theme.id == Theme.SAFARI) {
            float r = rnd.nextFloat(), side = rnd.nextBoolean() ? 1 : -1;
            if (r < 0.07f) s.add(new Prop(sp.acacia, side * (1.8f + rnd.nextFloat() * 3f), W_ACACIA, true));
            else if (r < 0.10f) s.add(new Prop(sp.termites, side * (1.4f + rnd.nextFloat() * 1.5f), W_TERMITES, true));
            else if (r < 0.13f) s.add(new Prop(sp.bush, side * (1.3f + rnd.nextFloat() * 2f), W_BUSH, false));
            if (i % 120 == 60) s.add(new Prop(sp.giraffe, side * (2.4f + rnd.nextFloat() * 2.5f), W_GIRAFFE, true));
        } else {
            townSide(s, -1, s.sideL);
            townSide(s, 1, s.sideR);
            if (i % 520 == 260) s.add(new Prop(sp.billboard, -1.35f, W_BILLBOARD, true));
        }
    }

    /** One side of a town road: blocks of houses, a park, or a beach down to the sea. */
    private void townSide(Seg s, int side, int style) {
        int i = s.index;
        if (style == SIDE_BEACH) {
            if (rnd.nextFloat() < 0.08f) s.add(new Prop(rnd.nextBoolean() ? sp.umbrellaRed : sp.umbrellaBlue, side * (1.4f + rnd.nextFloat() * 0.8f), W_UMBRELLA, true));
            if (i % 30 == 0) s.add(new Prop(sp.palm, side * 1.3f, W_PALM, true));
            return;
        }
        if (side > 0 && (s.exit || exitSegs > 0)) return;
        if (i % 24 == 0) {
            Prop lamp = new Prop(sp.lamp, side * 1.2f, W_LAMP, true);
            lamp.glow = 1;
            s.add(lamp);
        }
        if (style == SIDE_PARK) {
            float r = rnd.nextFloat();
            if (r < 0.12f) s.add(new Prop(rnd.nextBoolean() ? sp.treeGreen : sp.treeAutumn, side * (1.5f + rnd.nextFloat() * 2.5f), W_TREE, true));
            else if (r < 0.2f) s.add(new Prop(sp.bush, side * (1.35f + rnd.nextFloat() * 1.5f), W_BUSH, false));
            return;
        }
        if (i % 14 == (side < 0 ? 0 : 7)) s.add(new Prop(pick(sp.buildings), side * (2.1f + rnd.nextFloat() * 0.9f), W_BUILDING, true));
        if (rnd.nextFloat() < 0.045f) s.add(new Prop(sp.treeAutumn, side * (1.4f + rnd.nextFloat() * 0.5f), W_TREE, true));
    }

    private Bitmap pick(Bitmap[] a) {
        return a[rnd.nextInt(a.length)];
    }

    private static float easeIn(float a, float b, float t) {
        return a + (b - a) * t * t;
    }

    private static float easeInOut(float a, float b, float t) {
        return a + (b - a) * (float) ((-Math.cos(t * Math.PI) / 2) + 0.5);
    }
}
