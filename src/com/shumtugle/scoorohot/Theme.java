package com.shumtugle.scoorohot;

import android.graphics.Bitmap;

/** Colors and backdrop strips of one kind of country. */
final class Theme {
    static final int COAST = 0, CITY = 1, COUNTRY = 2, JUNGLE = 3, DESERT = 4, TAIGA = 5, SAFARI = 6, SPACE = 7, FLOOD = 8;

    final int id;
    final int[] sky;          // bands from the top of the screen down to the horizon
    int fog;
    int grassLight, grassDark;
    int roadLight, roadDark, tyreTrack, lane;
    int curbLeftA, curbLeftB, curbRightA, curbRightB;
    float fieldStart;         // in road half-widths; beyond it the verge turns into fields (0: none)
    int fieldA, fieldB;
    int waterA, waterB;       // rivers under bridges and fords
    boolean laneMarks = true;
    int lanes = 2, oncoming = 2;   // lanes each way on a two-way stretch
    float laneScale = 1f;          // dirt tracks have wider lanes
    int roadside = 0;              // ground just beyond the curb when it differs from the grass (0: same)
    Bitmap far, mid;

    private Theme(int id, int[] sky) {
        this.id = id;
        this.sky = sky;
    }

    private Theme road(int light, int dark, int track, int lane) {
        roadLight = light;
        roadDark = dark;
        tyreTrack = track;
        this.lane = lane;
        return this;
    }

    private Theme ground(int fog, int light, int dark) {
        this.fog = fog;
        grassLight = light;
        grassDark = dark;
        return this;
    }

    private Theme curbs(int la, int lb, int ra, int rb) {
        curbLeftA = la;
        curbLeftB = lb;
        curbRightA = ra;
        curbRightB = rb;
        return this;
    }

    private Theme water(int a, int b) {
        waterA = a;
        waterB = b;
        return this;
    }

    private Theme lanes(int ours, int theirs, float scale) {
        lanes = ours;
        oncoming = theirs;
        laneScale = scale;
        return this;
    }

    private Theme backdrop(Bitmap far, Bitmap mid) {
        this.far = far;
        this.mid = mid;
        return this;
    }

    static Theme coast(Sprites s) {
        return new Theme(COAST, new int[]{0xFF1670D2, 0xFF1C80DC, 0xFF2490E4, 0xFF309EEA, 0xFF42ACEE, 0xFF58BAF2, 0xFF70C6F4})
                .ground(0xFF94CDEE, 0xFF8CC43E, 0xFF7AB234)
                .road(0xFF6E7176, 0xFF66696E, 0xFF5B5E63, 0xFFE8E8E4)
                .curbs(0xFFC8382E, 0xFFEEEEEA, 0xFF2C5CC8, 0xFFEEEEEA)
                .water(0xFF2F86CC, 0xFF2672B8)
                .lanes(2, 2, 1f)
                .backdrop(s.coastFar, s.coastMid);
    }

    static Theme city(Sprites s) {
        return new Theme(CITY, new int[]{0xFF1A78D4, 0xFF2286DE, 0xFF2C94E4, 0xFF3CA2EA, 0xFF52AEEE, 0xFF6ABCF0, 0xFF84C8F2})
                .ground(0xFFB0D2E8, 0xFF86AE40, 0xFF749C36)
                .road(0xFF6A6D72, 0xFF62656A, 0xFF575A5F, 0xFFE8E8E4)
                .curbs(0xFFC8382E, 0xFFEEEEEA, 0xFF2C5CC8, 0xFFEEEEEA)
                .water(0xFF3A7AA8, 0xFF306A96)
                .lanes(3, 3, 1f)
                .backdrop(s.cityFar, s.cityMid);
    }

    /** Farmland: a green verge, then wheat and pasture in long bands. */
    static Theme country(Sprites s) {
        Theme t = new Theme(COUNTRY, new int[]{0xFF2078D0, 0xFF2A88DA, 0xFF3698E2, 0xFF48A6E8, 0xFF60B4EC, 0xFF7CC2EE, 0xFF9ACEEE})
                .ground(0xFFB8D6E2, 0xFF9CC64A, 0xFF88B23E)
                .road(0xFF717378, 0xFF686A70, 0xFF5E6065, 0xFFEAE6D8)
                .curbs(0xFFEEEEEA, 0xFF8A8A86, 0xFFEEEEEA, 0xFF8A8A86)
                .water(0xFF3A88C4, 0xFF2E78B2)
                .lanes(2, 2, 1f)
                .backdrop(s.countryFar, s.countryMid);
        t.fieldStart = 2.3f;
        t.fieldA = 0xFFD8BC54;
        t.fieldB = 0xFF7EB23E;
        return t;
    }

