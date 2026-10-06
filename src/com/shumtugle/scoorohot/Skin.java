package com.shumtugle.scoorohot;

import android.graphics.Bitmap;

/**
 * A skin is two things: the colors of every screen and control, and a filter
 * that redraws each rendered frame of the world in another manner. Dusk is the
 * game's own look; arcade comes with a police chase, neon with a black hole,
 * rainbow with a puddle. Filters
 * work on the finished pixels (their brightness, their edges, their hue), so
 * every road, car and tree in the game gets the style without being drawn twice.
 */
final class Skin {
    // the order of the color table
    static final int BG = 0, GRID = 1, STAR = 2, STAR_DIM = 3, FRAME = 4, FRAME_ALT = 5, PANEL = 6,
            BTN_EDGE = 7, BTN_INNER = 8, BTN_FILL = 9, BTN_TOP = 10, TEXT = 11, DIM = 12, ACCENT = 13, FAINT = 14,
            SHADOW = 15, G_FILL = 16, G_RIM = 17, G_TICK = 18, G_NEEDLE = 19, G_FLOW = 20, G_FLOW_HOT = 21,
            G_RED = 22, G_TRACK = 23, G_HUB = 24, TITLE_INK = 25, TITLE_SHADOW = 26, VEIL = 27;
    static final int NONE = 0, NEON_FILTER = 3, DUSK_FILTER = 4, RAINBOW_FILTER = 5;
    // positions in ALL: dusk is the game's own look; the others are states the world falls into
    static final int DUSK = 0, ARCADE = 1, NEON = 2, RAINBOW = 3;

    final String name;
    final int filter;
    final int[] c;
    final int[] title; // seven rows of the title letters, top to bottom

    private static final float[] COS_X = new float[1024], SIN_X = new float[1024];

    static {
        for (int x = 0; x < 1024; x++) {
            COS_X[x] = (float) Math.cos(x * 0.03);
            SIN_X[x] = (float) Math.sin(x * 0.03);
        }
    }

    // the dusk grade depends on brightness only, so it is a table
    private static final int[] DUSK_GRADE = new int[256];

    static {
        for (int i = 0; i < 256; i++) {
            float t = i / 255f;
            DUSK_GRADE[i] = mix(0xFF1C3A40, 0xFFF2A24A, t * t * (3 - 2 * t));
        }
    }

    private int[] buf = new int[0];
    private int[] lum = new int[0];

    private Skin(String name, int filter, int[] title, int... colors) {
        this.name = name;
        this.filter = filter;
        this.title = title;
        this.c = colors;
    }

