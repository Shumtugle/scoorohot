package com.shumtugle.scoorohot;

import android.graphics.Bitmap;

import java.util.Random;

/**
 * Every picture in the game, painted procedurally once at startup.
 * A fixed seed keeps the art identical from launch to launch.
 */
final class Sprites {
    private static final int INK = 0xFF07070D;

    final Bitmap bush, rock, treeGreen, treeAutumn, cypress, lamp, chevronLeft, chevronRight, billboard;
    final Bitmap[] buildings;
    final Bitmap gateCheckpoint, gateStart, gateFinish;
    final Bitmap clouds, coastFar, coastMid, cityFar, cityMid, countryFar, countryMid, jungleFar, jungleMid;
    static final int MILL_FRAMES = 6;
    final Bitmap[] windmill = new Bitmap[MILL_FRAMES];
    final Bitmap barn, hay, pole, palm, palmB, fern, overpass, rail, marker;
    final Bitmap[] trafficLight = new Bitmap[3]; // green, yellow, red
    final Bitmap saguaro, saguaroB, barrel, redRock, routeSign, snowFir, bear, drift, acacia, giraffe, termites,
            umbrellaRed, umbrellaBlue, badge;
    final Bitmap desertFar, desertMid, taigaFar, taigaMid, safariFar, safariMid;
    final Bitmap starRing, constellation, constellationB, spaceFar, spaceMid, station;
    final Bitmap barrelFloat, crate, plank;
    final Bitmap[] gasSigns = new Bitmap[4];
    final Bitmap[] blackHole = new Bitmap[2];   // upright, with a shimmering disk
    final Bitmap[] starRings = new Bitmap[8];   // whole rings round the rabbit hole, each tilted a little more
    final Bitmap vortex;
    final Bitmap[] wingL = new Bitmap[2], wingR = new Bitmap[2]; // a pair of wings, two beats
    final Bitmap wingsItem, heart;

    Sprites() {
        Random r = new Random(1978);
        bush = bush(r);
        rock = rock(r);
        treeGreen = tree(r, 0xFF2E6E28, 0xFF4C9638, 0xFF7CC250, 0xFF143410);
        treeAutumn = tree(r, 0xFFA8401A, 0xFFDE6A1E, 0xFFF4A042, 0xFF3A1606);
        cypress = cypress(r);
        lamp = lamp();
        chevronLeft = chevron(false);
        chevronRight = chevron(true);
        billboard = billboard();
        buildings = new Bitmap[]{
                building(r, 0xFFDCE4EC, 0xFFB8C6D4, 0xFF5E8EBE, 0xFFA8D4F0),
                building(r, 0xFFCDB89E, 0xFFAA947A, 0xFF4A5A70, 0xFFF0D890),
                building(r, 0xFF9FB0C8, 0xFF8094AE, 0xFF2E4058, 0xFFCFE6F5),
                building(r, 0xFFE6D6C2, 0xFFC4B29C, 0xFF6A7A90, 0xFFBFE0F2),
        };
        gateCheckpoint = gate("CHECKPOINT", 0xFFC8302A, 0xFFFFD24A);
        gateStart = gate("START", 0xFF16161C, 0xFFF4F4F0);
        gateFinish = gate("FINISH", 0xFF16161C, 0xFFFFD24A);
        clouds = clouds(r);
        coastFar = coastFar(r);
        coastMid = coastMid(r);
        cityFar = cityFar(r);
        cityMid = cityMid(r);
        countryFar = countryFar(r);
        countryMid = countryMid(r);
        jungleFar = jungleFar(r);
        jungleMid = jungleMid(r);
        for (int i = 0; i < MILL_FRAMES; i++) windmill[i] = windmill(i, MILL_FRAMES);
        barn = barn();
        hay = hay();
        pole = pole();
        palm = palm(r);
        palmB = palm(r);
        fern = fern(r);
        overpass = overpass();
        rail = rail();
        marker = marker();
        for (int i = 0; i < 3; i++) trafficLight[i] = trafficLight(i);
        saguaro = saguaro(r, 2);
        saguaroB = saguaro(r, 1);
        barrel = barrel();
        redRock = redRock(r);
        routeSign = routeSign();
        snowFir = snowFir(r);
        bear = bear();
        drift = drift();
        acacia = acacia(r);
        giraffe = giraffe(r);
        termites = termites();
        umbrellaRed = umbrella(0xFFD8302A);
        umbrellaBlue = umbrella(0xFF2A6AD8);
        badge = badge();
        desertFar = desertFar(r);
        desertMid = desertMid(r);
        taigaFar = taigaFar(r);
        taigaMid = taigaMid(r);
        safariFar = safariFar(r);
        safariMid = safariMid(r);
        starRing = starRing(r);
        constellation = constellation(r);
        constellationB = constellation(r);
        spaceFar = spaceFar(r);
        spaceMid = spaceMid(r);
        station = station();
        barrelFloat = barrelFloat();
        crate = crate();
        plank = plank();
        gasSigns[0] = gasSign("3 KM");
        gasSigns[1] = gasSign("1 KM");
        gasSigns[2] = gasSign("300 M");
        gasSigns[3] = gasSign(">");
        for (int i = 0; i < 2; i++) blackHole[i] = blackHole(i);
        for (int i = 0; i < 8; i++) starRings[i] = starRingFull(new Random(500 + i), (i - 3.5f) * 6f);
        vortex = vortex(r);
        for (int i = 0; i < 2; i++) {
            wingL[i] = wing(i, false);
            wingR[i] = wing(i, true);
        }
        wingsItem = wingsItem();
        heart = heart();
    }

    // ----------------------------------------------------------------- cars

    // ---------------------------------------------------------------- props

    private static Bitmap bush(Random r) {
        Px p = new Px(34, 20);
        int dk = 0xFF2E7A26, md = 0xFF4E9E36, lt = 0xFF7EC452;
        p.disc(10, 13, 7, dk);
        p.disc(20, 11, 8, dk);
        p.disc(27, 14, 5, dk);
        p.disc(10, 12, 5, md);
        p.disc(19, 10, 6, md);
        p.disc(26, 13, 3, md);
        p.disc(17, 7, 2, lt);
        p.disc(8, 10, 2, lt);
        p.speckle(r, md, dk, 0.12f);
        p.outline(0xFF10300C);
        return p.bitmap();
    }

    private static Bitmap rock(Random r) {
        Px p = new Px(30, 18);
        p.ellipse(15, 11, 13, 6, 0xFF6C6A66);
        p.ellipse(13, 9, 9, 4, 0xFF8E8B85);
        p.ellipse(10, 7, 4, 2, 0xFFB4B0A8);
        p.speckle(r, 0xFF6C6A66, 0xFF5A5854, 0.15f);
        p.outline(0xFF24221E);
        return p.bitmap();
    }

    private static Bitmap tree(Random r, int dk, int md, int lt, int ink) {
        Px p = new Px(38, 52);
        p.rect(17, 30, 21, 51, 0xFF5A3A1E);
        p.rect(17, 30, 18, 51, 0xFF7C5432);
        p.disc(19, 23, 13, dk);
        p.disc(11, 26, 8, dk);
        p.disc(27, 26, 8, dk);
        p.disc(19, 18, 11, md);
        p.disc(12, 22, 7, md);
        p.disc(26, 22, 7, md);
        p.disc(15, 13, 5, lt);
        p.disc(23, 16, 3, lt);
        p.speckle(r, md, dk, 0.10f);
        p.speckle(r, md, lt, 0.06f);
        p.outline(ink);
        return p.bitmap();
    }

