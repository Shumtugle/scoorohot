package com.shumtugle.scoorohot;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LightingColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;

/**
 * Classic segment-projection road, painted from the horizon toward the camera so
 * that nearer ground simply covers what lies behind it. That order is what lets
 * a segment have two heights: on a banked bend the outer edge stands higher than
 * the inner one, and the strip is drawn as a true slanted quad.
 * Bends accumulate as a sideways drift, hills as height. Everything is drawn
 * into a small framebuffer and scaled up without filtering.
 * The camera rolls with the car, so the road under the car looks level and the
 * world tilts; the background and the grass are painted wider than the screen.
 */
final class Renderer {
    static final int DRAW = 170;
    private static final float[] FOG = new float[DRAW + 2];

    static {
        for (int n = 0; n < FOG.length; n++) {
            float d = Math.min(1f, n / (float) DRAW);
            float f = 1f - (float) Math.exp(-d * d * 3.2f);
            FOG[n] = (float) Math.floor(f * 7f) / 7f; // banded haze
        }
    }

    // projected near (index n) and far (index n + 1) edges share one table
    private final float[] px = new float[DRAW + 2], py = new float[DRAW + 2], pw = new float[DRAW + 2];
    private final float[] pb = new float[DRAW + 2], ps = new float[DRAW + 2], pz = new float[DRAW + 2];
    private final Track.Seg[] seg = new Track.Seg[DRAW + 2];
    private final Path path = new Path();
    private final Paint paint = new Paint();
    private final Paint bmpPaint = new Paint();
    private final Paint stroke = new Paint();
    private final Rect src = new Rect();
    private final RectF dst = new RectF();
    private Bitmap clouds;
    private float time;
    // the last power pole drawn on each side, farther than the one being drawn now
    private final float[] wireX1 = new float[2], wireX2 = new float[2], wireY = new float[2];
    private final boolean[] wireOn = new boolean[2];

    Renderer() {
        paint.setAntiAlias(false);
        paint.setStyle(Paint.Style.FILL);
        bmpPaint.setAntiAlias(false);
        bmpPaint.setFilterBitmap(false);
        stroke.setAntiAlias(false);
        stroke.setStyle(Paint.Style.STROKE);
    }

    void setClouds(Bitmap b) {
        clouds = b;
    }