    static final Skin[] ALL = {
            new Skin("DUSK", DUSK_FILTER,
                    new int[]{0xFFFFE08A, 0xFFFFC660, 0xFFFFA840, 0xFFF48A30, 0xFFE06A24, 0xFFC04E1C, 0xFF923614},
                    0xFF141E26, 0xFF22323C, 0xFF6A7C84, 0xFF2A3A44, 0xFFE0902A, 0xFFFFC24A, 0xFF18242C,
                    0xFF8AA0A8, 0xFF141E26, 0xFF24343E, 0xFF34484E, 0xFFEDE2CC, 0xFF9AA8AC, 0xFFE8A040, 0xFF3A4C54,
                    0xC0000000, 0xFF1E2A30, 0xFFC0A070, 0xFFEDE2CC, 0xFFE8602A, 0xFFE8A040, 0xFFFFC24A,
                    0xFFC04A2A, 0xFF34404A, 0xFF9AA8AC, 0xFF2A1004, 0xFF0A0400, 0xA0060A0C),
            new Skin("ARCADE", NONE,
                    new int[]{0xFFFFEC9A, 0xFFFFD85A, 0xFFFFBE34, 0xFFFFA020, 0xFFF47E14, 0xFFD85A0C, 0xFFA83A08},
                    0xFF0A1026, 0xFF13234C, 0xFF7C8CC0, 0xFF2C3A6A, 0xFFFFD24A, 0xFFFFB020, 0xFF101A3A,
                    0xFF8FB4F0, 0xFF0A1026, 0xFF1A2852, 0xFF2A3C70, 0xFFF4F4F0, 0xFF8FA6D8, 0xFFF2D27A, 0xFF34477A,
                    0xC0000000, 0xFF1A2238, 0xFFA8B4D0, 0xFFEDEDF2, 0xFFF0402E, 0xFFFF7A1A, 0xFFFFB830,
                    0xFFE0302A, 0xFF3A2C3C, 0xFF9AA0AE, 0xFF3A1404, 0xFF1A0800, 0xA0000000),
            new Skin("NEON NIGHT", NEON_FILTER,
                    new int[]{0xFFFFD27A, 0xFFFFB05A, 0xFFFF8A4A, 0xFFFF6A5A, 0xFFF4508A, 0xFFE03AA8, 0xFFB82AB8},
                    0xFF12061E, 0xFF34124A, 0xFFB050D8, 0xFF3A1A50, 0xFFFF4FD8, 0xFFFF9A3A, 0xFF1C0A2C,
                    0xFFFF4FD8, 0xFF12061E, 0xFF2A0C3A, 0xFF4A1A5E, 0xFFFFD8F4, 0xFFB070D0, 0xFFFF9A3A, 0xFF5A2A70,
                    0xC0000000, 0xFF1A0828, 0xFFFF4FD8, 0xFFFFD8F4, 0xFF3FE8FF, 0xFFFF9A3A, 0xFFFFD27A,
                    0xFFFF3A8A, 0xFF3A1A4A, 0xFFB070D0, 0xFF2A0418, 0xFF000000, 0xB0060010),
            new Skin("RAINBOW", RAINBOW_FILTER,
                    new int[]{0xFFFF5A5A, 0xFFFF9A3A, 0xFFFFE04A, 0xFF6AE06A, 0xFF4AC8FF, 0xFF7A6AFF, 0xFFD06AFF},
                    0xFF3A2A8A, 0xFF5A48B4, 0xFFFFFFFF, 0xFF8A7AD8, 0xFFFFB830, 0xFFFF5AA0, 0xFF2A1A6A,
                    0xFFFFFFFF, 0xFF2A1A6A, 0xFF4A3AA8, 0xFF6A5AC8, 0xFFFFFFFF, 0xFFE0D0FF, 0xFFFFE04A, 0xFF7A6AD0,
                    0xC0100030, 0xFF2A1A6A, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFF5A5A, 0xFF6AE06A, 0xFFFFE04A,
                    0xFFFF5AA0, 0xFF4A3AA8, 0xFFFFFFFF, 0xFF1A0A3A, 0xFF000000, 0xA0100030),
    };

    // -------------------------------------------------------------- filters