    private static Bitmap cypress(Random r) {
        Px p = new Px(16, 54);
        p.rect(7, 48, 9, 53, 0xFF5A3A1E);
        p.ellipse(8, 26, 6, 23, 0xFF22582A);
        p.ellipse(7, 24, 4, 19, 0xFF33783A);
        p.ellipse(6, 18, 2, 9, 0xFF4C9A4C);
        p.speckle(r, 0xFF33783A, 0xFF22582A, 0.15f);
        p.outline(0xFF0C2410);
        return p.bitmap();
    }

    private static Bitmap lamp() {
        Px p = new Px(14, 64);
        p.rect(6, 9, 8, 62, 0xFF3C424C);
        p.rect(6, 9, 7, 62, 0xFF5E6672);
        p.rect(5, 58, 9, 63, 0xFF2C3038);
        p.rect(3, 2, 11, 5, 0xFF2A2E36);
        p.rect(4, 5, 10, 9, 0xFFF4EFC8);
        p.rect(5, 6, 9, 8, 0xFFFFFFF0);
        p.outline(0xFF101218);
        return p.bitmap();
    }

    private static Bitmap chevron(boolean pointsRight) {
        Px p = new Px(26, 24);
        p.rect(6, 13, 8, 23, 0xFF6A6E76);
        p.rect(18, 13, 20, 23, 0xFF6A6E76);
        p.rect(1, 1, 25, 14, 0xFFF2F2EC);
        int red = 0xFFD02A22;
        for (int k = 0; k < 2; k++) {
            int ox = 6 + k * 8;
            for (int i = 0; i < 5; i++) {
                int x = pointsRight ? ox + i : ox + 4 - i;
                p.rect(x, 3 + i, x + 2, 4 + i, red);
                p.rect(x, 11 - i, x + 2, 12 - i, red);
            }
        }
        p.outline(0xFF141418);
        return p.bitmap();
    }

    private static Bitmap billboard() {
        Px p = new Px(58, 40);
        p.rect(12, 26, 15, 39, 0xFF4A4E56);
        p.rect(43, 26, 46, 39, 0xFF4A4E56);
        p.rect(1, 1, 57, 27, 0xFFFFB830);
        p.rect(2, 2, 56, 26, 0xFF14204A);
        int w = PixelFont.width("'78", 2);
        PixelFont.draw(p, "'78", (58 - w) / 2 + 1, 8, 2, 0xFF7A2A06);
        PixelFont.draw(p, "'78", (58 - w) / 2, 7, 2, 0xFFFFC23A);
        p.outline(0xFF0A0A12);
        return p.bitmap();
    }

    private static Bitmap building(Random r, int face, int side, int winDark, int winLit) {
        Px p = new Px(64, 120);
        p.rect(1, 4, 63, 120, face);
        p.rect(55, 4, 63, 120, side);
        p.rect(1, 1, 63, 5, side);
        for (int y = 9; y + 6 < 110; y += 9) {
            for (int x = 5; x + 5 < 54; x += 8) {
                int c = r.nextFloat() < 0.3f ? winLit : winDark;
                p.rect(x, y, x + 5, y + 6, c);
                p.rect(x, y, x + 5, y + 1, side);
            }
            p.rect(56, y, 62, y + 5, winDark);
        }
        p.rect(22, 108, 34, 120, 0xFF2A2E36);
        p.rect(24, 110, 32, 120, winDark);
        p.outline(0xFF1A1C24);
        return p.bitmap();
    }

    private static Bitmap gate(String text, int banner, int ink) {
        Px p = new Px(124, 46);
        for (int y = 1; y < 45; y++) {
            int c = ((y / 4) & 1) == 0 ? 0xFFEDEDE8 : 0xFFC8302A;
            p.rect(1, y, 7, y + 1, c);
            p.rect(117, y, 123, y + 1, c);
        }
        p.rect(1, 3, 123, 17, banner);
        for (int x = 1; x < 123; x += 3) {
            p.rect(x, 17, x + 3, 19, ((x / 3) & 1) == 0 ? 0xFF16161C : 0xFFF4F4F0);
            p.rect(x, 1, x + 3, 3, ((x / 3) & 1) == 0 ? 0xFFF4F4F0 : 0xFF16161C);
        }
        int w = PixelFont.width(text, 1);
        PixelFont.draw(p, text, (124 - w) / 2, 6, 1, ink);
        p.outline(0xFF0A0A10);
        return p.bitmap();
    }

    // ----------------------------------------------------------- backdrops

    private static final float TAU = (float) (Math.PI * 2);

    private static Bitmap clouds(Random r) {
        Px p = new Px(480, 40);
        p.wrap = true;
        int lt = 0xFFE6F3FC, sh = 0xFFB4D6F0;
        for (int i = 0; i < 10; i++) {
            int x = r.nextInt(480), y = 8 + r.nextInt(26), len = 24 + r.nextInt(56);
            p.rect(x, y, x + len, y + 3, lt);
            p.rect(x + 6, y - 2, x + len - 10, y, lt);
            if (len > 50) p.rect(x + 14, y - 4, x + len / 2, y - 2, lt);
            p.rect(x + 3, y + 3, x + len - 4, y + 4, sh);
        }
        return p.bitmap();
    }

    private static Bitmap coastFar(Random r) {
        Px p = new Px(480, 48);
        p.wrap = true;
        for (int y = 38; y < 48; y++)
            for (int x = 0; x < 480; x++) p.set(x, y, y < 41 ? 0xFF2F86CC : 0xFF1F6DB4);
        for (int i = 0; i < 160; i++) {
            int x = r.nextInt(480), y = 39 + r.nextInt(9);
            p.rect(x, y, x + 2 + r.nextInt(4), y + 1, 0xFF5FA8E0);
        }
        for (int x = 0; x < 480; x++) {
            float t = x / 480f * TAU;
            float m = (float) Math.sin(t * 2 + 0.8f);
            if (m < -0.1f) continue;
            float hgt = (m + 0.1f) * 20 + 3 * (float) Math.sin(t * 9) + 2 * (float) Math.sin(t * 17);
            int top = Math.round(38 - hgt);
            for (int y = Math.max(0, top); y < 38; y++) p.set(x, y, y < top + 2 ? 0xFF9AB8CE : 0xFF7698B4);
        }
        return p.bitmap();
    }

    private static Bitmap coastMid(Random r) {
        Px p = new Px(480, 80);
        p.wrap = true;
        for (int x = 0; x < 480; x++) {
            float t = x / 480f * TAU;
            float hgt = 48 * (float) Math.sin(t * 2 + 2.6f) + 6 * (float) Math.sin(t * 7) + 3 * (float) Math.sin(t * 13);
            if (hgt < 3) continue;
            int top = Math.round(80 - Math.min(78, hgt));
            for (int y = top; y < 80; y++) {
                float d = (y - top) / hgt;
                int c;
                if (d < 0.55f) c = ((x + y) % 5 == 0) ? 0xFF46902E : 0xFF5CAA3C;
                else c = ((x + 2 * y) % 9 < 2) ? 0xFFC4B05C : 0xFFDECC7A;
                p.set(x, y, c);
            }
            p.set(x, top, 0xFF3E8228);
        }
        for (int i = 0; i < 60; i++) {
            int x = r.nextInt(480);
            p.disc(x, 70 + r.nextInt(8), 1 + r.nextInt(2), 0xFF4E9A36);
        }
        return p.bitmap();
    }