    void render(Canvas c, int w, int h, Race r, Garage garage, boolean mini) {
        time = r.clock;
        wireOn[0] = wireOn[1] = false;
        atmosphere(r);
        glows = 0;
        lit = 0;
        wOn = r.waveOn && r.waveT > 0.2f;
        wSeg = r.waveSeg;
        wSide = r.waveSide;
        wFront = r.waveFront;
        float horizon = (float) Math.floor(h * 0.36f);
        float carLine = h * (mini ? 0.9f : 0.86f);
        float k = w / 2f;
        float camH = (carLine - horizon) * Race.ROAD_W / (Race.FILL * k);
        // in a chase the camera falls back and climbs: the car is out ahead, the police close behind
        float blend = mini ? 0f : r.chaseBlend;
        boolean chase = blend > 0f;
        // among the stars the camera rides low, close behind, looking up the tube
        boolean low = r.inSpace && !r.flood;
        camH *= low ? 1f - 0.45f * blend : 1f + 0.5f * blend;
        float groundCam = camH;
        camH += r.altitude * 0.75f; // the camera rises with a flying car, so the road falls away below
        camLift = camH / groundCam;

        double pos = r.position - Race.CHASE_PULL * blend * (low ? 0.55f : 1f);
        int base = (int) (pos / Track.SEG);
        float basePct = (float) ((pos - (double) base * Track.SEG) / Track.SEG);
        int pIdx = r.playerSegment();
        float pPct = r.playerPercent();
        Track.Seg pseg = r.track.get(pIdx);
        float roadY = pseg.y1 + (pseg.y2 - pseg.y1) * pPct;
        float bankP = pseg.bank1 + (pseg.bank2 - pseg.bank1) * pPct;
        float widthP = pseg.width1 + (pseg.width2 - pseg.width1) * pPct;
        // the camera rides at the surface height under the car, not at the crown of the road
        float camY = roadY - r.playerX * bankP + camH;
        float camX = r.playerX * Race.ROAD_W * widthP;

        // project every edge once; edge n is the near edge of segment n
        float x = 0, dx = -(r.track.get(base).curve * basePct);
        for (int n = 0; n <= DRAW + 1; n++) {
            Track.Seg s = r.track.get(base + n);
            seg[n] = s;
            float z = (float) ((double) (base + n) * Track.SEG - pos);
            float sc = z > 1f ? Race.DEPTH / z : 0f;
            pz[n] = z;
            ps[n] = sc;
            px[n] = Math.round(k + sc * (x - camX) * k);
            py[n] = Math.round(horizon - sc * (s.y1 - camY) * k);
            pw[n] = Math.round(sc * Race.ROAD_W * s.width1 * k);
            pb[n] = sc * s.bank1 * k; // screen rise of the left edge above the center
            x += dx;
            dx += s.curve;
        }

        if (r.inSpace) c.drawColor(0xFF020108); // corners a vortex turns into view are open space
        c.save();
        c.rotate(r.roll + r.twist, w / 2f, carLine);
        backdrop(c, w, h, horizon, r);
        if (r.inSpace && !r.flood && vortex != null) {
            // the whirl the tube runs into, over the far end of the road
            float vw = w * 1.1f, vh = vw * vortex.getHeight() / vortex.getWidth();
            float vx = px[DRAW], vy = Math.min(py[DRAW], horizon) - vh * 0.3f;
            dst.set(vx - vw / 2, vy - vh / 2, vx + vw / 2, vy + vh / 2);
            bmpPaint.setAlpha(255);
            c.drawBitmap(vortex, null, dst, bmpPaint);
        }

        double playerZ = r.position + Race.PLAYER_Z;
        double policeZ = playerZ - r.policeGap * Track.SEG;
        for (int n = DRAW; n >= 1; n--) {
            Track.Seg s = seg[n];
            float fog = fogT[n];
            if (pz[n] > Race.DEPTH && py[n + 1] < py[n]) segment(c, w, n, s.theme, s);
            if (pz[n] > 350f) {
                for (int i = 0; i < s.nProps; i++) {
                    Track.Prop p = s.props[i];
                    Bitmap pic = p.junction != null ? p.frames[p.junction.light]
                            : p.frames == null ? p.bmp : p.frames[((int) (time * p.animRate)) % p.frames.length];
                    float sc = ps[n];
                    float px0 = Track.propX(s, p), ww = p.worldW;
                    if (p.spans) {
                        // a gate or an overpass spans both carriageways when they run together
                        float left = s.onc > 0 && s.gap1 < 0.5f ? Track.leftOf(s) : -1f;
                        px0 = (left + 1f) / 2f;
                        ww = p.worldW * s.width1 * (1f - left) / 2f;
                    }
                    float dw = ww * sc * k;
                    if (dw < 1f) continue;
                    float dh = dw * p.bmp.getHeight() / p.bmp.getWidth();
                    float off = px0 * Race.ROAD_W * s.width1;
                    float sx = px[n] + sc * off * k + p.anchor * dw;
                    // props stand on the edge they are near to
                    float sy = py[n] - pb[n] * Math.max(-1f, Math.min(1f, -px0));
                    if (p.wire) wires(c, px0 < 0 ? 0 : 1, sx, sy - dh, dw, dh, fog);
                    bmpPaint.setColorFilter(propFilter);
                    sprite(c, pic, sx, sy - dh * (1f - p.sink), dw, dh, fog);
                    bmpPaint.setColorFilter(null);
                    if (p.glow == 1) glow(sx + dw * 0.5f, sy - dh * 0.9f, Math.max(1f, dw * 0.45f), 0xFFFFE8A0);
                    else if (p.glow == 3) {
                        // the station at night: lights under the canopy, and the tall sign
                        glow(sx + dw * 0.2f, sy - dh * 0.58f, Math.max(1f, dw * 0.05f), 0xFFF4F8FF);
                        glow(sx + dw * 0.45f, sy - dh * 0.58f, Math.max(1f, dw * 0.05f), 0xFFF4F8FF);
                        glow(sx + dw * 0.92f, sy - dh * 0.84f, Math.max(1f, dw * 0.08f), 0xFFFFD24A);
                    } else if (p.glow == 4 && lit < litBmp.length) {
                        // road signs reflect the headlights: redrawn bright over the night, not haloed
                        litBmp[lit] = pic;
                        litBox[lit * 4] = sx;
                        litBox[lit * 4 + 1] = sy - dh * (1f - p.sink);
                        litBox[lit * 4 + 2] = dw;
                        litBox[lit * 4 + 3] = dh;
                        litFog[lit] = fog;
                        lit++;
                    }
                    else if (p.glow == 2 && p.junction != null) {
                        float ly = new float[]{20f, 13f, 6f}[p.junction.light] / 74f;
                        int lc = new int[]{0xFF3AE070, 0xFFFFC22A, 0xFFFF3A2A}[p.junction.light];
                        glow(sx + dw * 0.5f, sy - dh + dh * ly, Math.max(1f, dw * 0.18f), lc);
                    }
                }
            }
            for (int i = 0; i < r.cars.size(); i++) {
                Race.Car car = r.cars.get(i);
                if (!chase && car.z < playerZ - Track.SEG * 0.3f) continue;
                double cs = car.z / Track.SEG;
                if ((int) cs != base + n || pz[n] < 300f) continue;
                float pct = (float) (cs - Math.floor(cs));
                float sc = ps[n] + (ps[n + 1] - ps[n]) * pct;
                if (sc <= 0) continue;
                Garage.Model m = garage.traffic[car.kind];
                // each car is drawn one to one from a picture rendered at its own size
                Garage.Sprite t = m.traffic(Math.round(m.worldW * sc * k));
                if (t == null) continue;
                float wd = s.width1 + (s.width2 - s.width1) * pct;
                float bk = pb[n] + (pb[n + 1] - pb[n]) * pct;
                float sx = px[n] + (px[n + 1] - px[n]) * pct + sc * car.offset * Race.ROAD_W * wd * k;
                float sy = py[n] + (py[n + 1] - py[n]) * pct + bk * car.offset - car.lift * sc * k;
                bmpPaint.setAlpha((int) (255 * (1f - fog)));
                c.drawBitmap(t.bmp, Math.round(sx) - t.ax, Math.round(sy) - t.ay, bmpPaint);
                float cw = m.worldW * sc * k;
                glow(sx - cw * 0.3f, sy - cw * 0.24f, Math.max(0.8f, cw * 0.07f), 0xFFFF3020);
                glow(sx + cw * 0.3f, sy - cw * 0.24f, Math.max(0.8f, cw * 0.07f), 0xFFFF3020);
            }
            for (int i = 0; i < r.oncoming.size(); i++) {
                Race.Car car = r.oncoming.get(i);
                double cs = car.z / Track.SEG;
                if ((int) cs != base + n || pz[n] < 300f) continue;
                float pct = (float) (cs - Math.floor(cs));
                float sc = ps[n] + (ps[n + 1] - ps[n]) * pct;
                if (sc <= 0) continue;
                Garage.Model m = garage.traffic[car.kind];
                Garage.Sprite t = m.front(Math.round(m.worldW * sc * k));
                if (t == null) continue;
                float wd = s.width1 + (s.width2 - s.width1) * pct;
                float bk = pb[n] + (pb[n + 1] - pb[n]) * pct;
                float sx = px[n] + (px[n + 1] - px[n]) * pct + sc * car.offset * Race.ROAD_W * wd * k;
                float sy = py[n] + (py[n + 1] - py[n]) * pct + bk * Math.max(-2.5f, car.offset) - car.lift * sc * k;
                bmpPaint.setAlpha((int) (255 * (1f - fog)));
                c.drawBitmap(t.bmp, Math.round(sx) - t.ax, Math.round(sy) - t.ay, bmpPaint);
                float cw = m.worldW * sc * k;
                glow(sx - cw * 0.3f, sy - cw * 0.22f, Math.max(1f, cw * 0.09f), 0xFFFFF4D0);
                glow(sx + cw * 0.3f, sy - cw * 0.22f, Math.max(1f, cw * 0.09f), 0xFFFFF4D0);
            }
            if (s.cross && pz[n] > 300f) crossing(c, r, garage, n, k, fog);
            if (r.ufoActive && !mini && (int) (r.ufoZ / Track.SEG) == base + n && pz[n] > 300f) saucer(c, r, garage, n, k);
            if ((s.heartX < 9f || s.wingsX < 9f) && pz[n] > 150f) find(c, s, n, k, fog);
            if (s.hole != 0 && s.holeRing == 0 && pz[n] > 150f) {
                sw = w;
                portal(c, n, k, fog);
            }
            for (int i = 0; i < r.shots.size(); i++) {
                float[] sh = r.shots.get(i);
                if ((int) (sh[0] / Track.SEG) != base + n || pz[n] < 200f) continue;
                // a dash of rainbow fired past the car, streaking ahead of it
                int m = Math.min(DRAW + 1, n + 2);
                float x1 = px[n] + ps[n] * sh[1] * Race.ROAD_W * s.width1 * k, y1 = py[n] - ps[n] * 380f * k;
                float x2 = px[m] + ps[m] * sh[1] * Race.ROAD_W * s.width1 * k, y2 = py[m] - ps[m] * 380f * k;
                stroke.setStrokeWidth(Math.max(1f, ps[n] * 90f * k));
                stroke.setColor(RAINBOW[(i + (int) (time * 12)) % RAINBOW.length]);
                c.drawLine(x1, y1, x2, y2, stroke);
            }
            if (chase) {
                Garage.Model me = garage.vehicles[r.vehicle];
                float pitch = low ? 10f : 16f + Math.round(blend * 3f) * 6f;
                boardNext = r.flood;
                smokeNext = r.inSpace ? -1f : r.health;
                if ((int) (playerZ / Track.SEG) == base + n)
                    inWorld(c, me, playerZ, r.playerX, n, k, frameOf(me, r), r.braking || r.busted, yawOf(r), pitch, r.altitude);
                boardNext = false; // the board, the smoke and the wings were the player's only
                smokeNext = -1f;
                // behind you: the police, or in the rabbit hole the saucer, hovering
                Garage.Model cop = garage.vehicles[r.alien ? Garage.UFO_INDEX : Garage.POLICE_INDEX];
                if (r.chase && (int) (policeZ / Track.SEG) == base + n && pz[n] > 300f) {
                    if (r.flood) waveWall(c, r, n, k);
                    else inWorld(c, cop, policeZ, r.policeX, n, k, ((int) (r.clock * 5)) & 3, r.busted, 2, pitch, r.alien ? 500f : 0f);
                }
            }
        }
        night(c, w, h);
        c.restore();
        if (chase) {
            precipitation(c, w, h, r);
            return;
        }

        // the player's vehicle stays level: the camera is bolted to it. A turn shows
        // the body rotated, rendered from another yaw, never a bent picture.
        Garage.Model v = garage.vehicles[r.vehicle];
        float psc = Race.DEPTH / Race.PLAYER_Z;
        int vwPx = Math.round(v.worldW * psc * k);
        Garage.Sprite ps0;
        if (r.spin > 0) {
            // a spin turns the whole body round; its pictures cover the full circle
            Garage.Sprite[] turn = v.spin(vwPx);
            int i = Math.round(r.spinAngle / (360f / Garage.SPIN_VIEWS));
            ps0 = turn[((i % Garage.SPIN_VIEWS) + Garage.SPIN_VIEWS) % Garage.SPIN_VIEWS];
        } else {
            Garage.Sprite[] frames = v.views(vwPx)[yawOf(r)][r.braking ? 1 : 0];
            ps0 = frames[frameOf(v, r)];
        }
        int bounce = 0;
        if (v.wheels && r.speed > 0) bounce = ((int) (r.clock * (6 + r.speed / Race.VMAX * 14))) & 1;
        if (v.hovers) bounce = Math.round((float) Math.sin(r.clock * 3f) * 1.5f) + 2;
        int shake = r.offroad && r.speed > 0 && v.wheels ? (((int) (r.clock * 30)) & 1) * 2 - 1 : 0;
        bmpPaint.setAlpha(255);
        int vx = Math.round(k) - ps0.ax + shake, vy = Math.round(carLine) - ps0.ay - bounce;
        if (dark > 0.2f && v.wheels) {
            // headlamps lay two soft round pools on the road, a dark gap between them by the bumper,
            // and a fainter pool further on where the beams meet; soft edges come in steps
            int warm = 0xFFF2D8;
            float a0 = dark * 27f;
            for (int layer = 0; layer < 5; layer++) {
                float sc = 1f - layer * 0.17f;
                paint.setColor(((int) a0 << 24) | warm);
                for (int side = -1; side <= 1; side += 2) {
                    float cx = k + side * vwPx * 0.42f, cy = carLine - vwPx * 1.05f;
                    dst.set(cx - vwPx * 0.8f * sc, cy - vwPx * 0.42f * sc, cx + vwPx * 0.8f * sc, cy + vwPx * 0.42f * sc);
                    c.drawOval(dst, paint);
                }
                paint.setColor(((int) (a0 * 0.6f) << 24) | warm);
                float fy = carLine - vwPx * 2.05f;
                dst.set(k - vwPx * 1.15f * sc, fy - vwPx * 0.32f * sc, k + vwPx * 1.15f * sc, fy + vwPx * 0.32f * sc);
                c.drawOval(dst, paint);
            }
        }
        float beamTop = -1f;
        if (r.ufoActive && r.beamOn && Math.abs(r.ufoRel) < 4f || r.abductTime > 0) {
            // the beam over the player's own road: a warning, or the way up
            float bx = r.abductTime > 0 ? k : k + (r.beamX - r.playerX) * Race.ROAD_W * widthP * psc * k;
            float half = r.abductTime > 0 ? vwPx * 0.7f : Race.BEAM_HALF * Race.ROAD_W * widthP * psc * k;
            paint.setColor(0x48C8FFF0);
            c.drawRect(bx - half, 0, bx + half, carLine + 2, paint);
            paint.setColor(0x30FFFFFF);
            c.drawRect(bx - half * 0.4f, 0, bx + half * 0.4f, carLine + 2, paint);
            beamTop = 0f;
        }
        if (r.abductTime > 0) vy -= Math.round((1f - r.abductTime / 1.6f) * 150f);
        if (r.washTime > 0) {
            float t = 1f - r.washTime / 1.4f;
            vx += Math.round(r.waveSide * t * w * 0.35f);
            boardUnder(c, vx + ps0.ax, carLine - bounce, vwPx);
        }
        if (r.altitude > 0f) {
            // the ground under the car is lower on screen the higher the camera climbs
            float gy = horizon + (carLine - horizon) * camLift;
            paint.setColor(0x50000000);
            dst.set(k - vwPx * 0.45f, gy - vwPx * 0.08f, k + vwPx * 0.45f, gy + vwPx * 0.08f);
            c.drawOval(dst, paint);
            wings(c, k + shake, carLine - vwPx * 0.32f - bounce, vwPx);
        }
        bmpPaint.setColorFilter(beamTop >= 0 ? null : shadeFilter);
        c.drawBitmap(ps0.bmp, vx, vy, bmpPaint);
        bmpPaint.setColorFilter(null);
        if (!r.inSpace) smoke(c, k + shake, carLine - vwPx * 0.42f - bounce, vwPx, r.health);
        if (dark > 0.15f && v.wheels) {
            int lc = r.braking ? 0xFFFF4030 : 0xFFC02018;
            for (int sd = -1; sd <= 1; sd += 2) {
                paint.setColor(((int) (dark * 90) << 24) | (lc & 0xFFFFFF));
                c.drawCircle(k + sd * vwPx * 0.3f, carLine - vwPx * 0.17f, vwPx * 0.12f, paint);
            }
        }
        if (pseg.water && !v.hovers) wade(c, k, carLine, Math.round(v.worldW * psc * k), r, pseg.theme);

        precipitation(c, w, h, r);
        if (mini) {
            float rr = Math.max(8f, Math.min(w * 0.17f, h * 0.075f));
            gauge(c, rr + 3, h - rr - 3, rr, r, false, null);
        }
    }

