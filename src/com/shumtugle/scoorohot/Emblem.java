package com.shumtugle.scoorohot;

import android.graphics.Bitmap;

/**
 * The game's coat of arms, deliberately lopsided: a shield leaning to the
 * right with its point off to the left, a setting sun cut by its edge, dark
 * hills, a road coming in from the lower right corner, and a large 78. The
 * menu sets the chosen car on it; the launcher icon is the same shield.
 */
final class Emblem {
    static final int W = 42, H = 48;

    private Emblem() {}

    // the outline, clockwise: the right edge is shorter and leans, the point is off center
    private static final float[][] SHIELD = {{2, 4}, {40, 1}, {38, 30}, {16, 47}, {2, 30}};

    static Bitmap make() {
        Px p = new Px(W, H);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                float d = inside(x + 0.5f, y + 0.5f);
                if (d < 0) continue;
                p.set(x, y, d < 2.2f ? (d < 1f ? 0xFFF4C860 : 0xFFC8902E) : field(x, y));
            }
        int w = PixelFont.width("78", 2);
        PixelFont.draw(p, "78", 6, 8, 2, 0xFF3A1A06);
        PixelFont.draw(p, "78", 5, 7, 2, 0xFFFFD86A);
        p.rect(5, 7, 5 + w, 8, 0xFFFFF0B0);
        p.outline(0xFF0E0A08);
        return p.bitmap();
    }

    /** What is painted inside the shield at one pixel: sky, sun, hills, road. */
    private static int field(int x, int y) {
        // the road: from the lower right corner away to a point on the horizon left of center
        float t = (y - 27f) / 20f;
        if (t > 0) {
            float cx = 15 + 19 * t, half = 1.2f + 9 * t;
            if (Math.abs(x - cx) < half) {
                boolean dash = Math.abs(x - cx) < 0.6f + 0.6f * t && ((int) (t * 9)) % 2 == 0;
                return dash ? 0xFFF0E6C8 : (Math.abs(x - cx) > half - 1.2f ? 0xFFC8302A : 0xFF2C2A30);
            }
            return (x + y) % 5 == 0 ? 0xFF5E6E34 : 0xFF6E7E3A;
        }
        // hills along the horizon
        float ridge = 27 - 3 * (float) Math.abs(Math.sin(x * 0.28f)) - (x > 24 ? 2 : 0);
        if (y >= ridge) return 0xFF34303E;
        // the sun, low and big, cut by the right edge
        float dx = x - 35, dy = y - 19;
        if (dx * dx + dy * dy < 72) return dy < -2 ? 0xFFFFE07A : 0xFFFFB040;
        // a sky going from night at the top to orange at the hills
        float k = y / 27f;
        return mix(0xFF1A2440, 0xFFF2A24A, k * k);
    }

    /** Distance inside the shield outline, negative outside. */
    private static float inside(float x, float y) {
        float best = Float.MAX_VALUE;
        boolean in = false;
        int n = SHIELD.length;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            float xi = SHIELD[i][0], yi = SHIELD[i][1], xj = SHIELD[j][0], yj = SHIELD[j][1];
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) in = !in;
            float ex = xj - xi, ey = yj - yi, len = ex * ex + ey * ey;
            float u = Math.max(0f, Math.min(1f, ((x - xi) * ex + (y - yi) * ey) / len));
            float qx = xi + u * ex - x, qy = yi + u * ey - y;
            best = Math.min(best, (float) Math.sqrt(qx * qx + qy * qy));
        }
        return in ? best : -1f;
    }

    private static int mix(int a, int b, float t) {
        return Skin.mix(a, b, t);
    }
}