    private static Bitmap cityFar(Random r) {
        Px p = new Px(480, 72);
        p.wrap = true;
        for (int x = 0; x < 480; x++) {
            float t = x / 480f * TAU;
            float hgt = 22 + 9 * (float) Math.sin(t * 3) + 5 * (float) Math.sin(t * 7);
            for (int y = Math.round(72 - hgt); y < 72; y++) p.set(x, y, ((x + y) % 6 == 0) ? 0xFF6E985C : 0xFF80AA6C);
        }
        int[] tones = {0xFFB5C0D4, 0xFFC8C2D0, 0xFFA4B2C8, 0xFFBCC8D8};
        int x = 0;
        while (x < 470) {
            int bw = 8 + r.nextInt(14), bh = 12 + r.nextInt(30);
            if (r.nextFloat() < 0.25f || (x > 360 && x < 410)) {
                x += bw;
                continue;
            }
            int c = tones[r.nextInt(tones.length)];
            p.rect(x, 72 - bh, x + bw, 72, c);
            for (int wy = 72 - bh + 2; wy < 70; wy += 3)
                for (int wx = x + 2; wx < x + bw - 1; wx += 3) p.set(wx, wy, 0xFFE2EAF4);
            x += bw + 1;
        }
        // observation tower
        p.rect(386, 12, 398, 72, 0xFF485878);
        for (int y = 14; y < 72; y += 4) p.rect(387, y, 397, y + 2, 0xFFD8E2EC);
        p.rect(378, 4, 406, 14, 0xFF3A4660);
        p.rect(380, 7, 404, 10, 0xFFA8D8F0);
        p.rect(391, 0, 393, 4, 0xFF3A4660);
        return p.bitmap();
    }

    private static Bitmap cityMid(Random r) {
        Px p = new Px(480, 50);
        p.wrap = true;
        int[] ridge = new int[480];
        for (int x = 0; x < 480; x++) {
            float t = x / 480f * TAU;
            float hgt = 16 + 6 * (float) Math.sin(t * 4) + 4 * (float) Math.sin(t * 9);
            ridge[x] = Math.round(50 - hgt);
            for (int y = ridge[x]; y < 50; y++) p.set(x, y, ((x * 3 + y) % 7 == 0) ? 0xFF5E8E30 : 0xFF6E9E3A);
        }
        int[] leaves = {0xFFE07A2A, 0xFFC85A20, 0xFFE8A030, 0xFF5A9A3A};
        for (int i = 0; i < 70; i++) {
            int x = r.nextInt(480);
            int c = leaves[r.nextInt(leaves.length)];
            p.disc(x, ridge[x] + 2 + r.nextInt(6), 2 + r.nextInt(4), c);
        }
        return p.bitmap();
    }
    // -------------------------------------------------------- country, jungle

    /** A post mill: brick tower, timber cap, four lattice sails; frames turn the sails a quarter round. */
    private static Bitmap windmill(int frame, int frames) {
        Px p = new Px(72, 104);
        int hx = 36, hy = 30;
        for (int y = 34; y < 103; y++) {
            float t = (y - 34) / 69f;
            int half = Math.round(9 + 9 * t);
            for (int x = hx - half; x < hx + half; x++) {
                int c = ((y / 3 + (x + (y / 3) % 2 * 2) / 4) % 7 == 0) ? 0xFFC8B89A : 0xFFE6DAC0;
                if (x >= hx + half - 3) c = 0xFFB8A888;
                p.set(x, y, c);
            }
        }
        p.rect(hx - 4, 88, hx + 4, 103, 0xFF5A3A22);
        p.rect(hx - 3, 60, hx + 3, 67, 0xFF2A3A4A);
        p.ellipse(hx, 30, 12, 8, 0xFF5A3A2A);
        p.rect(hx - 12, 30, hx + 12, 36, 0xFF4A2E20);
        double base = Math.PI / 2 / frames * frame;
        for (int k = 0; k < 4; k++) {
            double a = base + k * Math.PI / 2;
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
            for (int y = 0; y < p.h; y++)
                for (int x = 0; x < p.w; x++) {
                    float dx = x + 0.5f - hx, dy = y + 0.5f - hy;
                    float u = dx * ca + dy * sa, v = -dx * sa + dy * ca;
                    if (u < 4 || u > 33 || v < -1 || v > 7) continue;
                    boolean frame0 = v < 0.5f || v > 6f || ((int) u) % 5 == 0 || u > 32;
                    p.set(x, y, frame0 ? 0xFF6A4A2A : 0xFFF2ECDC);
                }
        }
        p.disc(hx, hy, 2.5f, 0xFF2A1A10);
        p.outline(0xFF1E140C);
        return p.bitmap();
    }

    private static Bitmap barn() {
        Px p = new Px(64, 54);
        p.rect(4, 22, 60, 53, 0xFFB8302A);
        for (int x = 6; x < 60; x += 4) p.rect(x, 24, x + 1, 53, 0xFF9A2622);
        for (int y = 2; y < 24; y++) {
            int half = y < 12 ? 14 + y : 26 + (y - 12) / 3 + 4;
            p.rect(32 - half, y, 32 + half, y + 1, y < 12 ? 0xFF3C3C44 : 0xFF2E2E36);
        }
        p.rect(22, 32, 42, 53, 0xFFEFEAE0);
        p.rect(24, 34, 40, 53, 0xFF8E2420);
        for (int i = 0; i < 16; i++) {
            p.rect(24 + i, 34 + i * 19 / 16, 25 + i, 36 + i * 19 / 16, 0xFFEFEAE0);
            p.rect(39 - i, 34 + i * 19 / 16, 40 - i, 36 + i * 19 / 16, 0xFFEFEAE0);
        }
        p.rect(28, 16, 36, 22, 0xFFEFEAE0);
        p.rect(29, 17, 35, 21, 0xFF2A2A30);
        p.outline(0xFF1A0E0C);
        return p.bitmap();
    }

    private static Bitmap hay() {
        Px p = new Px(26, 18);
        p.ellipse(13, 10, 12, 7, 0xFFD8B850);
        p.ellipse(13, 10, 8, 4.5f, 0xFFC8A440);
        p.ellipse(13, 10, 4, 2, 0xFFB89438);
        p.rect(1, 9, 25, 10, 0xFFB08E34);
        p.outline(0xFF4A3A10);
        return p.bitmap();
    }

    /** A wooden pole with a cross arm; the wires hang from the arm's ends. */
    private static Bitmap pole() {
        Px p = new Px(16, 84);
        p.rect(7, 4, 10, 83, 0xFF6A4A2A);
        p.rect(7, 4, 8, 83, 0xFF8A6A42);
        p.rect(1, 7, 15, 10, 0xFF5A3C22);
        p.rect(1, 4, 3, 7, 0xFFE8E8E0);
        p.rect(13, 4, 15, 7, 0xFFE8E8E0);
        p.outline(0xFF1A120A);
        return p.bitmap();
    }

    private static Bitmap palm(Random r) {
        Px p = new Px(48, 76);
        int topX = 26, topY = 18;
        for (int y = 75; y > topY; y--) {
            float t = (75 - y) / 57f;
            int x = Math.round(20 + (float) Math.sin(t * 1.4f) * 7);
            int c = (y / 3) % 2 == 0 ? 0xFF7A5A34 : 0xFF5E4426;
            p.rect(x - 2, y, x + 2, y + 1, c);
            if (y == topY + 1) topX = x;
        }
        float[] angles = {-2.9f, -2.4f, -1.9f, -1.3f, -0.7f, -0.2f, 0.3f};
        for (float a : angles) {
            float len = 18 + r.nextInt(5);
            for (int i = 0; i < len; i++) {
                float t = i / len;
                int x = Math.round(topX + (float) Math.cos(a) * i);
                int y = Math.round(topY + (float) Math.sin(a) * i * 0.6f + t * t * 12);
                int c = i % 4 == 0 ? 0xFF2A7A2A : 0xFF3E9A36;
                p.rect(x - 1, y - 1, x + 1, y + 1, c);
                if (i % 3 == 0) p.set(x, y + 2, 0xFF2A7A2A);
            }
        }
        p.disc(topX - 2, topY + 3, 2, 0xFF6A4A22);
        p.disc(topX + 2, topY + 3, 2, 0xFF6A4A22);
        p.outline(0xFF0E2A0E);
        return p.bitmap();
    }