    /** Steering left turns the nose left: the car's left flank comes into view (positive yaw). */
    private static int yawOf(Race r) {
        return Math.max(0, Math.min(Garage.YAWS.length - 1, Math.round((1f - r.steer) * 0.5f * (Garage.YAWS.length - 1))));
    }

    private static int frameOf(Garage.Model v, Race r) {
        int n = v.frames();
        return v.rolls ? ((int) r.animPhase) % n : ((int) (r.clock * v.animRate)) % n;
    }

    /** A vehicle standing in the world at depth z, seen from the chase camera above. */
    private void inWorld(Canvas c, Garage.Model m, double z, float xNorm, int n, float k, int frame, boolean lights, int yaw, float pitch, float lift) {
        double cs = z / Track.SEG;
        float pct = (float) (cs - Math.floor(cs));
        float sc = ps[n] + (ps[n + 1] - ps[n]) * pct;
        if (sc <= 0) return;
        Track.Seg s = seg[n];
        float wd = s.width1 + (s.width2 - s.width1) * pct;
        float bk = pb[n] + (pb[n + 1] - pb[n]) * pct;
        int width = Math.max(4, Math.round(m.worldW * sc * k / 2f) * 2);
        Garage.Sprite[] fr = m.views(width, pitch)[yaw][lights ? 1 : 0];
        Garage.Sprite sp = fr[frame % fr.length];
        float sx = px[n] + (px[n + 1] - px[n]) * pct + sc * xNorm * Race.ROAD_W * wd * k;
        float sy = py[n] + (py[n + 1] - py[n]) * pct + bk * xNorm - lift * sc * k;
        if (boardNext) boardUnder(c, sx, sy, m.worldW * sc * k);
        if (lift > 0 && smokeNext >= 0) {
            float gy = sy + lift * sc * k;
            paint.setColor(0x50000000);
            float cw = m.worldW * sc * k;
            dst.set(sx - cw * 0.45f, gy - cw * 0.08f, sx + cw * 0.45f, gy + cw * 0.08f);
            c.drawOval(dst, paint);
            wings(c, sx, sy - cw * 0.3f, cw);
        }
        boolean mine = boardNext || smokeNext >= 0;
        boardNext = false;
        bmpPaint.setAlpha(255);
        c.drawBitmap(sp.bmp, Math.round(sx) - sp.ax, Math.round(sy) - sp.ay, bmpPaint);
        if (smokeNext >= 0 && mine) smoke(c, sx, sy - m.worldW * sc * k * 0.42f, m.worldW * sc * k, smokeNext);
        smokeNext = -1f;
    }

    private boolean boardNext;
    private float smokeNext = -1f, camLift = 1f;

    /** Smoke from a damaged car's hood: grey below 60%, thick and dark below 30%, drifting back. */
    private void smoke(Canvas c, float x, float y, float carW, float health) {
        if (health >= 0.6f) return;
        boolean bad = health < 0.3f;
        int n = bad ? 10 : 7;
        for (int i = 0; i < n; i++) {
            float ph = (time * (bad ? 1.1f : 0.8f) + i / (float) n) % 1f;
            float px0 = x + (float) Math.sin(i * 1.7f + time * 2f) * carW * 0.12f + ph * carW * 0.15f;
            float py0 = y - ph * carW * 1.1f;
            float rr = carW * (0.06f + 0.13f * ph);
            int a = (int) ((bad ? 210 : 175) * (1f - ph));
            paint.setColor((a << 24) | (bad ? 0x3A3A3E : 0x9A9A9C));
            c.drawCircle(px0, py0, rr, paint);
        }
    }

    /** A heart on the shoulder or wings in a lane, bobbing a little above the road. */
    private void find(Canvas c, Track.Seg s, int n, float k, float fog) {
        float sc = ps[n], wd = s.width1;
        float bob = (float) Math.sin(time * 4f + s.index) * 60f * sc * k;
        if (s.heartX < 9f && heart != null) {
            float dw = 820f * sc * k, dh = dw * heart.getHeight() / heart.getWidth();
            float x = px[n] + sc * s.heartX * Race.ROAD_W * wd * k, y = py[n] - dw * 0.5f - bob;
            sprite(c, heart, x - dw / 2, y - dh, dw, dh, fog * 0.5f);
            glow(x, y - dh / 2, Math.max(1f, dw * 0.35f), 0xFFFF4A5A);
        }
        if (s.wingsX < 9f && wingsItem != null) {
            float dw = 1800f * sc * k, dh = dw * wingsItem.getHeight() / wingsItem.getWidth();
            float x = px[n] + sc * s.wingsX * Race.ROAD_W * wd * k, y = py[n] - dw * 0.15f - bob;
            sprite(c, wingsItem, x - dw / 2, y - dh, dw, dh, fog * 0.5f);
            // gold glints round them, so they are seen from afar
            paint.setColor(0xFFFFD86A);
            for (int i = 0; i < 6; i++) {
                double an = time * 2.5 + i * Math.PI / 3;
                float gx = x + (float) Math.cos(an) * dw * 0.55f, gy = y - dh * 0.5f + (float) Math.sin(an) * dh * 0.9f;
                float gs = Math.max(1f, dw * 0.03f);
                c.drawRect(gx - gs, gy - gs, gx + gs, gy + gs, paint);
            }
        }
    }

