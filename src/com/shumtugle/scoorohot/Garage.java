package com.shumtugle.scoorohot;

import android.graphics.Bitmap;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Every vehicle in the game, built from cubes. The player's choice is rendered
 * from five yaws for steering; traffic is pre-rendered at every pixel width it
 * can appear at, so nothing on the road is ever stretched.
 */
final class Garage {
    // palette slots; slots from EMISSIVE up glow and ignore shading
    static final int BODY = 1, DARK = 2, ROOF = 3, STRIPE = 4, GLASS = 5, GLASS_HI = 6, SLAT = 7, BLACK = 8,
            CHROME = 9, TYRE = 10, RIM = 11, PLATE = 12, PIPE = 13, DECAL = 14, DIGIT = 15, SPOILER = 16,
            EXTRA1 = 17, EXTRA2 = 18, EXTRA3 = 19, EXTRA4 = 20;
    static final int EMISSIVE = 23, HEAD = 23, LIGHT = 24, LIGHT_HI = 25, BAR_R = 26, BAR_B = 27, GLOW1 = 28, GLOW2 = 29,
            GLOW3 = 30, SIGN = 31;
    static final int VOICE_PETROL = 0, VOICE_DIESEL = 1, VOICE_HOVER = 2, VOICE_ROLL = 3;
    static final int FASTBACK = 0, NOTCH = 1, SEDAN = 2, HATCH = 3, WAGON = 4;
    static final float[] YAWS = {-16f, -8f, 0f, 8f, 16f};
    static final float PLAYER_PITCH = 16f;
    static final float CHASE_PITCH = 34f;   // the chase is watched from higher up
    static final int SPIN_VIEWS = 16;
    static final int POLICE_INDEX = 12, UFO_INDEX = 14;

    /** How much of each knock a vehicle takes: the heavy ones shrug off half. */
    static float toughness(int vehicle) {
        switch (vehicle) {
            case 13: // the bus
            case 15: // the bowling ball
                return 0.5f;
            case 9:  // the wagon
            case 10: // the heavy sedan
            case 12: // the patrol car
                return 0.75f;
            case UFO_INDEX:
                return 0.7f;
            default:
                return 1f;
        }
    }
    // traffic: seven cars, two vans, a bus, a patrol car
    static final int T_CARS = 7, T_VAN = 7, T_BUS = 9, T_POLICE = 10;
    static final int LADDER_MAX = 80;
    private static final int INK = 0xFF07070D;

    /** A rendered picture and the pixel that sits on the road under the vehicle's center. */
    static final class Sprite {
        final Bitmap bmp;
        final int ax, ay;

        Sprite(Bitmap bmp, int ax, int ay) {
            this.bmp = bmp;
            this.ax = ax;
            this.ay = ay;
        }
    }

    static final class Model {
        final String name;
        final Voxel[] shapes;     // geometry frames: one, or several for things that roll
        final int[] palette;
        final float worldW;
        final int voice;
        final boolean wheels, rolls, hovers;
        final float animRate;     // frames per second for palette animation
        final int paletteFrames;
        private final HashMap<Integer, Sprite[][][]> views = new HashMap<Integer, Sprite[][][]>();
        private final AtomicReferenceArray<Sprite> ladder = new AtomicReferenceArray<Sprite>(LADDER_MAX + 1);
        // side views for crossing traffic: index 0 drives left to right, 1 right to left
        private final AtomicReferenceArray<Sprite> sideR = new AtomicReferenceArray<Sprite>(LADDER_MAX + 1);
        private final AtomicReferenceArray<Sprite> sideL = new AtomicReferenceArray<Sprite>(LADDER_MAX + 1);
        private final HashMap<Integer, Sprite[]> spins = new HashMap<Integer, Sprite[]>();
        private final AtomicReferenceArray<Sprite> front = new AtomicReferenceArray<Sprite>(LADDER_MAX + 1);
        private final HashMap<Integer, Sprite[]> singles = new HashMap<Integer, Sprite[]>();

        /** One straight-on view at any pitch and size, with its light frames: for the great saucer. */
        Sprite single(int width, float pitchDeg, int frame) {
            width = Math.max(8, Math.min(260, width / 8 * 8));
            int key = width * 1000 + Math.round(pitchDeg) + 500;
            Sprite[] s = singles.get(key);
            if (s == null) {
                Voxel.View v = shapes[0].render(width / shapes[0].bodyWidth, 0f, pitchDeg);
                int n = Math.max(1, paletteFrames);
                s = new Sprite[n];
                for (int f = 0; f < n; f++) s[f] = new Sprite(colorize(v, palette(false, f), true), v.ax, v.ay);
                singles.put(key, s);
            }
            return s[frame % s.length];
        }

