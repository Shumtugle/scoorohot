package com.shumtugle.scoorohot;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Settings, and the rallies the player has driven: each kept for good with
 * its seed, so the very same road can be driven again, its best result and
 * how many times it was tried. Newest first.
 */
final class Prefs {
    static final int KEEP = 12;
    private final SharedPreferences sp;
    int steering = 1, vehicle;
    boolean vibration = true, sound = true, mph;

    /** One rally: its kind, its road, the best so far (seconds for time, km for distance; 0 if never finished). */
    static final class Rally {
        int type;
        long seed;
        float best;
        int runs;
    }

    Prefs(Context c) {
        sp = c.getSharedPreferences("scoorohot", Context.MODE_PRIVATE);
        steering = sp.getInt("steering", 1);
        vehicle = sp.getInt("vehicle", 0);
        vibration = sp.getBoolean("vibration", true);
        sound = sp.getBoolean("sound", true);
        mph = sp.getBoolean("mph", false);
    }

    void save() {
        sp.edit().putInt("steering", steering).putInt("vehicle", vehicle)
                .putBoolean("vibration", vibration).putBoolean("sound", sound).putBoolean("mph", mph).apply();
    }

    ArrayList<Rally> rallies() {
        ArrayList<Rally> list = new ArrayList<Rally>();
        String s = sp.getString("rallies", "");
        if (s.length() == 0) return list;
        for (String e : s.split(";")) {
            String[] f = e.split(",");
            if (f.length < 4) continue;
            try {
                Rally r = new Rally();
                r.type = Integer.parseInt(f[0]);
                r.seed = Long.parseLong(f[1]);
                r.best = Float.parseFloat(f[2]);
                r.runs = Integer.parseInt(f[3]);
                list.add(r);
            } catch (RuntimeException ignored) {
            }
        }
        return list;
    }

    /**
     * Records a run of a rally and moves it to the top of the list. result is
     * 0 for a run that did not finish. Returns the rally's best before this run.
     */
    float record(int type, long seed, float result) {
        ArrayList<Rally> list = rallies();
        Rally r = null;
        for (int i = 0; i < list.size(); i++)
            if (list.get(i).type == type && list.get(i).seed == seed) r = list.remove(i);
        if (r == null) {
            r = new Rally();
            r.type = type;
            r.seed = seed;
        }
        float before = r.best;
        r.runs++;
        if (result > 0 && (r.best <= 0 || better(type, result, r.best))) r.best = result;
        list.add(0, r);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < list.size() && i < KEEP; i++) {
            Rally x = list.get(i);
            if (b.length() > 0) b.append(';');
            b.append(String.format(Locale.US, "%d,%d,%.2f,%d", x.type, x.seed, x.best, x.runs));
        }
        sp.edit().putString("rallies", b.toString()).apply();
        return before;
    }

    /** Less time is better in a time rally; more kilometres in a distance rally. */
    static boolean better(int type, float a, float b) {
        return type == Race.RALLY_TIME ? a < b : a > b;
    }
}