    /** Wings either side of a flying car, beating, and its shadow far below on the road. */
    private void wings(Canvas c, float x, float y, float carW) {
        if (wingL == null) return;
        int beat = ((int) (time * 7)) & 1;
        Bitmap l = wingL[beat], rt = wingR[beat];
        float ww = carW * 1.45f, wh = ww * l.getHeight() / l.getWidth();
        bmpPaint.setAlpha(255);
        dst.set(x - carW * 0.35f - ww, y - wh * 0.8f, x - carW * 0.35f, y + wh * 0.2f);
        c.drawBitmap(l, null, dst, bmpPaint);
        dst.set(x + carW * 0.35f, y - wh * 0.8f, x + carW * 0.35f + ww, y + wh * 0.2f);
        c.drawBitmap(rt, null, dst, bmpPaint);
    }

    /** A black hole standing upright over the road, wider than the road, its core to drive into. */
    private void portal(Canvas c, int n, float k, float fog) {
        if (blackHole == null) return;
        Bitmap b = blackHole[((int) (time * 6)) & 1];
        float sc = ps[n];
        // seen from afar it hangs over the horizon like a sun; close to, it grows to fill the road
        float bw = Math.max(Race.ROAD_W * 26f * seg[n].width1 * sc * k, sw * 0.5f), bh = bw * b.getHeight() / b.getWidth();
        float cx = px[n], cy = py[n] - bh * 0.24f; // the core sits on the road: the road runs into it
        dst.set(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2);
        bmpPaint.setAlpha(255);
        c.drawBitmap(b, null, dst, bmpPaint);
    }

    /** The great saucer, seen from below, and the cone of its beam down to the road. */
    private void saucer(Canvas c, Race r, Garage garage, int n, float k) {
        double cs = r.ufoZ / Track.SEG;
        float pct = (float) (cs - Math.floor(cs));
        float sc = ps[n] + (ps[n + 1] - ps[n]) * pct;
        if (sc <= 0) return;
        Track.Seg s = seg[n];
        float wd = s.width1;
        float gxs = px[n] + (px[n + 1] - px[n]) * pct + sc * r.beamX * Race.ROAD_W * wd * k;
        float gy = py[n] + (py[n + 1] - py[n]) * pct;
        float uw = Race.CAR_W * 5f * sc * k;
        float uy = gy - sc * r.ufoAlt * k;
        if (r.beamOn) {
            float half = sc * Race.BEAM_HALF * Race.ROAD_W * wd * k;
            paint.setColor(0x50C8FFF0);
            quad(c, gxs - uw * 0.12f, uy, gxs + uw * 0.12f, uy, gxs + half, gy, gxs - half, gy, 0x50C8FFF0);
            quad(c, gxs - uw * 0.05f, uy, gxs + uw * 0.05f, uy, gxs + half * 0.4f, gy, gxs - half * 0.4f, gy, 0x38FFFFFF);
            paint.setColor(0x70E8FFF8);
            dst.set(gxs - half, gy - half * 0.12f, gxs + half, gy + half * 0.12f);
            c.drawOval(dst, paint);
        }
        Garage.Sprite sp = garage.vehicles[Garage.UFO_INDEX].single(Math.round(uw), -12f, (int) (time * 6));
        bmpPaint.setAlpha(255);
        c.drawBitmap(sp.bmp, Math.round(gxs) - sp.ax, Math.round(uy) - sp.ay, bmpPaint);
    }

    /** Cars on the cross street of a crossroads, seen from the side. */
    private void crossing(Canvas c, Race r, Garage garage, int n, float k, float fog) {
        Track.Junction j = r.track.junctionAhead(seg[n].index - Track.CROSS_LEN - 2);
        if (j == null || j.cross.isEmpty()) return;
        for (int i = 0; i < j.cross.size(); i++) {
            Track.CrossCar cc = j.cross.get(i);
            double cs = cc.z / Track.SEG;
            if ((int) cs != seg[n].index) continue;
            float pct = (float) (cs - Math.floor(cs));
            float sc = ps[n] + (ps[n + 1] - ps[n]) * pct;
            Garage.Model m = garage.traffic[cc.kind];
            Garage.Sprite t = m.side(Math.round(m.worldW * sc * k), cc.dir);
            if (t == null) continue;
            float sx = px[n] + (px[n + 1] - px[n]) * pct + sc * cc.x * Race.ROAD_W * k;
            float sy = py[n] + (py[n + 1] - py[n]) * pct;
            bmpPaint.setAlpha((int) (255 * (1f - fog)));
            c.drawBitmap(t.bmp, Math.round(sx) - t.ax, Math.round(sy) - t.ay, bmpPaint);
        }
    }

    /** Wires from this pole's cross arm back to the previous, farther pole, sagging between. */
    private void wires(Canvas c, int side, float x, float y, float w, float h, float fog) {
        float x1 = x + w * 2f / 16f, x2 = x + w * 14f / 16f, ya = y + h * 5.5f / 84f;
        if (wireOn[side]) {
            stroke.setStrokeWidth(1);
            stroke.setColor(mix(0xFF26262C, 0xFF9AB4C4, fog));
            sag(c, x1, ya, wireX1[side], wireY[side]);
            sag(c, x2, ya, wireX2[side], wireY[side]);
        }
        wireX1[side] = x1;
        wireX2[side] = x2;
        wireY[side] = ya;
        wireOn[side] = true;
    }

    private void sag(Canvas c, float ax, float ay, float bx, float by) {
        float mx = (ax + bx) / 2, my = (ay + by) / 2 + Math.abs(ax - bx) * 0.06f + Math.abs(ay - by) * 0.12f + 1;
        c.drawLine(ax, ay, mx, my, stroke);
        c.drawLine(mx, my, bx, by, stroke);
    }

    /** Wading: the water hides the tyres and throws spray. */
    private void wade(Canvas c, float k, float carLine, int vw, Race r, Theme th) {
        paint.setColor((0xC0 << 24) | (mix(th.waterA, 0xFFFFFFFF, 0.15f) & 0xFFFFFF));
        c.drawRect(k - vw * 0.55f, carLine - 4, k + vw * 0.55f, carLine + 2, paint);
        float sp = r.speed / Race.VMAX;
        if (sp < 0.02f) return;
        int seed = (int) (r.clock * 24);
        for (int i = 0; i < 18; i++) {
            int h = (seed * 31 + i * 977) * 1103515245 + 12345;
            float fx = ((h >>> 8) & 255) / 255f, fy = ((h >>> 16) & 255) / 255f;
            float side = (i & 1) == 0 ? -1f : 1f;
            float x = k + side * (vw * 0.38f + fx * vw * 0.25f);
            float y = carLine - 3 - fy * (6 + 18 * sp);
            paint.setColor(i % 3 == 0 ? 0xFFFFFFFF : 0xFFBFE6EE);
            float sz = 1 + (i % 2);
            c.drawRect(Math.round(x), Math.round(y), Math.round(x) + sz, Math.round(y) + sz, paint);
        }
    }

    // ---------------------------------------------------------------- road

    // the segment being drawn: its screen edges, bank, the zone it blends from, and how far the bank reaches
    private float sX1, sY1, sW1, sB1, sX2, sY2, sW2, sB2, sw;
    // a wave over the road this frame: its stretch, its front and the sea's side
    private boolean wOn;
    private int wSeg, wSide;
    private float wFront;
    private Bitmap board;

    private Bitmap vortex;
    private Bitmap[] blackHole;
    private Bitmap heart, wingsItem;
    private Bitmap[] wingL, wingR;

    /** The finds on the road, and the wings a flying car wears. */
    void setFinds(Bitmap heart, Bitmap wingsItem, Bitmap[] wingL, Bitmap[] wingR) {
        this.heart = heart;
        this.wingsItem = wingsItem;
        this.wingL = wingL;
        this.wingR = wingR;
    }

    /** The whirl at the end of the rabbit hole, and the black hole standing over the road. */
    void setArt(Bitmap vortex, Bitmap[] blackHole) {
        this.vortex = vortex;
        this.blackHole = blackHole;
    }

    /** The torn billboard a washed-away car rides. */
    void setBoard(Bitmap b) {
        board = b;
    }
    private Theme zPrev;
    private float zT, yLo, yHi;

    /** A color of the zone being entered, blended from the zone left behind. */
    private int zc(int prevColor, int curColor) {
        return zPrev == null || zT >= 1f ? curColor : mix(prevColor, curColor, zT);
    }

    private static int fieldOf(Theme t, boolean light) {
        if (t.fieldStart > 0) return light ? t.fieldA : t.fieldB;
        return light ? t.grassLight : t.grassDark;
    }

