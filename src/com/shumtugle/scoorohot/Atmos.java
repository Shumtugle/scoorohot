package com.shumtugle.scoorohot;

import java.util.Random;

/**
 * The sky over a race: the hour, the season and the weather. A whole day
 * passes in eight minutes of driving. The season is drawn once per race.
 * Weather comes in spells of a minute or two and fades in and out; where it
 * falls depends on the season and the country: no snow in the desert, snow
 * all the time in the taiga, rain often in the jungle. Fog is its own spell.
 */
final class Atmos {
    static final int SPRING = 0, SUMMER = 1, AUTUMN = 2, WINTER = 3;
    static final int CLEAR = 0, RAIN = 1, SNOW = 2, FOG = 3;
    static final String[] SEASONS = {"SPRING", "SUMMER", "AUTUMN", "WINTER"};
    static final String[] WEATHERS = {"", "RAIN", "SNOW", "FOG"};
    private static final float DAY_SECONDS = 480f;

    final int season;
    float hour;            // 0 to 24
    int weather = CLEAR;   // the spell now, or the one fading out
    float strength;        // 0 to 1, how much of it there is
    private int next = CLEAR;
    private float spell;
    private final Random rnd;

    Atmos(long seed) {
        rnd = new Random(seed * 31 + 7);
        season = rnd.nextInt(4);
        // most races start in daylight; some at dusk or deep in the night
        float r = rnd.nextFloat();
        hour = r < 0.65f ? 8f + rnd.nextFloat() * 9f : r < 0.85f ? 17f + rnd.nextFloat() * 4f : 21f + rnd.nextFloat() * 8f;
        if (hour >= 24f) hour -= 24f;
        spell = 15f + rnd.nextFloat() * 40f;
    }

    void update(float dt, Theme zone) {
        hour += dt * 24f / DAY_SECONDS;
        if (hour >= 24f) hour -= 24f;
        spell -= dt;
        if (spell <= 0) {
            next = pick(zone);
            spell = 40f + rnd.nextFloat() * 80f;
        }
        // in the taiga it is always snowing a little; the desert dries everything but fog
        if (zone.id == Theme.TAIGA && next != SNOW) next = SNOW;
        if (zone.id == Theme.DESERT && (next == SNOW || next == RAIN)) next = CLEAR;
        if (next != weather) {
            strength -= dt / 6f;
            if (strength <= 0) {
                strength = 0;
                weather = next;
            }
        } else if (weather != CLEAR) {
            strength = Math.min(1f, strength + dt / 6f);
        }
    }

    private int pick(Theme zone) {
        float r = rnd.nextFloat();
        if (zone.id == Theme.JUNGLE) return r < 0.5f ? RAIN : r < 0.65f ? FOG : CLEAR;
        if (zone.id == Theme.DESERT) return r < 0.1f ? FOG : CLEAR;
        switch (season) {
            case WINTER:
                return r < 0.45f ? SNOW : r < 0.6f ? FOG : CLEAR;
            case AUTUMN:
                return r < 0.35f ? RAIN : r < 0.55f ? FOG : CLEAR;
            case SPRING:
                return r < 0.3f ? RAIN : r < 0.4f ? FOG : CLEAR;
            default:
                return r < 0.2f ? RAIN : CLEAR;
        }
    }

    /** 1 in full day, 0 in deep night, with a slow dawn and dusk between. */
    float daylight() {
        float h = hour;
        if (h >= 7f && h <= 18.5f) return 1f;
        if (h > 18.5f && h < 21f) return 1f - smooth((h - 18.5f) / 2.5f);
        if (h > 4.5f && h < 7f) return smooth((h - 4.5f) / 2.5f);
        return 0f;
    }

    /** How much warm low sun there is: at dawn and at dusk. */
    float glow() {
        float h = hour;
        float a = Math.max(0f, 1f - Math.abs(h - 19.3f) / 1.8f);
        float b = Math.max(0f, 1f - Math.abs(h - 6f) / 1.6f);
        return Math.max(a, b);
    }

    float rain() {
        return weather == RAIN ? strength : 0f;
    }

    float snow() {
        return weather == SNOW ? strength : 0f;
    }

    float fog() {
        return weather == FOG ? strength : 0f;
    }

    /** How much the weather loosens the tyres: wet and snowy roads hold less. */
    float grip() {
        return 1f - 0.12f * rain() - 0.18f * snow();
    }

    /** The label shown in the corner: time, and weather if any. */
    String label() {
        int h = (int) hour, m = (int) ((hour - h) * 60);
        String t = (h < 10 ? "0" : "") + h + ":" + (m < 10 ? "0" : "") + m;
        String w = strength > 0.3f ? WEATHERS[weather] : "";
        return SEASONS[season] + " " + t + (w.length() > 0 ? " " + w : "");
    }

    private static float smooth(float t) {
        return t * t * (3 - 2 * t);
    }
}
