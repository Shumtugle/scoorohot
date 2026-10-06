package com.shumtugle.scoorohot;

/**
 * A small box of colored cubes, and a ray caster that turns it into pixel art.
 * Vehicles are modeled here once and rendered at the exact pixel size the
 * screen needs, from a chosen yaw and pitch, so their shapes never stretch:
 * a turn is a real rotation of a rigid body, and a car far away is a smaller
 * rendering of the same model rather than a squashed bitmap.
 */
final class Voxel {
    final int gx, gy, gz;
    final byte[] cells;      // palette index per cell, 0 is empty
    float bodyWidth = 40f;   // physical width in cells; forty cells are one standard car
    private boolean[] foot;  // ground footprint for the shadow, built on first render

    /** Shade per face, in the order -x, +x, -y, +y, -z, +z. Light comes from behind, above and left. */
    static final float[] SHADE = new float[6];

    static {
        float lx = -0.45f, ly = 0.85f, lz = -0.35f;
        float n = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
        lx /= n;
        ly /= n;
        lz /= n;
        float[] d = {-lx, lx, -ly, ly, -lz, lz};
        for (int i = 0; i < 6; i++) SHADE[i] = 0.58f + 0.5f * Math.max(0f, d[i]);
    }

    Voxel(int gx, int gy, int gz) {
        this.gx = gx;
        this.gy = gy;
        this.gz = gz;
        cells = new byte[gx * gy * gz];
    }

