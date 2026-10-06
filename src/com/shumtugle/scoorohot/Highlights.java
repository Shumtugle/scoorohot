package com.shumtugle.scoorohot;

import android.graphics.Bitmap;
import android.graphics.Canvas;

import java.util.Locale;

/**
 * After a rally the game shows a few "photos" of it, as if it had been
 * watching. They are not recordings: the road just driven is searched for its
 * most striking places (the sharpest bend, the highest crest, a river or a
 * change of country), and each is staged afresh with the player's car in it,
 * seen from a camera above and behind, and captioned like a race report.
 */
final class Highlights {
    final Bitmap[] shots;
    final String[] captions;

    private Highlights(Bitmap[] shots, String[] captions) {
        this.shots = shots;
        this.captions = captions;
    }

    static Highlights of(Race src, Garage garage, float[] trafficWidths, Renderer renderer, int w, int h) {
        Track t = src.track;
        int last = Math.max(80, Math.min(src.playerSegment(), src.finishSeg > 0 ? src.finishSeg : Integer.MAX_VALUE));
        int bend = -1, crest = -1, special = -1;
        float bendC = 0, crestV = 0;
        String specialText = null;
        for (int i = 60; i < last - 10; i++) {
            Track.Seg s = t.get(i);
            float c = Math.abs(s.curve);
            if (c > bendC) {
                bendC = c;
                bend = i;
            }
            float before = slope(t.get(i - 8)), after = slope(t.get(i + 8));
            if (before - after > crestV) {
                crestV = before - after;
                crest = i;
            }
            if (special < 0) {
                if (s.bridge) specialText = "OVER THE RIVER";
                else if (s.water) specialText = "THROUGH THE FORD";
                else if (s.cross) specialText = "ACROSS THE CROSSROADS";
                else if (s.prev != null && i - s.zoneStart == 30) specialText = "INTO NEW COUNTRY";
                if (specialText != null) special = i;
            }
        }
        int[] at = {bend, crest, special};
        String[] what = {"THE SHARPEST BEND", "OVER THE TOP", specialText};
        int n = 0;
        for (int i = 0; i < 3; i++) if (at[i] > 0) n++;
        if (n == 0) {
            at[0] = last / 2;
            what[0] = "FLAT OUT";
            n = 1;
        }
        Bitmap[] shots = new Bitmap[n];
        String[] caps = new String[n];
        int k = 0;
        for (int i = 0; i < 3 && k < n; i++) {
            if (at[i] <= 0) continue;
            Track.Seg s = t.get(at[i]);
            // a speed the place would allow: what the car would plausibly have done there
            float vc = Math.abs(s.curve) > 0.05f ? Math.min(1f, 0.85f / (Race.CENTRIFUGAL * Math.abs(s.curve))) : 0.9f;
            int kmh = Math.round(vc * Race.VMAX_KMH * (0.92f + 0.06f * (i % 2)));
            caps[k] = what[i] + String.format(Locale.US, " / %.1f KM / %d KM/H", at[i] * Race.KM_PER_SEG, kmh);
            shots[k] = stage(src, garage, trafficWidths, renderer, at[i], vc, w, h);
            k++;
        }
        return new Highlights(shots, caps);
    }

    private static float slope(Track.Seg s) {
        return (s.y2 - s.y1) / Track.SEG;
    }

    /** One staged photo: the car at segment at, in its lane, leaning into the road, seen from above. */
    private static Bitmap stage(Race src, Garage garage, float[] widths, Renderer renderer, int at, float vc, int w, int h) {
        Race r = new Race(src.track, widths, true, src.track.hashCode() + at);
        r.vehicle = src.vehicle;
        r.vehicleW = src.vehicleW;
        r.atmos.hour = src.atmos.hour;
        r.position = at * (double) Track.SEG - Race.PLAYER_Z;
        r.speed = vc * Race.VMAX;
        r.update(1 / 60f);
        Track.Seg s = src.track.get(at);
        r.position = at * (double) Track.SEG - Race.PLAYER_Z;
        r.playerX = Race.laneX(s.lanes, 0);
        r.steer = Math.max(-1f, Math.min(1f, s.curve * 0.25f));
        r.roll = -s.curve * 1.6f;
        r.chaseBlend = 0.55f;
        r.cars.clear();
        r.oncoming.clear();
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        renderer.render(new Canvas(b), w, h, r, garage, false);
        Skin.ALL[Skin.DUSK].apply(b, r.clock, 0.36f);
        return b;
    }
}