    /**
     * One strip of road between edge n (near) and edge n + 1 (far): ground, the
     * oncoming carriageway with its curbs and median or center line, the
     * player's carriageway with its lanes, and whatever lies on it.
     */
    private void segment(Canvas c, int w, int n, Theme th, Track.Seg s) {
        sX1 = px[n];
        sY1 = py[n];
        sW1 = pw[n];
        sB1 = pb[n];
        sX2 = px[n + 1];
        sY2 = py[n + 1];
        sW2 = Math.round(ps[n + 1] * Race.ROAD_W * s.width2 * w / 2f);
        sB2 = ps[n + 1] * s.bank2 * w / 2f;
        sw = w;
        float fog = fogT[n];
        boolean light = ((s.index / Track.RUMBLE) & 1) == 0;
        Theme pv = s.prev;
        zPrev = pv;
        zT = pv == null ? 1f : Math.min(1f, (s.index - s.zoneStart) / (float) Track.FADE);
        int fogC = air(zc(pv == null ? 0 : pv.fog, th.fog));
        float dim = s.shade ? 0.4f : 0f;
        boolean two = s.onc > 0;
        float left = Track.leftOf(s), rb = 1f / 5.5f;
        yLo = Math.max(-2.5f, Math.min(left - rb, s.forkL >= 0 ? -3.2f - s.forkL : 0f));
        yHi = Math.min(2.5f, Math.max(1f + rb, s.forkR >= 0 ? 3.2f + s.forkR : 0f));
        int grass = tone(seasonal(zc(pv == null ? 0 : (light ? pv.grassLight : pv.grassDark), light ? th.grassLight : th.grassDark)), dim, fogC, fog);
        int asphalt = zc(pv == null ? 0 : (light ? pv.roadLight : pv.roadDark), light ? th.roadLight : th.roadDark);
        int road = tone(asphalt, dim, fogC, fog);

        if (th.id == Theme.SPACE) {
            hole(c, s, light, fogC, fog);
            return;
        }
        if (th.id == Theme.FLOOD) {
            channel(c, s, light, fogC, fog);
            return;
        }
        if (s.cross) {
            // the cross street: asphalt to the horizon both ways, a broken line down its middle
            quad(c, -w * 0.6f, sY1, w * 1.6f, sY1, w * 1.6f, sY2, -w * 0.6f, sY2, tone(asphalt, 0.12f, fogC, fog));
            if (s.crossMid) {
                int lc = tone(th.lane, 0, fogC, fog);
                for (float f = -9f; f < 9f; f += 0.7f)
                    if (Math.abs(f) > 1.1f) stripe(c, sX1, sY1, sW1, sB1, sX2, sY2, sW2, sB2, f, 0.18f, 0.18f, lc);
            }
            return;
        }
        if (s.water || s.bridge) {
            // a river: under the bridge, or across the road at a ford; its bands drift downstream
            boolean b = ((s.index + (int) (time * 3)) & 1) == 0;
            int wc = tone(zc(pv == null ? 0 : (b ? pv.waterA : pv.waterB), b ? th.waterA : th.waterB), 0, fogC, fog);
            ground(c, left - rb, -1, wc);
            ground(c, 1f + rb, 1, wc);
        } else {
            side(c, s, th, pv, left - rb, -1, s.sideL, grass, light, dim, fogC, fog);
            side(c, s, th, pv, 1f + rb, 1, s.sideR, grass, light, dim, fogC, fog);
        }
        if (s.water) {
            // the road is still there, under a hand of water
            boolean b = ((s.index + (int) (time * 3)) & 1) == 0;
            band(c, left - rb, 1f + rb, tone(mix(b ? th.waterA : th.waterB, asphalt, 0.3f), 0, fogC, fog));
            if (sW1 > 20) {
                int glint = tone(0xFFD8F0F0, 0, fogC, fog);
                for (int k = 0; k < 3; k++) {
                    int hsh = (s.index * 73856093) ^ (k * 19349663) ^ ((int) (time * 2) * 83492791);
                    hsh = hsh * 1103515245 + 12345;
                    if (((hsh >>> 20) & 3) != 0) continue;
                    float f = left + ((hsh >>> 8) & 1023) / 1023f * (1f - left);
                    stripe(c, sX1, sY1, sW1, sB1, sX2, sY2, sW2, sB2, f, 0.03f + ((hsh >>> 4) & 3) * 0.015f, 0.03f, glint);
                }
            }
            return;
        }
        int cl = s.bridge ? (light ? 0xFFCFCFC8 : 0xFF9E9E98) : zc(pv == null ? 0 : (light ? pv.curbLeftA : pv.curbLeftB), light ? th.curbLeftA : th.curbLeftB);
        int cr = s.bridge ? (light ? 0xFFCFCFC8 : 0xFF9E9E98) : zc(pv == null ? 0 : (light ? pv.curbRightA : pv.curbRightB), light ? th.curbRightA : th.curbRightB);
        cl = tone(cl, dim, fogC, fog);
        cr = tone(cr, dim, fogC, fog);
        int lane = tone(zc(pv == null ? 0 : pv.lane, th.lane), dim, fogC, fog);
        int track = tone(zc(pv == null ? 0 : pv.tyreTrack, th.tyreTrack), dim, fogC, fog);
        boolean marks = pv != null && zT < 0.5f ? pv.laneMarks : th.laneMarks;

        if (s.branch && s.forkL >= 0) carriage(c, -3f - s.forkL, -1f - s.forkL, road, cl, lane, light && marks);
        if (s.branch && s.forkR >= 0) carriage(c, 1f + s.forkR, 3f + s.forkR, road, cr, lane, light && marks);
        if (two) {
            float inner = -1f - s.gap1;
            if (s.gap1 > 0.05f) {
                band(c, inner + rb, -1f - rb, grass);   // the median
                band(c, -1f - rb, -1f, cl);              // our left curb
                band(c, inner, inner + rb, cl);          // their inner curb
            }
            band(c, left - rb, left, cl);                // their outer curb
            band(c, left, inner, road);                  // their carriageway
            if (sW1 > 24)
                for (int k = 0; k < s.onc; k++) tracks(c, inner - (2f * k + 1f) / s.lanes, s.lanes, track);
            if (marks && light)
                for (int k = 1; k < s.onc; k++) line(c, inner - 2f * k / s.lanes, lane);
        } else {
            band(c, -1f - rb, -1f, cl);
        }
        band(c, 1f, 1f + rb, cr);
        if (s.forkR >= 0 && s.exit) {
            // the slip road to a hangar, peeling away to the right
            float a0 = 1f + rb + s.forkR;
            band(c, a0, a0 + 0.75f, road);
            band(c, a0 - 0.08f, a0, cr);
            band(c, a0 + 0.75f, a0 + 0.83f, cr);
        }
        int surface = s.checkpoint ? tone(light ? 0xFFEDEDE8 : 0xFF202024, dim, fogC, fog) : road;
        band(c, -1f, 1f, surface);
        if (two && s.gap1 <= 0.05f && marks) {
            // the center line: a double yellow; dirt tracks have none
            int yel = tone(0xFFE8C040, dim, fogC, fog);
            line(c, -1.03f, yel);
            line(c, -0.97f, yel);
        }
        if (sW1 > 24)
            for (int k = 0; k < s.lanes; k++) tracks(c, Race.laneX(s.lanes, k), s.lanes, track);
        if (s.stopLine) stripe(c, sX1, sY1, sW1, sB1, sX2, sY2, sW2, sB2, 0f, 1f, 1f, tone(0xFFF0F0EA, 0, fogC, fog));
        if (s.zebra)
            for (float f = -0.84f; f < 0.9f; f += 0.28f) stripe(c, sX1, sY1, sW1, sB1, sX2, sY2, sW2, sB2, f, 0.08f, 0.08f, tone(0xFFF0F0EA, 0, fogC, fog));
        if (s.puddleX < 9f && s.puddleW > 0.02f) {
            // an oily rainbow: three rings of shifting color
            int shift = s.index + (int) (time * 6);
            for (int ring = 0; ring < 3; ring++) {
                float hw = s.puddleW * (1f - ring * 0.33f);
                stripe(c, sX1, sY1, sW1, sB1, sX2, sY2, sW2, sB2, s.puddleX, hw, hw, tone(RAINBOW[(shift + ring * 2) % RAINBOW.length], 0, fogC, fog));
            }
        }
        if (wOn && s.index >= wSeg && s.index < wSeg + Race.WAVE_LEN) {
            // the wave over the road: green water from the sea side to its foaming front
            boolean b = ((s.index + (int) (time * 8)) & 1) == 0;
            float sea = wSide * 9f;
            band(c, Math.min(wFront, sea), Math.max(wFront, sea), tone(b ? 0xFF3A8A9A : 0xFF2E7888, 0, fogC, fog));
            band(c, wFront - 0.12f, wFront + 0.12f, tone(0xFFF0F8FA, 0, fogC, fog));
        }
        if (s.hole != 0) {
            // under the black hole only its light lies on the road
            float f = 1.2f - s.holeRing * 0.25f;
            stripe(c, sX1, sY1, sW1, sB1, sX2, sY2, sW2, sB2, 0f, f, f, tone(0x60FFC870, 0, fogC, fog));
        }
        if (light && !s.checkpoint && marks && !s.zebra && !s.stopLine)
            for (int k = 1; k < s.lanes; k++) line(c, -1f + 2f * k / s.lanes, lane);
    }