    /** A red dirt road through rain forest; the verges are wet and green. */
    static Theme jungle(Sprites s) {
        Theme t = new Theme(JUNGLE, new int[]{0xFF2A82BE, 0xFF3490C6, 0xFF409CCC, 0xFF52A8D0, 0xFF68B4D4, 0xFF80C0D6, 0xFF9CCCD6})
                .ground(0xFFA6C8BC, 0xFF3E8A34, 0xFF327A2C)
                .road(0xFF9A6A44, 0xFF8E603C, 0xFF7C5232, 0xFFAE8460)
                .curbs(0xFF5E7A2E, 0xFF4E6A28, 0xFF5E7A2E, 0xFF4E6A28)
                .water(0xFF3E8C88, 0xFF327C78)
                .lanes(1, 1, 1.6f)
                .backdrop(s.jungleFar, s.jungleMid);
        t.laneMarks = false;
        return t;
    }

    /** Arizona: orange sand, red mesas, saguaros; a long two-lane highway. */
    static Theme desert(Sprites s) {
        return new Theme(DESERT, new int[]{0xFF2E7CC8, 0xFF3C8CD0, 0xFF52A0D8, 0xFF6EB2DC, 0xFF92C2D8, 0xFFBCCCC8, 0xFFE2CCA8})
                .ground(0xFFE8C8A0, 0xFFDC9A54, 0xFFCE8C48)
                .road(0xFF6E6A66, 0xFF66625E, 0xFF5C5854, 0xFFF0D060)
                .curbs(0xFFEEEEEA, 0xFFC8703A, 0xFFEEEEEA, 0xFFC8703A)
                .water(0xFF3A8AB8, 0xFF2E7AA8)
                .lanes(2, 2, 1f)
                .backdrop(s.desertFar, s.desertMid);
    }

    /** Siberian taiga in winter: snow to the horizon, dark firs, one lane each way. */
    static Theme taiga(Sprites s) {
        return new Theme(TAIGA, new int[]{0xFF6A86A8, 0xFF7890B0, 0xFF8A9EBA, 0xFF9CACC2, 0xFFB0BECC, 0xFFC4CED6, 0xFFD6DEE2})
                .ground(0xFFD8E0E6, 0xFFEEF2F6, 0xFFDCE4EC)
                .road(0xFF5E6268, 0xFF575B61, 0xFF4C5056, 0xFFDCE2E8)
                .curbs(0xFFFFFFFF, 0xFFB8C4D0, 0xFFFFFFFF, 0xFFB8C4D0)
                .water(0xFF4A6E8A, 0xFF3E6078)
                .lanes(1, 1, 1.3f)
                .backdrop(s.taigaFar, s.taigaMid);
    }

    /** African savanna: golden grass, acacias, a red dirt track. */
    static Theme safari(Sprites s) {
        Theme t = new Theme(SAFARI, new int[]{0xFF3A8AC8, 0xFF4A98CE, 0xFF5EA6D2, 0xFF78B4D4, 0xFF96C2D2, 0xFFB8CCC8, 0xFFDCD4B4})
                .ground(0xFFE0D2A8, 0xFFD8BE66, 0xFFCAB05A)
                .road(0xFFA8683C, 0xFF9C5E36, 0xFF8A522E, 0xFFB87A4E)
                .curbs(0xFFB89A50, 0xFFA88A46, 0xFFB89A50, 0xFFA88A46)
                .water(0xFF5A8A7A, 0xFF4E7C6C)
                .lanes(1, 1, 1.6f)
                .backdrop(s.safariFar, s.safariMid);
        t.laneMarks = false;
        return t;
    }

    /** The rabbit hole between the stars, where the aliens chase you. */
    static Theme space(Sprites s) {
        return new Theme(SPACE, new int[]{0xFF020108, 0xFF05030F, 0xFF080418, 0xFF0C0622, 0xFF12082E, 0xFF1A0C3A, 0xFF241048})
                .ground(0xFF1A0C32, 0xFF0A0616, 0xFF07040F)
                .road(0xFF1E1440, 0xFF180F36, 0xFF2A1E56, 0xFF46E6FF)
                .curbs(0xFFFF46C8, 0xFF3A1060, 0xFF46E6FF, 0xFF10305A)
                .water(0xFF100828, 0xFF0C0620)
                .lanes(2, 0, 1.2f)
                .backdrop(s.spaceFar, s.spaceMid);
    }

    /** A drowned city: grey-green water between walls of towers, under a storm sky. */
    static Theme flood(Sprites s) {
        return new Theme(FLOOD, new int[]{0xFF2A3440, 0xFF34404C, 0xFF404C58, 0xFF4C5864, 0xFF5A6670, 0xFF68747C, 0xFF78848A})
                .ground(0xFF5A6A70, 0xFF2A4A50, 0xFF244248)
                .road(0xFF3A6A70, 0xFF336268, 0xFF2C585E, 0xFFD8ECEC)
                .curbs(0xFFE8F4F4, 0xFF6A9AA0, 0xFFE8F4F4, 0xFF6A9AA0)
                .water(0xFF2A5A62, 0xFF244E56)
                .lanes(2, 0, 1.2f)
                .backdrop(s.cityFar, s.cityMid);
    }
}