        Model(String name, Voxel[] shapes, int[] palette, int voice, boolean wheels, boolean rolls, boolean hovers,
              float animRate, int paletteFrames) {
            this.name = name;
            this.shapes = shapes;
            this.palette = palette;
            this.worldW = Race.CAR_W * shapes[0].bodyWidth / 40f; // forty cells make one standard car width
            this.voice = voice;
            this.wheels = wheels;
            this.rolls = rolls;
            this.hovers = hovers;
            this.animRate = animRate;
            this.paletteFrames = paletteFrames;
        }

        int frames() {
            return Math.max(shapes.length, paletteFrames);
        }

        /** Colors for a lights state and an animation frame. */
        int[] palette(boolean brake, int frame) {
            int[] p = palette.clone();
            p[LIGHT] = brake ? 0xFFFF3420 : 0xFFA8161A;
            p[LIGHT_HI] = brake ? 0xFFFFD8B8 : 0xFFE0483A;
            if (paletteFrames == 2) {
                boolean a = (frame & 1) == 0;
                p[BAR_R] = a ? 0xFFFF2A20 : 0xFF5A1010;
                p[BAR_B] = a ? 0xFF1A2A5A : 0xFF4A8AFF;
            } else if (paletteFrames > 2) {
                int[] chase = {0xFFFFD24A, 0xFFFF4A3A, 0xFF5AE8FF};
                for (int i = 0; i < 3; i++) p[GLOW1 + i] = brake ? 0xFFFF3020 : chase[(i + frame) % 3];
            }
            return p;
        }

        Sprite[][][] views(int width) {
            return views(width, PLAYER_PITCH);
        }

        /** The player's views at one pixel width and pitch: [yaw][lights][frame]. Rendered on first use. */
        Sprite[][][] views(int width, float pitchDeg) {
            int key = width * 100 + Math.round(pitchDeg);
            Sprite[][][] s = views.get(key);
            if (s != null) return s;
            int n = frames();
            s = new Sprite[YAWS.length][2][n];
            float ppv = width / shapes[0].bodyWidth;
            for (int y = 0; y < YAWS.length; y++) {
                Voxel.View[] rendered = new Voxel.View[shapes.length];
                for (int g = 0; g < shapes.length; g++) rendered[g] = shapes[g].render(ppv, YAWS[y], pitchDeg);
                for (int b = 0; b < 2; b++)
                    for (int f = 0; f < n; f++) {
                        Voxel.View v = rendered[f % shapes.length];
                        s[y][b][f] = new Sprite(colorize(v, palette(b == 1, f % Math.max(1, paletteFrames)), true), v.ax, v.ay);
                    }
            }
            views.put(key, s);
            return s;
        }

        /** A full turn of the body in sixteen steps, for a spin. Index 0 faces away from the camera. */
        Sprite[] spin(int width) {
            Sprite[] s = spins.get(width);
            if (s != null) return s;
            s = new Sprite[SPIN_VIEWS];
            float ppv = width / shapes[0].bodyWidth;
            for (int i = 0; i < SPIN_VIEWS; i++) {
                Voxel.View v = shapes[0].render(ppv, i * 360f / SPIN_VIEWS, PLAYER_PITCH);
                s[i] = new Sprite(colorize(v, palette(true, 0), true), v.ax, v.ay);
            }
            spins.put(width, s);
            return s;
        }

        /** A crossing car seen from the side at a pixel width; dir is +1 for left to right. */
        Sprite side(int width, int dir) {
            if (width < 2) return null;
            if (width > LADDER_MAX) width = LADDER_MAX;
            AtomicReferenceArray<Sprite> l = dir > 0 ? sideR : sideL;
            for (int d = 0; d <= 10; d++) {
                Sprite s = l.get(Math.max(2, width - d));
                if (s != null) return s;
                if (width + d <= LADDER_MAX && (s = l.get(width + d)) != null) return s;
            }
            return null;
        }

        /** An oncoming car, nose first, at a pixel width; null until its picture is ready. */
        Sprite front(int width) {
            if (width < 2) return null;
            if (width > LADDER_MAX) width = LADDER_MAX;
            for (int d = 0; d <= 10; d++) {
                Sprite s = front.get(Math.max(2, width - d));
                if (s != null) return s;
                if (width + d <= LADDER_MAX && (s = front.get(width + d)) != null) return s;
            }
            return null;
        }