    private static Bitmap fern(Random r) {
        Px p = new Px(44, 26);
        for (int k = 0; k < 9; k++) {
            float a = (float) (Math.PI + 0.15 + k * (Math.PI - 0.3) / 8);
            float len = 14 + r.nextInt(8);
            for (int i = 0; i < len; i++) {
                float t = i / len;
                int x = Math.round(22 + (float) Math.cos(a) * i);
                int y = Math.round(25 + (float) Math.sin(a) * i * 0.9f + t * t * 9);
                p.rect(x, y, x + 2, y + 1, k % 2 == 0 ? 0xFF2E7A2A : 0xFF4A9A38);
                if (i % 2 == 0) p.set(x, y + 1, 0xFF1E5A1E);
            }
        }
        p.outline(0xFF0C240C);
        return p.bitmap();
    }

    /** A concrete overpass: two piers and a deck high enough to pass under, with a direction sign. */
    private static Bitmap overpass() {
        Px p = new Px(200, 200);
        for (int x : new int[]{8, 174}) {
            p.rect(x, 30, x + 18, 200, 0xFFAAA8A0);
            p.rect(x + 13, 30, x + 18, 200, 0xFF8A8882);
            for (int y = 40; y < 200; y += 22) p.rect(x, y, x + 18, y + 1, 0xFF92908A);
        }
        p.rect(0, 10, 200, 30, 0xFFBEBCB4);
        p.rect(0, 26, 200, 30, 0xFF8A8882);
        p.rect(0, 30, 200, 34, 0x66000000);
        for (int x = 0; x < 200; x += 7) p.rect(x, 2, x + 2, 10, 0xFF9A988E);
        p.rect(0, 2, 200, 4, 0xFFB4B2AA);
        p.rect(66, 34, 134, 52, 0xFF1E6A3A);
        p.rect(68, 36, 132, 50, 0xFF247A44);
        int w = PixelFont.width("78 NORTH", 1);
        PixelFont.draw(p, "78 NORTH", 100 - w / 2, 39, 1, 0xFFF4F4F0);
        p.rect(80, 30, 82, 34, 0xFF5A5A58);
        p.rect(118, 30, 120, 34, 0xFF5A5A58);
        p.outline(0xFF2A2A28);
        return p.bitmap();
    }

    private static Bitmap rail() {
        Px p = new Px(26, 16);
        p.rect(1, 3, 4, 15, 0xFFB8B8B0);
        p.rect(22, 3, 25, 15, 0xFFB8B8B0);
        p.rect(0, 3, 26, 6, 0xFFD8D8D0);
        p.rect(0, 9, 26, 11, 0xFFC4C4BC);
        p.outline(0xFF3A3A38);
        return p.bitmap();
    }

    private static Bitmap marker() {
        Px p = new Px(8, 34);
        for (int y = 1; y < 33; y++) p.rect(3, y, 6, y + 1, ((y / 5) & 1) == 0 ? 0xFFE8E8E0 : 0xFFD0302A);
        p.outline(0xFF1A1A1A);
        return p.bitmap();
    }

    private static Bitmap countryFar(Random r) {
        Px p = new Px(480, 52);
        p.wrap = true;
        for (int x = 0; x < 480; x++) {
            float t = x / 480f * TAU;
            float hgt = 20 + 8 * (float) Math.sin(t * 3 + 1) + 5 * (float) Math.sin(t * 8);
            int top = Math.round(52 - hgt);
            for (int y = top; y < 52; y++) p.set(x, y, y < top + 2 ? 0xFF94B4B0 : 0xFF7EA0A0);
        }
        for (int i = 0; i < 14; i++) {
            int x = r.nextInt(480);
            p.rect(x, 44, x + 3, 47, 0xFFB86A5A);
            p.rect(x + 4, 42, x + 5, 47, 0xFFA8B4B0);
        }
        return p.bitmap();
    }

    /** Patchwork hills: square fields in four crops. */
    private static Bitmap countryMid(Random r) {
        Px p = new Px(480, 56);
        p.wrap = true;
        int[] crops = {0xFFC9B24A, 0xFF8CB840, 0xFFA8C25A, 0xFF6E9E38};
        for (int x = 0; x < 480; x++) {
            float t = x / 480f * TAU;
            float hgt = 22 + 10 * (float) Math.sin(t * 2 + 0.4f) + 5 * (float) Math.sin(t * 6);
            int top = Math.round(56 - hgt);
            for (int y = top; y < 56; y++) {
                int patch = ((x / 26) * 7 + ((y - top) / 7) * 3) % crops.length;
                int c = crops[patch];
                if ((x % 26 == 0) || ((y - top) % 7 == 0 && y > top)) c = 0xFF5A8A30;
                p.set(x, y, c);
            }
        }
        for (int i = 0; i < 30; i++) {
            int x = r.nextInt(480);
            p.disc(x, 30 + r.nextInt(18), 2, 0xFF3E7A2A);
        }
        return p.bitmap();
    }

    private static Bitmap jungleFar(Random r) {
        Px p = new Px(480, 64);
        p.wrap = true;
        for (int x = 0; x < 480; x++) {
            float t = x / 480f * TAU;
            float hgt = 26 + 18 * Math.abs((float) Math.sin(t * 3 + 0.5f)) + 6 * (float) Math.sin(t * 11);
            int top = Math.round(64 - Math.min(62, hgt));
            for (int y = top; y < 64; y++) p.set(x, y, y < top + 3 ? 0xFF8EB2A6 : 0xFF6E9888);
        }
        return p.bitmap();
    }

    /** The canopy line: round treetops, a few palms standing out. */
    private static Bitmap jungleMid(Random r) {
        Px p = new Px(480, 46);
        p.wrap = true;
        for (int x = 0; x < 480; x++)
            for (int y = 30; y < 46; y++) p.set(x, y, 0xFF1F5A2A);
        for (int i = 0; i < 90; i++) {
            int x = r.nextInt(480);
            int rad = 4 + r.nextInt(6);
            p.disc(x, 30 - r.nextInt(8), rad, r.nextBoolean() ? 0xFF1F5A2A : 0xFF2A6E30);
            p.disc(x - 1, 28 - r.nextInt(8), rad / 2f, 0xFF3A8A3A);
        }
        for (int i = 0; i < 8; i++) {
            int x = r.nextInt(480);
            p.rect(x, 8, x + 1, 30, 0xFF3A2E1E);
            for (int k = -6; k <= 6; k++) p.set(x + k, 8 + Math.abs(k) / 2, 0xFF2A6E30);
        }
        return p.bitmap();
    }