    /**
     * A ring of the rabbit hole: no road, no lanes, a faint floor of star dust
     * between two glowing walls, and branch tubes peeling away to the sides.
     */
    private void hole(Canvas c, Track.Seg s, boolean light, int fogC, float fog) {
        int voidC = tone(0xFF06040E, 0, fogC, fog);
        ground(c, -1.32f, -1, voidC);
        ground(c, 1.32f, 1, voidC);
        int floor = tone(light ? 0xFF1E1440 : 0xFF150D30, 0, fogC, fog);
        int wall = tone(light ? 0xFF46E6FF : 0xFFFF46C8, 0, fogC, fog);
        tube(c, -1.3f, 1.3f, floor, wall);
        if (s.forkL >= 0) tube(c, -1.3f - s.forkL - 2.6f, -1.3f - s.forkL, floor, wall);
        if (s.forkR >= 0) tube(c, 1.3f + s.forkR, 1.3f + s.forkR + 2.6f, floor, wall);
        if (sW1 > 12) {
            int dust = tone(0xFFE8E4FF, 0, fogC, fog);
            for (int k = 0; k < 2; k++) {
                int hsh = (s.index * 73856093) ^ (k * 19349663);
                hsh = hsh * 1103515245 + 12345;
                stripe(c, sX1, sY1, sW1, sB1, sX2, sY2, sW2, sB2, ((hsh >>> 8) & 1023) / 1023f * 2.4f - 1.2f, 0.012f, 0.012f, dust);
            }
        }
        if (s.puddleX < 9f && s.puddleW > 0.02f) {
            int shift = s.index + (int) (time * 6);
            for (int ring = 0; ring < 3; ring++) {
                float hw = s.puddleW * (1f - ring * 0.33f);
                stripe(c, sX1, sY1, sW1, sB1, sX2, sY2, sW2, sB2, s.puddleX, hw, hw, tone(RAINBOW[(shift + ring * 2) % RAINBOW.length], 0, fogC, fog));
            }
        }
    }

    /** The drowned street: water between the towers, a lighter channel down the middle, streaks of foam. */
    private void channel(Canvas c, Track.Seg s, boolean light, int fogC, float fog) {
        boolean b = ((s.index + (int) (time * 5)) & 1) == 0;
        int deep = tone(b ? 0xFF22444C : 0xFF1E3E46, 0, fogC, fog);
        ground(c, -1.5f, -1, deep);
        ground(c, 1.5f, 1, deep);
        band(c, -1.5f, 1.5f, tone(b ? 0xFF3A6A70 : 0xFF33626A, 0, fogC, fog));
        int foam = tone(0xFFE8F4F4, 0, fogC, fog);
        band(c, -1.56f, -1.48f, foam);
        band(c, 1.48f, 1.56f, foam);
        if (sW1 > 10) {
            for (int k = 0; k < 3; k++) {
                int hsh = (s.index * 73856093) ^ (k * 19349663) ^ ((int) (time * 3) * 83492791);
                hsh = hsh * 1103515245 + 12345;
                if (((hsh >>> 20) & 3) != 0) continue;
                stripe(c, sX1, sY1, sW1, sB1, sX2, sY2, sW2, sB2, ((hsh >>> 8) & 1023) / 1023f * 2.6f - 1.3f, 0.06f, 0.03f, foam);
            }
        }
    }

    /** The wave coming up behind in the drowned city: a wall of water with a breaking crest. */
    private void waveWall(Canvas c, Race r, int n, float k) {
        double z = r.position + Race.PLAYER_Z - r.policeGap * Track.SEG;
        double cs = z / Track.SEG;
        float pct = (float) (cs - Math.floor(cs));
        float sc = ps[n] + (ps[n + 1] - ps[n]) * pct;
        if (sc <= 0) return;
        float wd = seg[n].width1;
        float x = px[n] + (px[n + 1] - px[n]) * pct, y = py[n] + (py[n + 1] - py[n]) * pct;
        float half = sc * 1.7f * Race.ROAD_W * wd * k, h = sc * 850f * k;
        int slices = 14;
        for (int i = 0; i < slices; i++) {
            float x0 = x - half + 2 * half * i / slices, x1 = x0 + 2 * half / slices + 1;
            float top = y - h * (0.8f + 0.2f * (float) Math.sin(time * 5f + i * 0.9f));
            paint.setColor(0xC02E6E78);
            c.drawRect(x0, top, x1, y, paint);
            paint.setColor(0xC04A9AA4);
            c.drawRect(x0, top + (y - top) * 0.25f, x1, top + (y - top) * 0.4f, paint);
            paint.setColor(0xFFF0F8FA);
            c.drawRect(x0, top - h * 0.06f, x1, top + h * 0.1f, paint);
        }
    }

    /** The billboard under the car, bobbing, with spray off its sides. */
    private void boardUnder(Canvas c, float x, float y, float carW) {
        if (board == null) return;
        float bw = carW * 1.8f, bh = carW * 0.42f;
        float bob = (float) Math.sin(time * 4f) * carW * 0.04f;
        dst.set(x - bw / 2, y - bh * 0.55f + bob, x + bw / 2, y + bh * 0.45f + bob);
        bmpPaint.setAlpha(255);
        c.drawBitmap(board, null, dst, bmpPaint);
        paint.setColor(0xC0F0F8FA);
        for (int i = 0; i < 6; i++) {
            float side = i % 2 == 0 ? -1 : 1;
            float sx = x + side * bw * (0.45f + 0.1f * ((i * 37 + (int) (time * 20)) % 5) / 5f);
            float sy = y + bh * 0.3f - ((i * 13 + (int) (time * 30)) % 9) * carW * 0.02f;
            c.drawRect(sx, sy, sx + 2, sy + 2, paint);
        }
    }

    /** The other road of a fork: a whole carriageway with its curbs and a broken line down the middle. */
    private void carriage(Canvas c, float a, float b, int road, int curb, int lane, boolean dashes) {
        float rb = 1f / 5.5f;
        band(c, a - rb, a, curb);
        band(c, b, b + rb, curb);
        band(c, a, b, road);
        if (dashes) line(c, (a + b) / 2f, lane);
    }

    private void tube(Canvas c, float a, float b, int floor, int wall) {
        band(c, a, b, floor);
        band(c, a, a + 0.06f, wall);
        band(c, b - 0.06f, b, wall);
    }

    /** What lies beside the road on one side: the zone's ground and fields, a park, or a beach and the sea. */
    private void side(Canvas c, Track.Seg s, Theme th, Theme pv, float edge, int dir, int style, int grass,
                      boolean light, float dim, int fogC, float fog) {
        if (style == Track.SIDE_BEACH) {
            int sand = tone(light ? 0xFFE8D29A : 0xFFDCC68C, dim, fogC, fog);
            boolean b = ((s.index + (int) (time * 3)) & 1) == 0;
            float split = edge + dir * 1.4f;
            band(c, Math.min(edge, split), Math.max(edge, split), sand);
            ground(c, split, dir, tone(b ? th.waterA : th.waterB, 0, fogC, fog));
        } else if (style == Track.SIDE_PARK) {
            ground(c, edge, dir, tone(light ? 0xFF5E9E3E : 0xFF52903A, dim, fogC, fog));
        } else {
            int field = tone(seasonal(zc(pv == null ? 0 : fieldOf(pv, light), fieldOf(th, light))), dim, fogC, fog);
            float split = edge + dir * 1.3f;
            band(c, Math.min(edge, split), Math.max(edge, split), grass);
            ground(c, split, dir, field);
        }
    }

    /** Ground from lateral position f out to the screen edge on one side. */
    private void ground(Canvas c, float f, int dir, int color) {
        float g = Math.max(yLo, Math.min(yHi, f));
        float xa1 = sX1 + f * sW1, ya1 = sY1 + g * sB1, xa2 = sX2 + f * sW2, ya2 = sY2 + g * sB2;
        float outer = dir < 0 ? -sw * 0.6f : sw * 1.6f;
        if (dir < 0) quad(c, outer, ya1, xa1, ya1, xa2, ya2, outer, ya2, color);
        else quad(c, xa1, ya1, outer, ya1, outer, ya2, xa2, ya2, color);
    }