    int get(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= gx || y >= gy || z >= gz) return 0;
        return cells[(z * gy + y) * gx + x] & 0xFF;
    }

    void set(int x, int y, int z, int c) {
        if (x < 0 || y < 0 || z < 0 || x >= gx || y >= gy || z >= gz) return;
        cells[(z * gy + y) * gx + x] = (byte) c;
    }

    /** Fills [x0, x1) x [y0, y1) x [z0, z1). */
    void box(int x0, int x1, int y0, int y1, int z0, int z1, int c) {
        for (int z = z0; z < z1; z++)
            for (int y = y0; y < y1; y++)
                for (int x = x0; x < x1; x++) set(x, y, z, c);
    }

    /** Recolors only cells that already exist. */
    void paint(int x0, int x1, int y0, int y1, int z0, int z1, int c) {
        for (int z = z0; z < z1; z++)
            for (int y = y0; y < y1; y++)
                for (int x = x0; x < x1; x++) if (get(x, y, z) != 0) set(x, y, z, c);
    }

    /** A disc extruded along x: wheels. */
    void cylinderX(int x0, int x1, float cy, float cz, float r, int c) {
        int y0 = (int) Math.floor(cy - r), y1 = (int) Math.ceil(cy + r);
        int z0 = (int) Math.floor(cz - r), z1 = (int) Math.ceil(cz + r);
        for (int z = z0; z <= z1; z++)
            for (int y = y0; y <= y1; y++) {
                float dy = y + 0.5f - cy, dz = z + 0.5f - cz;
                if (dy * dy + dz * dz <= r * r)
                    for (int x = x0; x < x1; x++) set(x, y, z, c);
            }
    }

    void ellipsoid(float cx, float cy, float cz, float rx, float ry, float rz, int c) {
        for (int z = (int) (cz - rz); z <= cz + rz; z++)
            for (int y = (int) (cy - ry); y <= cy + ry; y++)
                for (int x = (int) (cx - rx); x <= cx + rx; x++) {
                    float dx = (x + 0.5f - cx) / rx, dy = (y + 0.5f - cy) / ry, dz = (z + 0.5f - cz) / rz;
                    if (dx * dx + dy * dy + dz * dz <= 1f) set(x, y, z, c);
                }
    }

    // ---------------------------------------------------------------- render

    /** A rendered view: palette index per pixel (0 empty, 255 ground shadow) and the face it hit. */
    static final class View {
        final int w, h, ax, ay; // (ax, ay): where the ground point under the model's center lands
        final byte[] idx, face;

        View(int w, int h, int ax, int ay) {
            this.w = w;
            this.h = h;
            this.ax = ax;
            this.ay = ay;
            idx = new byte[w * h];
            face = new byte[w * h];
        }
    }

    /**
     * Orthographic ray casting. ppv is pixels per cell; yaw turns the camera
     * around the model (positive shows the model's left flank), pitch looks down.
     */
    View render(float ppv, float yawDeg, float pitchDeg) {
        if (foot == null) buildFootprint();
        double a = Math.toRadians(yawDeg), p = Math.toRadians(pitchDeg);
        float fx = (float) (Math.sin(a) * Math.cos(p)), fy = (float) -Math.sin(p), fz = (float) (Math.cos(a) * Math.cos(p));
        float rx = (float) Math.cos(a), rz = (float) -Math.sin(a);
        float ux = fy * rz, uy = fz * rx - fx * rz, uz = -fy * rx;
        float ox = gx / 2f, oz = gz / 2f;

        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            float cx = ((i & 1) == 0 ? 0 : gx) - ox, cy = (i & 2) == 0 ? 0 : gy, cz = ((i & 4) == 0 ? 0 : gz) - oz;
            float sx = cx * rx + cz * rz, sy = cx * ux + cy * uy + cz * uz;
            minX = Math.min(minX, sx);
            maxX = Math.max(maxX, sx);
            minY = Math.min(minY, sy);
            maxY = Math.max(maxY, sy);
        }
        int w = (int) Math.ceil((maxX - minX) * ppv) + 2, h = (int) Math.ceil((maxY - minY) * ppv) + 2;
        View v = new View(w, h, Math.round(-minX * ppv + 1), Math.round(maxY * ppv + 1));
        float back = gx + gy + gz;
        int[] cell = new int[2];
        for (int j = 0; j < h; j++) {
            float sy = maxY - (j - 1 + 0.5f) / ppv;
            for (int i = 0; i < w; i++) {
                float sx = minX + (i - 1 + 0.5f) / ppv;
                float px = ox + rx * sx + ux * sy - fx * back;
                float py = uy * sy - fy * back;
                float pz = oz + rz * sx + uz * sy - fz * back;
                if (cast(px, py, pz, fx, fy, fz, cell)) {
                    v.idx[j * w + i] = (byte) cell[0];
                    v.face[j * w + i] = (byte) cell[1];
                } else if (fy < 0) {
                    float t = -py / fy;
                    int gxp = (int) Math.floor(px + fx * t), gzp = (int) Math.floor(pz + fz * t);
                    if (gxp >= 0 && gzp >= 0 && gxp < gx && gzp < gz && foot[gzp * gx + gxp]) v.idx[j * w + i] = (byte) 255;
                }
            }
        }
        return v;
    }

    /** Walks one ray through the grid cell by cell; out[0] gets the palette index, out[1] the face. */
    private boolean cast(float px, float py, float pz, float fx, float fy, float fz, int[] out) {
        float tmin = 0f, tmax = Float.MAX_VALUE;
        int axis = -1;
        float[] o = {px, py, pz}, d = {fx, fy, fz};
        int[] g = {gx, gy, gz};
        for (int k = 0; k < 3; k++) {
            if (Math.abs(d[k]) < 1e-6f) {
                if (o[k] < 0 || o[k] > g[k]) return false;
                continue;
            }
            float t1 = (0 - o[k]) / d[k], t2 = (g[k] - o[k]) / d[k];
            float lo = Math.min(t1, t2), hi = Math.max(t1, t2);
            if (lo > tmin) {
                tmin = lo;
                axis = k;
            }
            if (hi < tmax) tmax = hi;
        }
        if (tmax <= tmin || axis < 0) return false;
        float t = tmin + 1e-4f;
        float x = px + fx * t, y = py + fy * t, z = pz + fz * t;
        int ix = Math.min(gx - 1, Math.max(0, (int) Math.floor(x)));
        int iy = Math.min(gy - 1, Math.max(0, (int) Math.floor(y)));
        int iz = Math.min(gz - 1, Math.max(0, (int) Math.floor(z)));
        int sx = fx > 0 ? 1 : -1, sy = fy > 0 ? 1 : -1, sz = fz > 0 ? 1 : -1;
        float dx = fx != 0 ? Math.abs(1f / fx) : Float.MAX_VALUE;
        float dy = fy != 0 ? Math.abs(1f / fy) : Float.MAX_VALUE;
        float dz = fz != 0 ? Math.abs(1f / fz) : Float.MAX_VALUE;
        float mx = fx > 0 ? t + (ix + 1 - x) / fx : fx < 0 ? t + (ix - x) / fx : Float.MAX_VALUE;
        float my = fy > 0 ? t + (iy + 1 - y) / fy : fy < 0 ? t + (iy - y) / fy : Float.MAX_VALUE;
        float mz = fz > 0 ? t + (iz + 1 - z) / fz : fz < 0 ? t + (iz - z) / fz : Float.MAX_VALUE;
        int face = axis == 0 ? (fx > 0 ? 0 : 1) : axis == 1 ? (fy > 0 ? 2 : 3) : (fz > 0 ? 4 : 5);
        while (true) {
            int c = cells[(iz * gy + iy) * gx + ix] & 0xFF;
            if (c != 0) {
                out[0] = c;
                out[1] = face;
                return true;
            }
            if (mx < my && mx < mz) {
                ix += sx;
                mx += dx;
                face = sx > 0 ? 0 : 1;
                if (ix < 0 || ix >= gx) return false;
            } else if (my < mz) {
                iy += sy;
                my += dy;
                face = sy > 0 ? 2 : 3;
                if (iy < 0 || iy >= gy) return false;
            } else {
                iz += sz;
                mz += dz;
                face = sz > 0 ? 4 : 5;
                if (iz < 0 || iz >= gz) return false;
            }
        }
    }

    private void buildFootprint() {
        boolean[] f = new boolean[gx * gz];
        for (int z = 0; z < gz; z++)
            for (int x = 0; x < gx; x++)
                for (int y = 0; y < gy; y++)
                    if (get(x, y, z) != 0) {
                        f[z * gx + x] = true;
                        break;
                    }
        foot = new boolean[gx * gz];
        for (int z = 0; z < gz; z++)
            for (int x = 0; x < gx; x++) {
                boolean on = false;
                for (int dz = -1; dz <= 1 && !on; dz++)
                    for (int dx = -1; dx <= 1 && !on; dx++) {
                        int xx = x + dx, zz = z + dz;
                        if (xx >= 0 && zz >= 0 && xx < gx && zz < gz && f[zz * gx + xx]) on = true;
                    }
                foot[z * gx + x] = on;
            }
    }
}