    /** A light on a pole: frame 0 shows green, 1 yellow, 2 red. */
    private static Bitmap trafficLight(int lit) {
        Px p = new Px(18, 74);
        p.rect(8, 22, 11, 73, 0xFF3C424C);
        p.rect(8, 22, 9, 73, 0xFF5E6672);
        p.rect(3, 1, 15, 24, 0xFF1A1A20);
        p.rect(4, 2, 14, 23, 0xFF26262E);
        int[] on = {0xFF3AE070, 0xFFFFC22A, 0xFFFF3A2A};
        int[] off = {0xFF14301E, 0xFF3A3010, 0xFF3A1210};
        int[] cy = {19, 12, 5};
        for (int k = 0; k < 3; k++) {
            p.disc(9, cy[k] + 1, 2.6f, k == lit ? on[k] : off[k]);
            if (k == lit) p.set(8, cy[k], 0xFFFFFFF0);
        }
        p.outline(0xFF0A0A0E);
        return p.bitmap();
    }
    // ------------------------------------------------------ desert, taiga, savanna

    private static Bitmap saguaro(Random r, int arms) {
        Px p = new Px(34, 70);
        int dk = 0xFF2E6A34, md = 0xFF3E8A44, lt = 0xFF5AAA58;
        p.rect(13, 6, 21, 69, md);
        p.rect(13, 6, 15, 69, lt);
        p.rect(19, 6, 21, 69, dk);
        p.disc(17, 7, 4, md);
        if (arms > 0) {
            p.rect(4, 26, 13, 31, md);
            p.rect(4, 12, 9, 31, md);
            p.disc(6.5f, 12, 2.5f, md);
            p.rect(4, 12, 5, 31, lt);
        }
        if (arms > 1) {
            p.rect(21, 34, 30, 39, md);
            p.rect(25, 18, 30, 39, md);
            p.disc(27.5f, 18, 2.5f, md);
            p.rect(29, 18, 30, 39, dk);
        }
        for (int y = 9; y < 68; y += 3) p.set(17, y, dk);
        p.outline(0xFF12301A);
        return p.bitmap();
    }

    private static Bitmap barrel() {
        Px p = new Px(18, 16);
        p.ellipse(9, 10, 7, 5.5f, 0xFF3E8A44);
        p.ellipse(7, 9, 3, 3.5f, 0xFF5AAA58);
        p.disc(9, 4, 2, 0xFFE8506A);
        p.outline(0xFF12301A);
        return p.bitmap();
    }

    private static Bitmap redRock(Random r) {
        Px p = new Px(40, 26);
        p.ellipse(20, 17, 18, 9, 0xFFA84A2E);
        p.ellipse(16, 13, 11, 7, 0xFFC8603A);
        p.ellipse(12, 10, 5, 3, 0xFFE07E4E);
        p.speckle(r, 0xFFA84A2E, 0xFF8A3A24, 0.15f);
        p.outline(0xFF3A160C);
        return p.bitmap();
    }

    private static Bitmap routeSign() {
        Px p = new Px(28, 34);
        p.rect(12, 18, 15, 33, 0xFF6A6E76);
        p.rect(3, 1, 25, 19, 0xFFF0F0EA);
        p.rect(4, 2, 24, 18, 0xFF1A1A20);
        p.rect(5, 3, 23, 17, 0xFFF0F0EA);
        int w = PixelFont.width("78", 1);
        PixelFont.draw(p, "78", 14 - w / 2, 7, 1, 0xFF1A1A20);
        p.outline(0xFF101014);
        return p.bitmap();
    }

    /** A fir in tiers, each tier capped with snow. */
    private static Bitmap snowFir(Random r) {
        Px p = new Px(34, 64);
        p.rect(15, 54, 19, 63, 0xFF4A3424);
        for (int t = 0; t < 5; t++) {
            int top = 4 + t * 10, half = 4 + t * 3;
            for (int y = 0; y < 13; y++) {
                int hw = half * y / 12 + 1;
                p.rect(17 - hw, top + y, 17 + hw, top + y + 1, y < 3 ? 0xFFF4F8FC : y % 4 == 3 ? 0xFF1A3A2A : 0xFF24503A);
            }
        }
        p.speckle(r, 0xFF24503A, 0xFFE8EEF4, 0.06f);
        p.outline(0xFF0C1C14);
        return p.bitmap();
    }

    /** A brown bear walking, seen from the side. */
    private static Bitmap bear() {
        Px p = new Px(44, 28);
        int fur = 0xFF5A3A22, dk = 0xFF3E2614, lt = 0xFF7A5232;
        p.ellipse(20, 14, 15, 8, fur);
        p.ellipse(16, 10, 9, 4, lt);
        p.ellipse(9, 9, 5, 4, fur);
        p.disc(35, 12, 6, fur);
        p.ellipse(40, 13, 3.5f, 2.5f, lt);
        p.set(42, 12, 0xFF101010);
        p.disc(33, 6, 2, dk);
        p.set(36, 10, 0xFF101010);
        p.rect(8, 19, 13, 27, dk);
        p.rect(15, 19, 20, 26, fur);
        p.rect(24, 19, 29, 27, dk);
        p.rect(30, 19, 35, 26, fur);
        p.outline(0xFF1A0E06);
        return p.bitmap();
    }

    private static Bitmap drift() {
        Px p = new Px(36, 14);
        p.ellipse(18, 10, 17, 5, 0xFFDCE6EE);
        p.ellipse(15, 8, 10, 3.5f, 0xFFFFFFFF);
        p.outline(0xFF8A9AAA);
        return p.bitmap();
    }

    /** An umbrella thorn acacia: a flat crown on a forked trunk. */
    private static Bitmap acacia(Random r) {
        Px p = new Px(60, 44);
        int bark = 0xFF5A4028;
        for (int y = 16; y < 43; y++) {
            int sp = (43 - y) / 3;
            p.rect(29 - sp, y, 32 - sp, y + 1, bark);
            if (y < 30) p.rect(29 + sp, y, 32 + sp, y + 1, bark);
        }
        p.ellipse(30, 12, 28, 6, 0xFF4A6A2A);
        p.ellipse(28, 10, 22, 4, 0xFF5E8234);
        p.speckle(r, 0xFF4A6A2A, 0xFF3A5420, 0.15f);
        p.outline(0xFF1E2A10);
        return p.bitmap();
    }

    /** A giraffe in profile: tall neck, patches, ossicones. */
    private static Bitmap giraffe(Random r) {
        Px p = new Px(36, 74);
        int coat = 0xFFE8B45A, spot = 0xFF8A5022;
        p.ellipse(16, 44, 11, 7, coat);
        for (int y = 8; y < 42; y++) {
            int x = 24 + (42 - y) / 6;
            p.rect(x - 2, y, x + 3, y + 1, coat);
        }
        p.ellipse(31, 8, 4.5f, 3, coat);
        p.rect(27, 2, 28, 5, spot);
        p.rect(30, 2, 31, 5, spot);
        p.set(31, 7, 0xFF101010);
        for (int[] l : new int[][]{{8, 50}, {12, 50}, {20, 50}, {24, 50}}) p.rect(l[0], l[1], l[0] + 3, 73, coat);
        p.rect(4, 42, 6, 54, coat);
        for (int i = 0; i < 26; i++) {
            int x = 6 + r.nextInt(22), y = 12 + r.nextInt(38);
            if (p.get(x, y) == coat) p.rect(x, y, x + 2, y + 2, spot);
        }
        p.outline(0xFF2A1406);
        return p.bitmap();
    }

    private static Bitmap termites() {
        Px p = new Px(18, 30);
        for (int y = 2; y < 29; y++) {
            int hw = 2 + (y - 2) / 4;
            p.rect(9 - hw, y, 9 + hw, y + 1, (y / 3) % 2 == 0 ? 0xFFB8703A : 0xFFA8622E);
        }
        p.outline(0xFF3A1A0A);
        return p.bitmap();
    }