    /** Redraws a rendered frame in this skin's manner. horizon is a share of the height. */
    void apply(Bitmap b, float time, float horizon) {
        if (filter == NONE) return;
        int w = b.getWidth(), h = b.getHeight(), n = w * h;
        if (buf.length < n) {
            buf = new int[n];
            lum = new int[n];
        }
        int[] px = buf, l = lum;
        b.getPixels(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < n; i++) {
            int p = px[i];
            l[i] = (((p >> 16) & 255) * 77 + ((p >> 8) & 255) * 150 + (p & 255) * 29) >> 8;
        }
        int hy = (int) (h * horizon);
        int frame = (int) (time * 12);
        double ca = 0, sa = 0;
        for (int y = 0; y < h; y++) {
            if (filter == RAINBOW_FILTER) {
                double a = y * 0.08 + time * 2.2;
                ca = Math.cos(a);
                sa = Math.sin(a);
            }
            // how much of the horizon glow this row gets
            float k = y < hy ? (float) y / hy : 0f;
            float glow = filter == NEON_FILTER ? k * k * k * 0.55f : k * k * 0.35f;
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                int e = 0;
                if (x > 0 && y > 0 && x < w - 1 && y < h - 1) {
                    int gx = l[i + 1] - l[i - 1], gy = l[i + w] - l[i - w];
                    e = (gx < 0 ? -gx : gx) + (gy < 0 ? -gy : gy);
                }
                int p = px[i];
                int r = (p >> 16) & 255, g = (p >> 8) & 255, bl = p & 255, L = l[i];
                switch (filter) {
                    case NEON_FILTER:
                        px[i] = neon(r, g, bl, L, e, y, glow);
                        break;
                    case DUSK_FILTER:
                        px[i] = dusk(r, g, bl, L, y, glow, x, frame);
                        break;
                    default:
                        px[i] = rainbow(r, g, bl, e, x, ca, sa);
                        break;
                }
            }
        }
        b.setPixels(px, 0, w, 0, 0, w, h);
    }

    /** Neon night: a dark violet world outlined in pink and cyan light, a glow at the horizon. */
    private static int neon(int r, int g, int b, int L, int e, int y, float glow) {
        int out = 0xFF000000 | ((14 + r * 22 / 100) << 16) | ((4 + g * 8 / 100) << 8) | (26 + b * 38 / 100);
        if (glow > 0.01f) out = mix(out, 0xFFFF3A9A, glow);
        if (e > 45) out = mix(out, r > b ? 0xFFFF46C8 : 0xFF46E6FF, Math.min(1f, (e - 45) / 110f));
        if (L > 205) out = mix(out, 0xFFFFB4F0, 0.85f);
        if (r > 180 && g < 110 && b < 110) out = 0xFFFF3A5A;
        if ((y & 1) == 1) out = mix(out, 0xFF000000, 0.12f);
        return out;
    }

    /** Dusk: shadows go teal, light goes orange; a little film grain. */
    private static int dusk(int r, int g, int b, int L, int y, float glow, int x, int frame) {
        int out = mix(0xFF000000 | (r << 16) | (g << 8) | b, DUSK_GRADE[L], 0.62f);
        if (glow > 0.01f) out = mix(out, 0xFFFFB060, glow);
        int hsh = (x * 73856093) ^ (y * 19349663) ^ (frame * 83492791);
        int grain = ((hsh >>> 13) & 15) - 8;
        int rr = Math.max(0, Math.min(255, ((out >> 16) & 255) + grain));
        int gg = Math.max(0, Math.min(255, ((out >> 8) & 255) + grain));
        int bb = Math.max(0, Math.min(255, (out & 255) + grain));
        return 0xFF000000 | (rr << 16) | (gg << 8) | bb;
    }

    /** Rainbow: hue turns with height and time, and even the grey road takes color. */
    private static int rainbow(int r, int g, int b, int e, int x, double ca, double sa) {
        if (e > 90) return 0xFF2A1A5A;
        float cx = COS_X[x & 1023], sx = SIN_X[x & 1023];
        float c = (float) (ca * cx - sa * sx), s = (float) (sa * cx + ca * sx);
        float yy = 0.299f * r + 0.587f * g + 0.114f * b;
        float ii = 0.596f * r - 0.274f * g - 0.322f * b;
        float qq = 0.211f * r - 0.523f * g + 0.312f * b;
        float sat = (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b))) / 255f;
        float ni = (ii * c - qq * s) * 1.4f + 70 * c * (1 - sat);
        float nq = (ii * s + qq * c) * 1.4f + 70 * s * (1 - sat);
        int rr = clamp(yy + 0.956 * ni + 0.621 * nq);
        int gg = clamp(yy - 0.272 * ni - 0.647 * nq);
        int bb = clamp(yy - 1.106 * ni + 1.703 * nq);
        return 0xFF000000 | (rr << 16) | (gg << 8) | bb;
    }

    private static int clamp(double v) {
        return v < 0 ? 0 : v > 255 ? 255 : (int) v;
    }

    static int mix(int a, int b, float t) {
        if (t <= 0) return a;
        if (t >= 1) return b;
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }
}