    /** A strip of this segment between two lateral positions; the bank lifts it, but only across the paving. */
    private void band(Canvas c, float f0, float f1, int color) {
        float g0 = Math.max(yLo, Math.min(yHi, f0)), g1 = Math.max(yLo, Math.min(yHi, f1));
        quad(c, sX1 + f0 * sW1, sY1 + g0 * sB1, sX1 + f1 * sW1, sY1 + g1 * sB1,
                sX2 + f1 * sW2, sY2 + g1 * sB2, sX2 + f0 * sW2, sY2 + g0 * sB2, color);
    }

    /** A painted line, never thinner than half a pixel. */
    private void line(Canvas c, float f, int color) {
        float h1 = Math.max(0.5f, sW1 * 0.022f) / Math.max(1f, sW1), h2 = Math.max(0.5f, sW2 * 0.022f) / Math.max(1f, sW2);
        stripe(c, sX1, sY1, sW1, sB1, sX2, sY2, sW2, sB2, f, h1, h2, color);
    }

    /** The two darker tyre tracks worn into a lane. */
    private void tracks(Canvas c, float center, int lanes, int color) {
        float off = 0.45f / lanes;
        stripe(c, sX1, sY1, sW1, sB1, sX2, sY2, sW2, sB2, center - off, 0.035f, 0.035f, color);
        stripe(c, sX1, sY1, sW1, sB1, sX2, sY2, sW2, sB2, center + off, 0.035f, 0.035f, color);
    }

    private static final int[] RAINBOW = {0xFFFF5A5A, 0xFFFF9A3A, 0xFFFFE04A, 0xFF6AE06A, 0xFF4AC8FF, 0xFF8A6AFF, 0xFFE06AFF};

    /** A color darkened by shade, then hazed by distance; an empty color stands for the haze itself. */
    private static int tone(int c, float dim, int fogColor, float fog) {
        if (c == 0) c = fogColor;
        if (dim > 0) c = mix(c, 0xFF101418, dim);
        return mix(c, fogColor, fog);
    }

    /** A thin band along the road at lateral position f (-1 left edge, 1 right edge). */
    private void stripe(Canvas c, float X1, float Y1, float W1, float B1, float X2, float Y2, float W2, float B2,
                        float f, float half1, float half2, int color) {
        float a = f - half1, b = f + half1, a2 = f - half2, b2 = f + half2;
        quad(c, X1 + a * W1, Y1 + a * B1, X1 + b * W1, Y1 + b * B1,
                X2 + b2 * W2, Y2 + b2 * B2, X2 + a2 * W2, Y2 + a2 * B2, color);
    }

    private void quad(Canvas c, float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy, int color) {
        path.rewind();
        path.moveTo(ax, ay);
        path.lineTo(bx, by);
        path.lineTo(cx, cy);
        path.lineTo(dx, dy);
        path.close();
        paint.setColor(color);
        c.drawPath(path, paint);
    }