    private static Bitmap umbrella(int stripe) {
        Px p = new Px(30, 30);
        p.rect(14, 8, 16, 29, 0xFFE8E8E0);
        for (int y = 0; y < 9; y++) {
            int hw = 3 + y * 12 / 8;
            for (int x = 15 - hw; x < 15 + hw; x++) p.set(x, y + 1, ((x - 15 + 30) / 4) % 2 == 0 ? stripe : 0xFFF4F4EE);
        }
        p.outline(0xFF2A2A2A);
        return p.bitmap();
    }

    /** The police star: shown for a moment before a chase, so the player knows what happened. */
    private static Bitmap badge() {
        Px p = new Px(72, 72);
        float cx = 36, cy = 36;
        for (int y = 0; y < 72; y++)
            for (int x = 0; x < 72; x++) {
                double a = Math.atan2(y - cy, x - cx), d = Math.hypot(x - cx, y - cy);
                double k = Math.cos(7 * (a + Math.PI / 2)) * 0.5 + 0.5;
                double rr = 18 + 15 * Math.pow(k, 3);
                if (d < rr) p.set(x, y, d > rr - 2.5 ? 0xFFA8781E : (x < cx ? 0xFFF0CC5A : 0xFFD8AE3A));
            }
        p.disc(cx, cy, 13, 0xFFA8781E);
        p.disc(cx, cy, 11, 0xFF1E3A8A);
        int w = PixelFont.width("78", 2);
        PixelFont.draw(p, "78", (int) cx - w / 2, (int) cy - 7, 2, 0xFFF0CC5A);
        p.outline(0xFF1A1004);
        return p.bitmap();
    }

    private static Bitmap desertFar(Random r) {
        Px p = new Px(480, 56);
        p.wrap = true;
        int x = 0;
        while (x < 480) {
            int w = 30 + r.nextInt(60), h = 14 + r.nextInt(26);
            if (r.nextFloat() < 0.35f) {
                x += w;
                continue;
            }
            for (int y = 56 - h; y < 56; y++) {
                int inset = y < 56 - h + 3 ? 0 : Math.max(0, (y - (56 - h)) / 5);
                p.rect(x - inset, y, x + w + inset, y + 1, y < 56 - h + 2 ? 0xFFE08A5A : (y / 4) % 3 == 0 ? 0xFFA84E34 : 0xFFC0603E);
            }
            x += w + 10 + r.nextInt(30);
        }
        return p.bitmap();
    }

    private static Bitmap desertMid(Random r) {
        Px p = new Px(480, 36);
        p.wrap = true;
        for (int x = 0; x < 480; x++) {
            float t = x / 480f * TAU;
            int top = Math.round(36 - (10 + 5 * (float) Math.sin(t * 5) + 3 * (float) Math.sin(t * 11)));
            for (int y = top; y < 36; y++) p.set(x, y, y < top + 2 ? 0xFFF0B070 : 0xFFE0A060);
        }
        for (int i = 0; i < 14; i++) {
            int x = r.nextInt(480), y = 16 + r.nextInt(10);
            p.rect(x, y, x + 2, y + 9, 0xFF4A7A3A);
            p.rect(x - 2, y + 3, x, y + 4, 0xFF4A7A3A);
            p.rect(x - 2, y + 1, x - 1, y + 3, 0xFF4A7A3A);
        }
        return p.bitmap();
    }

    private static Bitmap taigaFar(Random r) {
        Px p = new Px(480, 60);
        p.wrap = true;
        for (int x = 0; x < 480; x++) {
            float t = x / 480f * TAU;
            float h = 26 + 16 * Math.abs((float) Math.sin(t * 2.5f + 1)) + 6 * (float) Math.sin(t * 9);
            int top = Math.round(60 - Math.min(58, h));
            for (int y = top; y < 60; y++) p.set(x, y, y < top + 8 ? 0xFFF4F8FC : 0xFF8C9CB4);
        }
        return p.bitmap();
    }

    private static Bitmap taigaMid(Random r) {
        Px p = new Px(480, 40);
        p.wrap = true;
        for (int x = 0; x < 480; x++)
            for (int y = 30; y < 40; y++) p.set(x, y, 0xFF1E3A2C);
        for (int i = 0; i < 110; i++) {
            int x = r.nextInt(480), h = 10 + r.nextInt(16);
            for (int y = 0; y < h; y++) {
                int hw = y * 4 / h + 1;
                p.rect(x - hw, 30 - h + y, x + hw, 31 - h + y, y < 2 ? 0xFFEAF0F6 : 0xFF1E3A2C);
            }
        }
        return p.bitmap();
    }

    private static Bitmap safariFar(Random r) {
        Px p = new Px(480, 52);
        p.wrap = true;
        for (int x = 0; x < 480; x++) {
            float d = Math.abs(((x + 480 - 300) % 480) - 0f);
            float h = Math.max(0f, 40f - d * 0.28f) + 3 * (float) Math.sin(x * 0.05f);
            int top = Math.round(52 - Math.max(4f, h));
            for (int y = top; y < 52; y++) p.set(x, y, h > 30 && y < top + 5 ? 0xFFF0F2F4 : 0xFF8A9AA8);
        }
        return p.bitmap();
    }

    private static Bitmap safariMid(Random r) {
        Px p = new Px(480, 34);
        p.wrap = true;
        for (int x = 0; x < 480; x++)
            for (int y = 28; y < 34; y++) p.set(x, y, 0xFFC8A858);
        for (int i = 0; i < 16; i++) {
            int x = r.nextInt(480), h = 8 + r.nextInt(8);
            p.rect(x, 28 - h, x + 1, 28, 0xFF3A2E1E);
            p.ellipse(x, 28 - h, 7 + r.nextInt(4), 2, 0xFF3E5A2A);
        }
        return p.bitmap();
    }

    // ------------------------------------------------------------------ space

    /** An arch of stars joined by thin lines: a ring of the rabbit hole the car flies through. */
    private static Bitmap starRing(Random r) {
        Px p = new Px(160, 104);
        int n = 15;
        int[] xs = new int[n], ys = new int[n];
        for (int i = 0; i < n; i++) {
            double a = Math.PI + Math.PI * i / (n - 1);
            xs[i] = (int) Math.round(80 + 76 * Math.cos(a) + r.nextInt(5) - 2);
            ys[i] = (int) Math.round(100 + 96 * Math.sin(a) + r.nextInt(5) - 2);
        }
        for (int i = 0; i + 1 < n; i++) {
            int steps = Math.max(Math.abs(xs[i + 1] - xs[i]), Math.abs(ys[i + 1] - ys[i]));
            for (int k = 0; k <= steps; k++) {
                int x = xs[i] + (xs[i + 1] - xs[i]) * k / Math.max(1, steps), y = ys[i] + (ys[i + 1] - ys[i]) * k / Math.max(1, steps);
                if (k % 2 == 0) p.set(x, y, 0xFF5A6AC8);
            }
        }
        for (int i = 0; i < n; i++) {
            int c = i % 3 == 0 ? 0xFFFFF4C0 : i % 3 == 1 ? 0xFFC8E8FF : 0xFFFFC8F0;
            p.rect(xs[i] - 1, ys[i] - 1, xs[i] + 2, ys[i] + 2, c);
            p.set(xs[i], ys[i] - 2, c);
            p.set(xs[i], ys[i] + 2, c);
            p.set(xs[i] - 2, ys[i], c);
            p.set(xs[i] + 2, ys[i], c);
        }
        return p.bitmap();
    }