        void warmFront(int width) {
            if (front.get(width) != null) return;
            float pitch = 7f + 9f * Math.min(1f, width / 64f);
            Voxel.View v = shapes[0].render(width / shapes[0].bodyWidth, 180f, pitch);
            front.set(width, new Sprite(colorize(v, palette(false, 0), width >= 10), v.ax, v.ay));
        }

        void warmSide(int width) {
            if (sideR.get(width) != null) return;
            float pitch = 7f + 9f * Math.min(1f, width / 64f);
            float ppv = width / shapes[0].bodyWidth;
            // a nose to the right shows the right flank: yaw -90 moves left to right
            Voxel.View r = shapes[0].render(ppv, -90f, pitch);
            sideR.set(width, new Sprite(colorize(r, palette(false, 0), width >= 10), r.ax, r.ay));
            Voxel.View l = shapes[0].render(ppv, 90f, pitch);
            sideL.set(width, new Sprite(colorize(l, palette(false, 0), width >= 10), l.ax, l.ay));
        }

        /** Traffic picture closest to a pixel width, or null if none is ready yet. */
        Sprite traffic(int width) {
            if (width < 2) return null;
            if (width > LADDER_MAX) width = LADDER_MAX;
            for (int d = 0; d <= 10; d++) {
                Sprite s = ladder.get(Math.max(2, width - d));
                if (s != null) return s;
                if (width + d <= LADDER_MAX) {
                    s = ladder.get(width + d);
                    if (s != null) return s;
                }
            }
            return null;
        }

        /** Renders one rung of the traffic ladder; nearer cars are seen from a little higher. */
        void warm(int width) {
            if (ladder.get(width) != null) return;
            float pitch = 7f + 9f * Math.min(1f, width / 64f);
            Voxel.View v = shapes[0].render(width / shapes[0].bodyWidth, 0f, pitch);
            ladder.set(width, new Sprite(colorize(v, palette(false, 0), width >= 10), v.ax, v.ay));
        }
    }

    final Model[] vehicles;
    final Model[] traffic;
    final float[] trafficWidths;

    Garage() {
        vehicles = new Model[]{
                car("BLACK BLAZE", FASTBACK, 0xFF24262E, 0xFF24262E, 0, 0, true, false, 0, false),
                car("RED FURY", NOTCH, 0xFFC42A22, 0xFFC42A22, 0, 0, false, false, 0, false),
                car("STRIPE GT", NOTCH, 0xFFE4E4DE, 0xFFE4E4DE, 0xFF2448B4, 1, false, false, 0, false),
                car("SUN RACER", NOTCH, 0xFFE8B21E, 0xFFE8B21E, 0, 0, false, true, 0, false),
                car("FOREST RUNNER", HATCH, 0xFF2E8A3E, 0xFF2E8A3E, 0, 0, false, false, 0, false),
                car("SKYLINE", NOTCH, 0xFF2458C8, 0xFF2458C8, 0, 0, false, false, 2, false),
                car("DESERT EAGLE", SEDAN, 0xFFD6CAA6, 0xFF2A2C34, 0, 0, false, false, 0, false),
                car("NIGHT VIPER", FASTBACK, 0xFF5B3CB4, 0xFF5B3CB4, 0, 0, true, false, 0, false),
                car("RALLY STAR", NOTCH, 0xFFCC2A20, 0xFFCC2A20, 0xFFF2F2EC, 2, false, false, 7, false),
                car("COAST WAGON", WAGON, 0xFF2EA4A4, 0xFF2EA4A4, 0, 0, false, false, 0, false),
                car("IRON HORSE", SEDAN, 0xFF4E525C, 0xFF4E525C, 0, 0, false, false, 0, false),
                car("TIGER", FASTBACK, 0xFFEC7420, 0xFFEC7420, 0, 0, true, true, 0, false),
                car("POLICE", SEDAN, 0xFF18181E, 0xFFF0F0EA, 0, 0, false, false, 0, true),
                new Model("BUS", new Voxel[]{bus()}, busPalette(), VOICE_DIESEL, true, false, false, 0f, 1),
                new Model("UFO", new Voxel[]{ufo()}, ufoPalette(), VOICE_HOVER, false, false, true, 6f, 4),
                new Model("BOWLING BALL", balls(), ballPalette(), VOICE_ROLL, false, true, false, 0f, 1),
        };
        traffic = new Model[]{
                car("", NOTCH, 0xFFB8312A, 0xFFB8312A, 0, 0, false, false, 0, false),
                car("", SEDAN, 0xFF2A7F8F, 0xFF2A7F8F, 0, 0, false, false, 0, false),
                car("", HATCH, 0xFFD9A520, 0xFFD9A520, 0, 0, false, false, 0, false),
                car("", SEDAN, 0xFFD8D8D2, 0xFF3A3C44, 0, 0, false, false, 0, false),
                car("", HATCH, 0xFF3A7A3A, 0xFF3A7A3A, 0, 0, false, false, 0, false),
                car("", WAGON, 0xFF8A5A3A, 0xFF8A5A3A, 0, 0, false, false, 0, false),
                car("", FASTBACK, 0xFFA0A6AE, 0xFFA0A6AE, 0, 0, false, false, 0, false),
                new Model("", new Voxel[]{van()}, vanPalette(0xFF2E4F9A), VOICE_PETROL, true, false, false, 0f, 1),
                new Model("", new Voxel[]{van()}, vanPalette(0xFFD8661E), VOICE_PETROL, true, false, false, 0f, 1),
                new Model("", new Voxel[]{bus()}, busPalette(), VOICE_DIESEL, true, false, false, 0f, 1),
                car("", SEDAN, 0xFF18181E, 0xFFF0F0EA, 0, 0, false, false, 0, true),
        };
        trafficWidths = new float[traffic.length];
        for (int i = 0; i < traffic.length; i++) trafficWidths[i] = traffic[i].worldW;
    }