    private void sprite(Canvas c, Bitmap b, float x, float y, float w, float h, float fog) {
        dst.set(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h));
        if (dst.width() < 1 || dst.height() < 1) return;
        bmpPaint.setAlpha((int) (255 * (1f - fog)));
        c.drawBitmap(b, null, dst, bmpPaint);
    }

    // ------------------------------------------------------------ atmosphere

    private static final int[] NIGHT = {0xFF04071A, 0xFF060A22, 0xFF090E2A, 0xFF0C1232, 0xFF10173A, 0xFF151D42, 0xFF1A2348};
    private float dark, overcast, glowAmt, rainAmt, snowAmt, fogAmt;
    private int season;
    private boolean space;
    private final float[] fogT = new float[DRAW + 2];
    private float fogMul = -1f;
    // lights to be lit after the night has been laid over the world
    private final float[] gx = new float[160], gy = new float[160], gr = new float[160];
    private final int[] gc = new int[160];
    private int glows;
    private final Bitmap[] litBmp = new Bitmap[12];
    private final float[] litBox = new float[48], litFog = new float[12];
    private int lit;
    private LightingColorFilter shadeFilter, propFilter;
    private int shadeKey = -1, propKey = -1;

    /** Reads the sky for this frame: how dark, how grey, how wet, how foggy. */
    private void atmosphere(Race r) {
        Atmos a = r.atmos;
        space = r.inSpace;
        if (space || a == null) {
            dark = overcast = glowAmt = rainAmt = snowAmt = fogAmt = 0f;
            season = Atmos.SUMMER;
        } else {
            dark = 1f - a.daylight();
            rainAmt = a.rain();
            snowAmt = a.snow();
            fogAmt = a.fog();
            overcast = Math.max(rainAmt, Math.max(snowAmt * 0.8f, fogAmt));
            glowAmt = a.glow() * (1f - overcast);
            season = a.season;
        }
        float mul = 1f + fogAmt * 3.5f + rainAmt * 0.9f + snowAmt * 0.7f;
        if (Math.abs(mul - fogMul) > 0.02f) {
            fogMul = mul;
            for (int n = 0; n < fogT.length; n++) {
                float d = Math.min(1f, n / (float) DRAW);
                float f = Math.min(1f, 1f - (float) Math.exp(-d * d * 3.2f * mul));
                fogT[n] = (float) Math.floor(f * 7f) / 7f;
            }
        }
        // props darken at night and take the season's color
        int shade = Math.round(255 * (1f - dark * 0.72f));
        int key = shade;
        if (key != shadeKey) {
            shadeKey = key;
            shadeFilter = shade >= 250 ? null : new LightingColorFilter(0xFF000000 | (shade << 16) | (shade << 8) | Math.min(255, shade + 18), 0);
        }
        // how wintry the props look: all winter long, or as much as it is snowing; quantized so the filter is reused
        float frost = Math.max(season == Atmos.WINTER ? 1f : 0f, Math.min(1f, snowAmt * 1.4f));
        int fq = Math.round(frost * 8f);
        int pk = (season * 10 + fq) * 1000 + shade;
        if (pk != propKey) {
            propKey = pk;
            float f = fq / 8f, au = season == Atmos.AUTUMN ? 1f - f : 0f;
            // multiply: white-ish for frost, warm for autumn; add: a little light for snow lying on things
            float mr = 255f - 55f * f, mg = 255f - 47f * f - 41f * au, mb = 255f - 39f * f - 95f * au;
            float ar = 44f * f + 20f * au, ag = 48f * f + 8f * au, ab = 54f * f;
            float k = 1f - dark * 0.72f;
            int mulR = Math.round(mr * k), mulG = Math.round(mg * k), mulB = Math.min(255, Math.round(mb * k + dark * 18f));
            float ka = 1f - dark;
            int addR = Math.round(ar * ka), addG = Math.round(ag * ka), addB = Math.round(ab * ka);
            propFilter = (mulR >= 255 && mulG >= 255 && mulB >= 255 && addR + addG + addB == 0) ? null
                    : new LightingColorFilter(0xFF000000 | (mulR << 16) | (mulG << 8) | mulB, (addR << 16) | (addG << 8) | addB);
        }
    }

    /** The fog color of the moment: grey under cloud, deep blue at night. */
    private int air(int fog) {
        if (overcast > 0) fog = mix(fog, 0xFF8E969E, overcast * 0.7f);
        if (dark > 0) fog = mix(fog, 0xFF0A0E26, dark * 0.9f);
        return fog;
    }

    /** Grass and fields through the season: gold in autumn, white in winter or snow. */
    private int seasonal(int c) {
        if (space) return c;
        // the season's own color first, then snow over it, as much as there is
        if (season == Atmos.AUTUMN) c = mix(c, 0xFFB8924A, 0.45f);
        else if (season == Atmos.SPRING) c = mix(c, 0xFF7ACC4A, 0.22f);
        float snow = Math.max(season == Atmos.WINTER ? 0.8f : 0f, snowAmt * 0.75f);
        return snow > 0 ? mix(c, 0xFFE6ECF2, snow) : c;
    }

    private void glow(float x, float y, float r, int color) {
        if (glows >= gx.length) return;
        gx[glows] = x;
        gy[glows] = y;
        gr[glows] = r;
        gc[glows] = color;
        glows++;
    }

    /** Night over the world, then every recorded light on top of it. */
    private void night(Canvas c, int w, int h) {
        int veil = (int) (dark * 190 + rainAmt * 40);
        if (veil > 4) c.drawColor((Math.min(230, veil) << 24) | 0x080C28);
        if (fogAmt > 0.05f) c.drawColor(((int) (fogAmt * 110) << 24) | (air(0xFFB8C4CC) & 0xFFFFFF));
        if (dark < 0.15f) {
            glows = 0;
            lit = 0;
            return;
        }
        for (int i = 0; i < lit; i++) {
            float a = (1f - litFog[i]) * Math.min(1f, dark * 1.4f);
            sprite(c, litBmp[i], litBox[i * 4], litBox[i * 4 + 1], litBox[i * 4 + 2], litBox[i * 4 + 3], 1f - a);
        }
        lit = 0;
        for (int i = 0; i < glows; i++) {
            paint.setColor(((int) (dark * 80) << 24) | (gc[i] & 0xFFFFFF));
            c.drawCircle(gx[i], gy[i], gr[i] * 2.8f, paint);
            paint.setColor(gc[i] | 0xFF000000);
            c.drawCircle(gx[i], gy[i], Math.max(0.8f, gr[i]), paint);
        }
        glows = 0;
    }

    /** Rain streaks or snowflakes over everything, in screen space. */
    private void precipitation(Canvas c, int w, int h, Race r) {
        if (rainAmt > 0.02f) {
            int n = (int) (150 * rainAmt);
            stroke.setStrokeWidth(1);
            stroke.setColor(0x90C8D6E6);
            float slant = -2f - r.steer * 3f;
            for (int i = 0; i < n; i++) {
                int hsh = i * 1103515245 + 12345;
                float x = (((hsh >>> 8) & 1023) / 1023f * (w + 40) + time * 30f * slant) % (w + 40);
                if (x < 0) x += w + 40;
                float y = (((hsh >>> 18) & 1023) / 1023f * h + time * (700f + (i % 5) * 60f)) % h;
                c.drawLine(x - 20, y, x - 20 + slant, y + 7, stroke);
            }
        }
        if (snowAmt > 0.02f) {
            int n = (int) (120 * snowAmt);
            for (int i = 0; i < n; i++) {
                int hsh = i * 1103515245 + 54321;
                float sp = 0.5f + ((hsh >>> 4) & 15) / 15f;
                float x = (((hsh >>> 8) & 1023) / 1023f * w + (float) Math.sin(time * 1.3f + i) * 6f - r.steer * time * 8f) % w;
                if (x < 0) x += w;
                float y = (((hsh >>> 18) & 1023) / 1023f * h + time * 55f * sp) % h;
                paint.setColor(i % 3 == 0 ? 0xFFFFFFFF : 0xD0E8F0FF);
                float s = sp > 1.1f ? 2f : 1f;
                c.drawRect(Math.round(x), Math.round(y), Math.round(x) + s, Math.round(y) + s, paint);
            }
        }
    }

    // ------------------------------------------------------------ backdrop

    private void backdrop(Canvas c, int w, int h, float horizon, Race r) {
        int pIdx = r.playerSegment();
        Track.Seg ps = r.track.get(pIdx);
        Theme th = ps.theme, prev = ps.prev;
        float t = prev == null ? 1f : Math.min(1f, (pIdx - ps.zoneStart) / (float) Track.FADE);
        int bands = th.sky.length;
        for (int i = 0; i < bands; i++) {
            int col = t >= 1f ? th.sky[i] : mix(prev.sky[i], th.sky[i], t);
            if (overcast > 0) col = mix(col, 0xFF8A9298, overcast * 0.65f);
            if (glowAmt > 0) col = mix(col, 0xFFFF9A50, glowAmt * 0.45f * i / (bands - 1f));
            if (dark > 0) col = mix(col, NIGHT[i * NIGHT.length / bands], dark);
            paint.setColor(col);
            float top = i == 0 ? -h * 0.3f : (int) (i * horizon / bands);
            c.drawRect(-w * 0.5f, top, w * 1.5f, (int) ((i + 1) * horizon / bands) + 1, paint);
        }
        if (dark > 0.4f && overcast < 0.5f) {
            // stars come out when the sky is dark and clear
            paint.setColor(((int) (Math.min(1f, (dark - 0.4f) * 2.5f) * 220) << 24) | 0xFFFFF0);
            for (int i = 0; i < 60; i++) {
                int hsh = i * 1103515245 + 777;
                float x = ((hsh >>> 8) & 1023) / 1023f * w * 1.6f - w * 0.3f;
                float y = ((hsh >>> 18) & 1023) / 1023f * horizon * 0.85f;
                c.drawRect(Math.round(x), Math.round(y), Math.round(x) + 1, Math.round(y) + 1, paint);
            }
        }
        paint.setColor(t >= 1f ? th.grassDark : mix(prev.grassDark, th.grassDark, t));
        c.drawRect(-w * 0.5f, horizon, w * 1.5f, h * 1.3f, paint);
        float s = w < 150 ? w / 220f : 1f;
        layer(c, w, clouds, r.bgCloud, s, horizon * 0.22f + 40 * s, 255);
        if (t < 1f) {
            layer(c, w, prev.far, r.bgFar, s, horizon + 1, 255);
            layer(c, w, prev.mid, r.bgMid, s, horizon + 1, 255);
        }
        int a = (int) (255 * t);
        layer(c, w, th.far, r.bgFar, s, horizon + 1, a);
        layer(c, w, th.mid, r.bgMid, s, horizon + 1, a);
    }

    private void layer(Canvas c, int w, Bitmap b, float off, float s, float bottom, int alpha) {
        if (b == null || alpha <= 0) return;
        bmpPaint.setAlpha(alpha);
        float lw = b.getWidth() * s, lh = b.getHeight() * s;
        float o = off - (float) Math.floor(off);
        float x = -o * lw - lw;
        while (x < w * 1.5f) {
            dst.set(Math.round(x), Math.round(bottom - lh), Math.round(x + lw), Math.round(bottom));
            c.drawBitmap(b, null, dst, bmpPaint);
            x += lw;
        }
    }

    // --------------------------------------------------------------- gauge

    /** Speedometer: needle for speed, the orange rim for flow, the digit for the gear. Colors come from the skin. */
    void gauge(Canvas c, float cx, float cy, float rad, Race r, boolean gearDigit, int[] k) {
        if (k == null) k = Skin.ALL[0].c;
        paint.setColor(k[Skin.G_RIM]);
        c.drawCircle(cx, cy, rad + 1, paint);
        paint.setColor(k[Skin.G_FILL]);
        c.drawCircle(cx, cy, rad - 1, paint);

        stroke.setStrokeWidth(2);
        stroke.setColor(k[Skin.G_TRACK]);
        dst.set(cx - rad, cy - rad, cx + rad, cy + rad);
        c.drawArc(dst, 135, 270, false, stroke);
        if (r.flow > 0.01f) {
            stroke.setColor(r.flow > 0.8f ? k[Skin.G_FLOW_HOT] : k[Skin.G_FLOW]);
            c.drawArc(dst, 135, 270 * r.flow, false, stroke);
        }
        stroke.setStrokeWidth(Math.max(2, rad / 8));
        stroke.setColor(k[Skin.G_RED]);
        float ri = rad - 4;
        dst.set(cx - ri, cy - ri, cx + ri, cy + ri);
        c.drawArc(dst, 135 + 270 * 0.8f, 270 * 0.2f, false, stroke);

        stroke.setStrokeWidth(1);
        int ticks = rad > 16 ? 20 : 10;
        for (int i = 0; i <= ticks; i++) {
            double a = Math.toRadians(135 + 270.0 * i / ticks);
            float len = (i % 2 == 0 || ticks == 10) ? 4 : 2;
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
            stroke.setColor(k[Skin.G_TICK]);
            c.drawLine(cx + ca * (rad - 2), cy + sa * (rad - 2), cx + ca * (rad - 2 - len), cy + sa * (rad - 2 - len), stroke);
        }
        float frac = Math.min(1f, r.speed / Race.VMAX);
        double a = Math.toRadians(135 + 270.0 * frac);
        stroke.setStrokeWidth(rad > 16 ? 2 : 1);
        stroke.setColor(k[Skin.G_NEEDLE]);
        c.drawLine(cx, cy, cx + (float) Math.cos(a) * (rad - 5), cy + (float) Math.sin(a) * (rad - 5), stroke);
        paint.setColor(k[Skin.G_HUB]);
        c.drawCircle(cx, cy, Math.max(2, rad / 8), paint);
        if (gearDigit) {
            String g = String.valueOf(r.gear);
            int s = rad > 20 ? 2 : 1;
            PixelFont.draw(c, paint, g, (int) (cx - PixelFont.width(g, s) / 2f), (int) (cy + rad * 0.35f), s, k[Skin.G_TICK]);
        }
    }

    static int mix(int a, int b, float t) {
        if (t <= 0) return a;
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }
}