    private static Bitmap constellation(Random r) {
        Px p = new Px(54, 64);
        int n = 6 + r.nextInt(3);
        int px = 27, py = 60;
        for (int i = 0; i < n; i++) {
            int nx = Math.max(3, Math.min(50, px + r.nextInt(25) - 12)), ny = Math.max(3, py - 6 - r.nextInt(9));
            int steps = Math.max(Math.abs(nx - px), Math.abs(ny - py));
            for (int k = 0; k <= steps; k += 2) p.set(px + (nx - px) * k / Math.max(1, steps), py + (ny - py) * k / Math.max(1, steps), 0xFF4A5AA8);
            p.rect(nx - 1, ny - 1, nx + 2, ny + 2, 0xFFF4F0FF);
            px = nx;
            py = ny;
        }
        return p.bitmap();
    }

    private static Bitmap spaceFar(Random r) {
        Px p = new Px(480, 70);
        p.wrap = true;
        for (int i = 0; i < 60; i++) {
            float cx = r.nextInt(480), cy = 10 + r.nextInt(50), rr = 6 + r.nextInt(14);
            int c = r.nextBoolean() ? 0x302A0A6A : 0x30601A80;
            for (int y = (int) (cy - rr); y < cy + rr; y++)
                for (int x = (int) (cx - rr); x < cx + rr; x++)
                    if ((x - cx) * (x - cx) + (y - cy) * (y - cy) < rr * rr && p.get(x, y) == 0) p.set(x, y, c | 0x50000000);
        }
        for (int i = 0; i < 160; i++) p.set(r.nextInt(480), r.nextInt(70), i % 4 == 0 ? 0xFFFFF4D0 : 0xC0C8D8FF);
        p.disc(120, 30, 9, 0xFFC86A4A);
        p.ellipse(120, 30, 16, 2, 0xFFE8B080);
        p.disc(118, 28, 3, 0xFFE89A6A);
        p.disc(380, 44, 5, 0xFF4A8AD8);
        return p.bitmap();
    }

    private static Bitmap spaceMid(Random r) {
        Px p = new Px(480, 30);
        p.wrap = true;
        for (int x = 0; x < 480; x++) {
            int h = 4 + (int) (3 * Math.sin(x * 0.04) + 2 * Math.sin(x * 0.13));
            for (int y = 30 - h; y < 30; y++) if (r.nextFloat() < 0.4f) p.set(x, y, r.nextBoolean() ? 0xA06A4AC8 : 0xA0C86AE8);
        }
        return p.bitmap();
    }

    /** A gas station: a canopy on two posts over the pumps, a shop behind, a tall sign with the price. */
    private static Bitmap station() {
        Px p = new Px(120, 84);
        p.rect(54, 30, 112, 80, 0xFFE8E2D4);
        p.rect(54, 30, 112, 34, 0xFFC8302A);
        p.rect(60, 46, 80, 80, 0xFF2A3A4A);
        p.rect(86, 46, 106, 66, 0xFF6A9AC8);
        p.rect(4, 22, 70, 30, 0xFFF0EAE0);
        p.rect(4, 30, 70, 34, 0xFFC8302A);
        p.rect(10, 34, 14, 80, 0xFFB8B8B0);
        p.rect(60, 34, 64, 80, 0xFFB8B8B0);
        for (int x : new int[]{22, 42}) {
            p.rect(x, 56, x + 8, 80, 0xFFD8D4C8);
            p.rect(x + 1, 58, x + 7, 63, 0xFF1A2A3A);
            p.rect(x + 2, 66, x + 6, 70, 0xFFC8302A);
        }
        p.rect(112, 4, 116, 80, 0xFF8A8A84);
        p.rect(100, 2, 120, 26, 0xFF1A1A20);
        int w = PixelFont.width("GAS", 1);
        PixelFont.draw(p, "GAS", 110 - w / 2, 5, 1, 0xFFFFC22A);
        w = PixelFont.width("78", 1);
        PixelFont.draw(p, "78", 110 - w / 2, 15, 1, 0xFFF0F0EA);
        for (int x = 4; x < 120; x += 6) p.rect(x, 80, x + 3, 84, 0xFFFFC22A);
        p.outline(0xFF101216);
        return p.bitmap();
    }

    /** A green sign over the road at a fork: where each road goes, made fresh for every fork. */
    Bitmap sign(String left, String right) {
        Px p = new Px(150, 44);
        p.rect(4, 14, 7, 44, 0xFF8A8E94);
        p.rect(143, 14, 146, 44, 0xFF8A8E94);
        p.rect(0, 0, 150, 26, 0xFFF0F0EA);
        p.rect(2, 2, 148, 24, 0xFF1E6A3A);
        PixelFont.draw(p, left, 7, 9, 1, 0xFFF0F0EA);
        PixelFont.draw(p, right, 143 - PixelFont.width(right, 1), 9, 1, 0xFFF0F0EA);
        p.rect(74, 4, 76, 22, 0xFFF0F0EA);
        p.outline(0xFF0E1A12);
        return p.bitmap();
    }

    /** Flotsam in the flooded streets. */
    private static Bitmap barrelFloat() {
        Px p = new Px(20, 18);
        p.rect(3, 4, 17, 16, 0xFFC8302A);
        p.rect(3, 7, 17, 8, 0xFF8A1A14);
        p.rect(3, 12, 17, 13, 0xFF8A1A14);
        p.ellipse(10, 4, 7, 2, 0xFFE8584A);
        p.rect(0, 15, 20, 18, 0x90E8F4F8);
        p.outline(0xFF2A0A08);
        return p.bitmap();
    }

    private static Bitmap crate() {
        Px p = new Px(24, 18);
        p.rect(2, 2, 22, 15, 0xFFB8884A);
        for (int y = 2; y < 15; y += 4) p.rect(2, y, 22, y + 1, 0xFF8A6430);
        p.rect(2, 2, 4, 15, 0xFF8A6430);
        p.rect(20, 2, 22, 15, 0xFF8A6430);
        p.rect(0, 14, 24, 18, 0x90E8F4F8);
        p.outline(0xFF2A1A08);
        return p.bitmap();
    }

    private static Bitmap plank() {
        Px p = new Px(34, 10);
        p.rect(1, 3, 33, 7, 0xFF9A7A4A);
        p.rect(1, 3, 33, 4, 0xFFC8A06A);
        p.rect(0, 6, 34, 10, 0x90E8F4F8);
        p.outline(0xFF2A1A08);
        return p.bitmap();
    }

    /** A blue roadside sign with a pump and the distance to the next gas station. */
    private static Bitmap gasSign(String text) {
        boolean turn = text.equals(">");
        int w = turn ? 44 : 36, h = turn ? 52 : 46;
        Px p = new Px(w, h);
        p.rect(w / 2 - 1, 26, w / 2 + 2, h, 0xFF7A7E86);
        p.rect(0, 0, w, 28, 0xFFF0F0EA);
        p.rect(1, 1, w - 1, 27, 0xFF1E4AA8);
        // the pump
        p.rect(4, 5, 11, 19, 0xFFF0F0EA);
        p.rect(5, 7, 10, 11, 0xFF1E4AA8);
        p.rect(11, 8, 13, 9, 0xFFF0F0EA);
        p.rect(12, 9, 13, 16, 0xFFF0F0EA);
        PixelFont.draw(p, "GAS", 15, 5, 1, 0xFFFFD24A);
        if (turn) PixelFont.draw(p, ">", w - 10, 13, 2, 0xFFF0F0EA);
        else PixelFont.draw(p, text, w / 2 - PixelFont.width(text, 1) / 2, 18, 1, 0xFFF0F0EA);
        p.outline(0xFF0A1630);
        return p.bitmap();
    }