    /** Fills the traffic ladders, small sizes first since far cars appear first. Runs on a background thread. */
    void prewarm() {
        for (int w = 2; w <= LADDER_MAX; w += w < 24 ? 1 : w < 48 ? 2 : 4)
            for (Model m : traffic) {
                m.warm(w);
                m.warmFront(w);
            }
        for (int w = 2; w <= LADDER_MAX; w += w < 24 ? 1 : w < 48 ? 2 : 4)
            for (int k = 0; k < Race.CROSS_KINDS; k++) traffic[k].warmSide(w);
    }

    // ------------------------------------------------------------- coloring

    static Bitmap colorize(Voxel.View v, int[] pal, boolean outline) {
        int n = v.w * v.h;
        int[] px = new int[n];
        for (int i = 0; i < n; i++) {
            int idx = v.idx[i] & 0xFF;
            if (idx == 0) continue;
            if (idx == 255) {
                px[i] = 0x50000000;
                continue;
            }
            int c = pal[idx];
            px[i] = idx >= EMISSIVE ? c | 0xFF000000 : shade(c, Voxel.SHADE[v.face[i]]);
        }
        if (outline) {
            int[] src = px.clone();
            for (int y = 0; y < v.h; y++)
                for (int x = 0; x < v.w; x++) {
                    int i = y * v.w + x;
                    if ((src[i] >>> 24) == 0xFF) continue;
                    if ((x > 0 && (src[i - 1] >>> 24) == 0xFF) || (x < v.w - 1 && (src[i + 1] >>> 24) == 0xFF)
                            || (y > 0 && (src[i - v.w] >>> 24) == 0xFF) || (y < v.h - 1 && (src[i + v.w] >>> 24) == 0xFF))
                        px[i] = INK;
                }
        }
        return Bitmap.createBitmap(px, v.w, v.h, Bitmap.Config.ARGB_8888);
    }