    /**
     * A black hole standing upright over the road: a black core in a thin bright
     * ring, a flat disk of glowing matter seen edge-on across it (in front below,
     * bent up behind above), and a cross of light. Frame decides the shimmer.
     */
    private static Bitmap blackHole(int frame) {
        int W = 140, H = 120;
        Px p = new Px(W, H);
        float cx = W / 2f, cy = H / 2f;
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                float dx = x - cx, dy = y - cy;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                int c = 0;
                // the disk bent up over the top of the core by the gravity of it
                float halo = Math.abs(d - 27f);
                if (halo < 4f && dy < 2) c = halo < 1.6f ? 0xFFFFE8B0 : 0xFFE0782A;
                // the flat disk, edge on, wider than everything
                float de = (dx * dx) / (64f * 64f) + (dy * dy) / (7f * 7f);
                if (de < 1f) {
                    float t = 1f - de;
                    int band = ((int) (Math.abs(dx) / 5f + frame)) % 3;
                    c = t > 0.6f ? 0xFFFFF0C8 : band == 0 ? 0xFFF0A040 : 0xFFD0602A;
                }
                // the core and its photon ring
                if (d < 22f) c = d > 20f ? 0xFFFFF4D8 : 0xFF050308;
                // the disk passes in front of the core below its middle
                if (de < 1f && dy > 0 && d < 22f) c = 0xFFF0A040;
                // the cross of light
                if (c == 0) {
                    float fx = Math.max(0f, 1f - Math.abs(dy) / 1.2f) * Math.max(0f, 1f - Math.abs(dx) / 70f);
                    float fy = Math.max(0f, 1f - Math.abs(dx) / 1.2f) * Math.max(0f, 1f - Math.abs(dy) / 60f);
                    float f = Math.max(fx, fy);
                    if (f > 0.15f) c = ((int) (Math.min(1f, f * 1.4f) * 255) << 24) | 0xFFD890;
                }
                if (c != 0) p.set(x, y, c);
            }
        return p.bitmap();
    }

    /** A whole ring of stars joined by dotted lines, tilted by a few degrees. */
    private static Bitmap starRingFull(Random r, float tiltDeg) {
        Px p = new Px(170, 170);
        int n = 22;
        double tilt = Math.toRadians(tiltDeg);
        int[] xs = new int[n], ys = new int[n];
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            double x = 80 * Math.cos(a), y = 80 * Math.sin(a);
            xs[i] = (int) Math.round(85 + x * Math.cos(tilt) - y * Math.sin(tilt)) + r.nextInt(3) - 1;
            ys[i] = (int) Math.round(85 + x * Math.sin(tilt) + y * Math.cos(tilt)) + r.nextInt(3) - 1;
        }
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            int steps = Math.max(Math.abs(xs[j] - xs[i]), Math.abs(ys[j] - ys[i]));
            for (int k = 0; k <= steps; k += 3) p.set(xs[i] + (xs[j] - xs[i]) * k / Math.max(1, steps), ys[i] + (ys[j] - ys[i]) * k / Math.max(1, steps), 0xFF6A7AD8);
        }
        for (int i = 0; i < n; i++) {
            int c = i % 3 == 0 ? 0xFFFFF4C0 : i % 3 == 1 ? 0xFFC8E8FF : 0xFFFFC8F0;
            p.rect(xs[i] - 1, ys[i] - 1, xs[i] + 2, ys[i] + 2, c);
            if (i % 2 == 0) {
                p.set(xs[i], ys[i] - 2, c);
                p.set(xs[i], ys[i] + 2, c);
                p.set(xs[i] - 2, ys[i], c);
                p.set(xs[i] + 2, ys[i], c);
            }
        }
        return p.bitmap();
    }

    /** The whirl the rabbit hole runs into: spiral arms round a black eye. */
    private static Bitmap vortex(Random r) {
        int W = 200, H = 140;
        Px p = new Px(W, H);
        float cx = W / 2f, cy = H / 2f;
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                float dx = (x - cx) / 1.25f, dy = y - cy;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d > 68f) continue;
                if (d < 9f) {
                    p.set(x, y, 0xFF020104);
                    continue;
                }
                double a = Math.atan2(dy, dx) + d * 0.09;
                float arm = (float) Math.cos(a * 3);
                float fade = 1f - d / 68f;
                if (arm > 0.35f) {
                    int c = d < 20 ? 0xFFE0D0FF : d < 40 ? 0xFF8A6AE8 : 0xFF4A3AA8;
                    int alpha = (int) (Math.min(1f, fade * 1.6f) * 255);
                    p.set(x, y, (alpha << 24) | (c & 0xFFFFFF));
                } else if (d < 14f) {
                    p.set(x, y, 0xFF2A1A5A);
                }
            }
        for (int i = 0; i < 40; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = 12 + r.nextDouble() * 50;
            int x = (int) (cx + Math.cos(a) * d * 1.25), y = (int) (cy + Math.sin(a) * d);
            p.set(x, y, 0xFFFFF4E0);
        }
        return p.bitmap();
    }

    /** One wing, feathered, white with a gold edge; beat 1 is lifted. The root is at the right. */
    private static Px wingPx(int beat) {
        int W = 60, H = 36;
        Px p = new Px(W, H);
        float lift = beat == 0 ? 0f : 7f;
        for (int f = 0; f < 6; f++) {
            // feathers fan out from the root at the car's side
            float len = 54 - f * 6, ang = 0.10f + f * 0.16f;
            for (int t = 0; t < len; t++) {
                float x = W - 2 - t * (float) Math.cos(ang) * (1f + f * 0.04f);
                float y = H - 8 - t * (float) Math.sin(ang) * 0.55f - lift * t / len + f * 1.4f;
                int th = Math.max(2, 6 - f / 2);
                for (int k = 0; k < th; k++) {
                    int c = t > len - 4 ? 0xFFE8B850 : (f % 2 == 0 ? 0xFFF8F6F0 : 0xFFE6E2D8);
                    p.set(Math.round(x), Math.round(y) + k, c);
                }
            }
        }
        p.outline(0xFF6A5A3A);
        return p;
    }

    private static Bitmap wing(int beat, boolean right) {
        Px p = wingPx(beat);
        if (!right) return p.bitmap();
        Px m = new Px(60, 36);
        for (int y = 0; y < 36; y++) for (int x = 0; x < 60; x++) m.set(59 - x, y, p.get(x, y));
        return m.bitmap();
    }

    /** The wings lying on the road to be picked up: a pair, spread. */
    private static Bitmap wingsItem() {
        Px w = wingPx(1);
        Px p = new Px(120, 36);
        for (int y = 0; y < 36; y++)
            for (int x = 0; x < 60; x++) {
                int c = w.get(x, y);
                if (c == 0) continue;
                p.set(x, y, c);
                p.set(119 - x, y, c);
            }
        return p.bitmap();
    }

    /** A red heart with a white glint, for the road's edge. */
    private static Bitmap heart() {
        Px p = new Px(22, 20);
        for (int y = 0; y < 20; y++)
            for (int x = 0; x < 22; x++) {
                float u = (x - 10.5f) / 10f, v = (9f - y) / 9f;
                float q = u * u + v * v - 1f;
                if (q * q * q - u * u * v * v * v < 0) p.set(x, y, y < 8 ? 0xFFFF4A5A : 0xFFD8263A);
            }
        p.rect(5, 4, 8, 7, 0xFFFFE8EC);
        p.outline(0xFF4A0A12);
        return p.bitmap();
    }
}