    private static int shade(int c, float k) {
        int r = Math.min(255, (int) (((c >> 16) & 255) * k));
        int g = Math.min(255, (int) (((c >> 8) & 255) * k));
        int b = Math.min(255, (int) ((c & 255) * k));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int darker(int c, float k) {
        return shade(c, k);
    }

    private static int[] basePalette(int body, int roof) {
        int[] p = new int[32];
        p[BODY] = body;
        p[DARK] = darker(body, 0.6f);
        p[ROOF] = roof;
        p[STRIPE] = 0xFFF2F2EC;
        p[GLASS] = 0xFF22324A;
        p[GLASS_HI] = 0xFF4A6A90;
        p[SLAT] = 0xFF0E0E14;
        p[BLACK] = 0xFF1C1C22;
        p[CHROME] = 0xFFD4D8DE;
        p[TYRE] = 0xFF17171B;
        p[RIM] = 0xFF8A8E96;
        p[PLATE] = 0xFFE6E6DC;
        p[PIPE] = 0xFF9A9AA2;
        p[DECAL] = 0xFFF2F2EC;
        p[DIGIT] = 0xFF141418;
        p[SPOILER] = darker(body, 0.5f);
        p[EXTRA1] = 0xFFF0F0EA;
        p[SIGN] = 0xFFFFB830;
        p[HEAD] = 0xFFFFF0C0;
        return p;
    }

    // ---------------------------------------------------------------- cars

    /** Glyphs 3x5 for door numbers and route signs, top row first. */
    private static final String[] DIGITS = {
            "####.##.##.####", "..#..#..#..#..#", "###..#####..###", "###..####..####", "#.##.####..#..#",
            "####..###..####", "####..####.####", "###..#..#..#..#", "####.#####.####", "####.####..####"};

    private static boolean digitBit(int d, int col, int row) {
        return DIGITS[d].charAt(row * 3 + col) == '#';
    }

    private static Model car(String name, int style, int body, int roof, int stripe, int stripes,
                             boolean louvers, boolean spoiler, int number, boolean police) {
        int[] pal = basePalette(body, roof);
        if (stripe != 0) pal[STRIPE] = stripe;
        Voxel v = carShape(style, louvers, spoiler, stripes, number, police);
        return new Model(name, new Voxel[]{v}, pal, VOICE_PETROL, true, false, false, police ? 5f : 0f, police ? 2 : 1);
    }

    /** Rear deck start, roof start, roof end, windscreen base, roof height. */
    private static int[] profile(int style, int z0) {
        switch (style) {
            case FASTBACK:
                return new int[]{z0 + 10, z0 + 32, z0 + 52, z0 + 61, 24};
            case NOTCH:
                return new int[]{z0 + 20, z0 + 29, z0 + 51, z0 + 61, 25};
            case SEDAN:
                return new int[]{z0 + 18, z0 + 26, z0 + 52, z0 + 62, 26};
            case HATCH:
                return new int[]{z0 + 3, z0 + 9, z0 + 47, z0 + 57, 26};
            default:
                return new int[]{z0 + 2, z0 + 5, z0 + 54, z0 + 63, 26};
        }
    }

    private static int cabinTop(int z, int[] p) {
        if (z < p[0] || z >= p[3]) return 14;
        if (z < p[1]) return 14 + (p[4] - 14) * (z - p[0] + 1) / (p[1] - p[0] + 1);
        if (z < p[2]) return p[4];
        return p[4] - (p[4] - 14) * (z - p[2]) / (p[3] - p[2]);
    }

    private static boolean pillar(int z, int[] p, int style) {
        int mid = (p[1] + p[2]) / 2;
        if (z >= mid - 1 && z < mid + 1) return true;
        if (style == WAGON) {
            int third = (p[2] - p[1]) / 3;
            return (z >= p[1] + third - 1 && z < p[1] + third + 1) || (z >= p[2] - third - 1 && z < p[2] - third + 1);
        }
        return false;
    }

    private static boolean arch(int y, int z, float cy, float cz, float r) {
        float dy = y + 0.5f - cy, dz = z + 0.5f - cz;
        return dy * dy + dz * dz < r * r;
    }

    /**
     * A road car, about 40 cells wide and 86 long: body with wheel arches,
     * cabin by profile, rear lights, bumper, plate, twin exhausts, mirrors,
     * and optional louvers, spoiler, stripes, door numbers and a light bar.
     */
    private static Voxel carShape(int style, boolean louvers, boolean spoiler, int stripes, int number, boolean police) {
        Voxel v = new Voxel(46, 30, 92);
        v.bodyWidth = 40;
        int z0 = 3, z1 = style == HATCH ? 84 : 89;
        float wr = 7.2f, cy = 7.2f;
        int rz = z0 + 15, fz = z1 - 16;
        for (int z = z0; z < z1; z++) {
            int top = 14;
            if (z > z1 - 22) top = 14 - (z - (z1 - 22)) / 9;
            int inset = (z == z0 || z == z1 - 1) ? 1 : 0;
            for (int y = 4; y < top; y++)
                for (int x = 3 + inset; x < 43 - inset; x++) {
                    boolean side = x < 10 || x >= 36;
                    if (side && (arch(y, z, cy, rz, wr + 1.3f) || arch(y, z, cy, fz, wr + 1.3f))) continue;
                    v.set(x, y, z, y <= 6 ? DARK : BODY);
                }
        }
        int[] axles = {rz, fz};
        for (int zc : axles) {
            v.cylinderX(3, 10, cy, zc, wr, TYRE);
            v.cylinderX(36, 43, cy, zc, wr, TYRE);
            v.cylinderX(3, 4, cy, zc, 4.4f, RIM);
            v.cylinderX(42, 43, cy, zc, 4.4f, RIM);
            v.cylinderX(3, 4, cy, zc, 1.6f, CHROME);
            v.cylinderX(42, 43, cy, zc, 1.6f, CHROME);
        }
        int[] p = profile(style, z0);
        for (int z = p[0]; z < p[3]; z++) {
            int h = cabinTop(z, p);
            for (int y = 14; y < h; y++) {
                int inset = 4 + (int) ((y - 14) * 0.45f);
                int xa = 3 + inset, xb = 43 - inset;
                for (int x = xa; x < xb; x++) {
                    boolean edge = x < xa + 2 || x >= xb - 2;
                    int c;
                    if (y == h - 1 && z >= p[1] && z < p[2]) c = ROOF;
                    else if (z < p[1]) c = edge ? ROOF : louvers ? ((z - p[0]) % 3 == 2 ? GLASS_HI : SLAT) : GLASS;
                    else if (z >= p[2]) c = edge ? ROOF : GLASS;
                    else c = (y >= 16 && y < h - 1 && !pillar(z, p, style)) ? GLASS : ROOF;
                    v.set(x, y, z, c);
                }
            }
        }
        // a streak of light across the rear glass
        if (!louvers)
            for (int i = 0; i < 5; i++) v.paint(12 + i, 13 + i, 15 + i, 16 + i, p[0], p[1] + 1, GLASS_HI);
        // rear end
        v.paint(5, 15, 9, 12, z0, z0 + 1, LIGHT);
        v.paint(31, 41, 9, 12, z0, z0 + 1, LIGHT);
        v.paint(7, 13, 10, 11, z0, z0 + 1, LIGHT_HI);
        v.paint(33, 39, 10, 11, z0, z0 + 1, LIGHT_HI);
        if (style != WAGON) v.paint(15, 31, 9, 12, z0, z0 + 1, BLACK);
        v.box(3, 43, 4, 7, z0 - 2, z0 + 2, BLACK);
        v.box(3, 43, 4, 7, z1 - 1, z1 + 1, BLACK);
        // the face oncoming drivers see: headlamps either side of a dark grille, a plate below
        v.paint(5, 13, 9, 12, z1 - 1, z1, HEAD);
        v.paint(33, 41, 9, 12, z1 - 1, z1, HEAD);
        v.paint(14, 32, 8, 12, z1 - 1, z1, BLACK);
        v.box(18, 28, 7, 10, z1 + 1, z1 + 2, PLATE);
        v.box(18, 28, 7, 10, z0 - 1, z0, PLATE);
        v.box(11, 13, 3, 5, z0 - 3, z0 + 3, PIPE);
        v.box(14, 16, 3, 5, z0 - 3, z0 + 3, PIPE);
        // side trim and door line
        v.paint(3, 4, 8, 9, z0 + 2, z1 - 2, CHROME);
        v.paint(42, 43, 8, 9, z0 + 2, z1 - 2, CHROME);
        int door = (p[1] + p[2]) / 2 + 2;
        v.paint(3, 4, 7, 14, door, door + 1, DARK);
        v.paint(42, 43, 7, 14, door, door + 1, DARK);
        // mirrors
        v.box(1, 3, 15, 17, p[3] - 4, p[3] - 2, ROOF);
        v.box(43, 45, 15, 17, p[3] - 4, p[3] - 2, ROOF);
        if (spoiler) {
            v.box(4, 42, 17, 19, z0 + 1, z0 + 6, SPOILER);
            v.box(10, 12, 14, 17, z0 + 2, z0 + 5, SPOILER);
            v.box(34, 36, 14, 17, z0 + 2, z0 + 5, SPOILER);
        }
        if (stripes > 0) {
            for (int z = 0; z < v.gz; z++)
                for (int y = 8; y < v.gy; y++)
                    for (int x = 0; x < v.gx; x++) {
                        boolean band = (x >= 16 && x < 20) || (x >= 26 && x < 30);
                        int c = v.get(x, y, z);
                        if (band && (c == BODY || c == ROOF)) v.set(x, y, z, STRIPE);
                    }
            if (stripes == 2) {
                v.paint(3, 4, 11, 13, z0 + 2, z1 - 2, STRIPE);
                v.paint(42, 43, 11, 13, z0 + 2, z1 - 2, STRIPE);
            }
        }
        if (number > 0) {
            int zc = door - 9, yc = 10;
            for (int z = zc - 5; z <= zc + 5; z++)
                for (int y = yc - 5; y <= yc + 5; y++) {
                    float dz = z - zc, dy = y - yc;
                    if (dz * dz + dy * dy > 4.6f * 4.6f) continue;
                    int col = z - (zc - 1), row = (yc + 2) - y;
                    boolean ink = col >= 0 && col < 3 && row >= 0 && row < 5 && digitBit(number, col, row);
                    for (int x : new int[]{3, 42}) if (v.get(x, y, z) != 0) v.set(x, y, z, ink ? DIGIT : DECAL);
                }
        }
        if (police) {
            for (int x : new int[]{3, 42}) v.paint(x, x + 1, 7, 14, door - 14, door + 14, EXTRA1);
            int top = p[4];
            v.box(14, 23, top, top + 2, p[1] + 6, p[1] + 11, BAR_R);
            v.box(23, 32, top, top + 2, p[1] + 6, p[1] + 11, BAR_B);
        }
        return v;
    }

    // ------------------------------------------------------------ the rest

    private static Voxel bus() {
        Voxel v = new Voxel(60, 62, 152);
        v.bodyWidth = 54;
        float wr = 9.5f, cy = 9.5f;
        for (int z = 4; z < 148; z++)
            for (int y = 6; y < 58; y++)
                for (int x = 3; x < 57; x++) {
                    boolean side = x < 13 || x >= 47;
                    if (side && y < 20 && (arch(y, z, cy, 34, wr + 1.5f) || arch(y, z, cy, 128, wr + 1.5f))) continue;
                    v.set(x, y, z, y >= 20 && y < 36 ? EXTRA2 : y >= 56 ? ROOF : EXTRA1);
                }
        for (int zc : new int[]{34, 128}) {
            v.cylinderX(3, 13, cy, zc, wr, TYRE);
            v.cylinderX(47, 57, cy, zc, wr, TYRE);
            v.cylinderX(3, 4, cy, zc, 5f, RIM);
            v.cylinderX(56, 57, cy, zc, 5f, RIM);
        }
        v.paint(7, 53, 38, 53, 4, 5, GLASS);
        for (int i = 0; i < 8; i++) v.paint(11 + i, 12 + i, 51 - i, 52 - i, 4, 5, GLASS_HI);
        v.paint(14, 46, 53, 58, 4, 5, BLACK);
        for (int d = 0; d < 2; d++) {
            int digit = d == 0 ? 7 : 8;
            for (int col = 0; col < 3; col++)
                for (int row = 0; row < 5; row++)
                    if (digitBit(digit, col, row)) v.paint(26 + d * 5 + col, 27 + d * 5 + col, 57 - row - 1, 57 - row, 4, 5, SIGN);
        }
        for (int y = 22; y < 33; y += 2) v.paint(16, 44, y, y + 1, 4, 5, BLACK);
        v.paint(5, 11, 22, 36, 4, 5, LIGHT);
        v.paint(49, 55, 22, 36, 4, 5, LIGHT);
        v.paint(6, 10, 30, 33, 4, 5, LIGHT_HI);
        v.paint(50, 54, 30, 33, 4, 5, LIGHT_HI);
        v.box(3, 57, 5, 10, 2, 6, BLACK);
        v.box(25, 35, 10, 14, 3, 4, PLATE);
        v.paint(6, 14, 12, 18, 147, 148, HEAD);
        v.paint(46, 54, 12, 18, 147, 148, HEAD);
        v.paint(7, 53, 30, 52, 147, 148, GLASS);
        for (int z = 10; z < 144; z++) {
            boolean post = (z - 10) % 18 < 2;
            if (!post) {
                v.paint(3, 4, 38, 54, z, z + 1, GLASS);
                v.paint(56, 57, 38, 54, z, z + 1, GLASS);
            }
        }
        return v;
    }

    private static int[] busPalette() {
        int[] p = basePalette(0xFFE8DCC0, 0xFFF6EEDA);
        p[EXTRA1] = 0xFFE8DCC0;
        p[EXTRA2] = 0xFFD8661E;
        return p;
    }

    private static Voxel van() {
        Voxel v = new Voxel(46, 42, 98);
        v.bodyWidth = 40;
        float wr = 7.5f, cy = 7.5f;
        for (int z = 3; z < 94; z++) {
            int top = z > 72 ? 28 - (z - 72) / 3 : 38;
            for (int y = 5; y < top; y++)
                for (int x = 3; x < 43; x++) {
                    boolean side = x < 10 || x >= 36;
                    if (side && y < 16 && (arch(y, z, cy, 20, wr + 1.3f) || arch(y, z, cy, 78, wr + 1.3f))) continue;
                    v.set(x, y, z, y >= top - 2 ? ROOF : BODY);
                }
        }
        for (int zc : new int[]{20, 78}) {
            v.cylinderX(3, 10, cy, zc, wr, TYRE);
            v.cylinderX(36, 43, cy, zc, wr, TYRE);
            v.cylinderX(3, 4, cy, zc, 4f, RIM);
            v.cylinderX(42, 43, cy, zc, 4f, RIM);
        }
        v.paint(22, 24, 8, 36, 3, 4, DARK);
        v.paint(8, 20, 26, 33, 3, 4, GLASS);
        v.paint(26, 38, 26, 33, 3, 4, GLASS);
        v.paint(5, 9, 12, 24, 3, 4, LIGHT);
        v.paint(37, 41, 12, 24, 3, 4, LIGHT);
        v.box(3, 43, 5, 9, 1, 4, BLACK);
        v.box(18, 28, 9, 12, 2, 3, PLATE);
        v.paint(5, 12, 10, 14, 93, 94, HEAD);
        v.paint(34, 41, 10, 14, 93, 94, HEAD);
        v.paint(10, 36, 30, 36, 87, 94, GLASS);
        return v;
    }

    private static int[] vanPalette(int body) {
        return basePalette(body, darker(body, 1.15f));
    }

    private static Voxel ufo() {
        Voxel v = new Voxel(56, 30, 56);
        v.bodyWidth = 50;
        v.ellipsoid(28, 12, 28, 20, 4, 20, EXTRA3);
        v.ellipsoid(28, 15, 28, 25, 2.2f, 25, EXTRA4);
        v.ellipsoid(28, 17, 28, 17, 3, 17, EXTRA1);
        v.ellipsoid(28, 20, 28, 9, 7, 9, GLASS);
        v.ellipsoid(25, 24, 25, 3, 2, 3, GLASS_HI);
        for (int i = 0; i < 18; i++) {
            double a = i * Math.PI * 2 / 18;
            int x = (int) Math.round(28 + 24 * Math.cos(a)), z = (int) Math.round(28 + 24 * Math.sin(a));
            v.box(x - 1, x + 1, 14, 17, z - 1, z + 1, GLOW1 + i % 3);
        }
        return v;
    }

    private static int[] ufoPalette() {
        int[] p = basePalette(0xFFB8C2CE, 0xFFB8C2CE);
        p[EXTRA1] = 0xFFC8D0DA;
        p[EXTRA3] = 0xFF5E6672;
        p[EXTRA4] = 0xFFDCE4EC;
        p[GLASS] = 0xFF3FB4CC;
        p[GLASS_HI] = 0xFFDFFFFF;
        return p;
    }

    /** Eight frames of a rolling ball: the finger holes and the swirl turn with it. */
    private static Voxel[] balls() {
        Voxel[] f = new Voxel[8];
        float[][] holes = {{-0.3f, 0.35f, -0.89f}, {0.3f, 0.35f, -0.89f}, {0f, -0.12f, -0.99f}};
        for (int k = 0; k < 8; k++) {
            Voxel v = new Voxel(36, 36, 36);
            v.bodyWidth = 32;
            double th = k * Math.PI * 2 / 8;
            float c = (float) Math.cos(th), s = (float) Math.sin(th);
            float cx = 18, cyy = 16.5f, cz = 18, r = 16f;
            for (int z = 0; z < 36; z++)
                for (int y = 0; y < 36; y++)
                    for (int x = 0; x < 36; x++) {
                        float dx = (x + 0.5f - cx) / r, dy = (y + 0.5f - cyy) / r, dz = (z + 0.5f - cz) / r;
                        float d2 = dx * dx + dy * dy + dz * dz;
                        if (d2 > 1f) continue;
                        // undo the roll to find where this cell sits on the ball's own surface
                        float by = dy * c + dz * s, bz = -dy * s + dz * c;
                        int col = BODY;
                        if (Math.sin(dx * 7 + Math.sin(by * 5) * 2 + bz * 3) > 0.62) col = DARK;
                        if (d2 > 0.6f)
                            for (int h = 0; h < 3; h++) {
                                float[] hd = holes[h];
                                float dot = (dx * hd[0] + by * hd[1] + bz * hd[2]) / (float) Math.sqrt(d2);
                                if (dot > (h == 2 ? 0.985f : 0.989f)) col = BLACK;
                            }
                        v.set(x, y, z, col);
                    }
            f[k] = v;
        }
        return f;
    }

    private static int[] ballPalette() {
        int[] p = basePalette(0xFF1E3078, 0xFF1E3078);
        p[DARK] = 0xFF3A52B0;
        p[BLACK] = 0xFF04060C;
        return p;
    }
}
