package com.shumtugle.scoorohot;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The whole game lives in one surface: a render thread paints a small
 * framebuffer and blows it up to the screen with whole-number pixels.
 * Touches travel to that thread through a queue.
 */
final class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    private static final int MENU = 0, OPTIONS = 1, SCORES = 2, RACE = 3;
    private static final int EV_DOWN = 0, EV_MOVE = 1, EV_UP = 2, EV_BACK = 3;
    private static final int B_START = 1, B_SCORES = 2, B_OPTIONS = 3, B_EXIT = 4, B_BACK = 5,
            B_STEER = 6, B_VIBRATION = 7, B_UNITS = 8, B_RESUME = 9, B_MENU = 10, B_AGAIN = 11,
            B_PAUSE = 12, B_SOUND = 13, B_PREV_CAR = 14, B_NEXT_CAR = 15, B_PREV_MODE = 16, B_NEXT_MODE = 17,
            B_PREVIEW = 20, B_DRIVE = 22;
    private static final int B_CONTINUE = 23, B_BANNER = 24, B_PIT = 25, B_REPAIR = 26, B_ROW = 40;
    private static final String[] RALLY_NAMES = {"", "TIME RALLY", "DISTANCE RALLY"};
    private static final String[] RALLY_LINES = {"", "5 KM ON THE CLOCK", "3 MIN, GO FAR"};
    private static final String[] STEER_NAMES = {"SOFT", "NORMAL", "SHARP"};
    private static final float[] STEER_SENS = {0.85f, 1f, 1.2f};
    private static final float[] STEER_RANGE = {0.22f, 0.17f, 0.13f};
    private static final String VERSION = "V1.2";
    private static final String TITLE = "SCOOROHOT'78";

    private static final class Btn {
        final int id, x, y, w, h;

        Btn(int id, int x, int y, int w, int h) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        boolean hit(float px, float py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    private final MainActivity act;
    private final Prefs prefs;
    private Sprites sprites;
    private Garage garage;
    private Theme[] themes;
    private Theme spaceTheme, floodTheme;
    private Bitmap emblem;
    private final Object surfaceLock = new Object();
    private final ConcurrentLinkedQueue<float[]> events = new ConcurrentLinkedQueue<float[]>();
    private volatile boolean running, surfaceReady;
    private volatile int viewW, viewH;
    private Thread thread;
    private int trackedPointer = -1;

    private int pix = 1, W, H;
    private Bitmap fb;
    private Canvas fc;
    private final Paint blit = new Paint();
    private final Paint paint = new Paint();
    private final Rect blitDst = new Rect();
    private final Renderer mainRenderer = new Renderer();
    private final Renderer previewRenderer = new Renderer();
    private Bitmap previewBmp;
    private Canvas previewCanvas;
    private Race demo;
    private final Sound sound = new Sound();
    // a change of skin is a dissolve, not a cut
    private int shownSkin = Skin.DUSK, fromSkin = Skin.DUSK;
    private float fadeStart = -9f;
    private Bitmap fadeBmp;
    private Canvas fadeCanvas;
    private final Paint fadePaint = new Paint();

    private int screen = MENU;
    private Race race;
    private boolean paused, resultSaved;
    private int resultRank = -1;
    // rally offers that slide in while driving free
    private float offerTimer = 40f, offerShow;
    private int offerType = Race.RALLY_TIME;
    private final java.util.Random offers = new java.util.Random();
    private long rallySeed;
    private Highlights highlights;
    private float bestBefore;
    private final Renderer photoRenderer = new Renderer();
    private float stationClock;
    private boolean stationWas;
    private float clock;
    private boolean touching, steeringTouch;
    private float touchX, touchY, anchorX, downX, downY;
    private final ArrayList<Btn> buttons = new ArrayList<Btn>();

    GameView(MainActivity act) {
        super(act);
        this.act = act;
        this.prefs = new Prefs(act);
        getHolder().addCallback(this);
        setFocusable(true);
        blit.setFilterBitmap(false);
        blit.setAntiAlias(false);
        paint.setAntiAlias(false);
    }

    // ------------------------------------------------------------ lifecycle

    void resumeLoop() {
        if (running) return;
        running = true;
        thread = new Thread(this, "game");
        thread.start();
        sound.start();
    }

    void pauseLoop() {
        running = false;
        sound.stop();
        if (thread != null) {
            try {
                thread.join(1500);
            } catch (InterruptedException ignored) {
            }
            thread = null;
        }
        if (screen == RACE && race != null && race.state != Race.DONE) paused = true;
    }

    void backPressed() {
        events.add(new float[]{EV_BACK, 0, 0});
    }

    @Override
    public void surfaceCreated(SurfaceHolder h) {
        synchronized (surfaceLock) {
            surfaceReady = true;
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder h, int format, int w, int hh) {
        viewW = w;
        viewH = hh;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder h) {
        synchronized (surfaceLock) {
            surfaceReady = false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                trackedPointer = e.getPointerId(0);
                events.add(new float[]{EV_DOWN, e.getX(), e.getY()});
                break;
            case MotionEvent.ACTION_MOVE: {
                int idx = e.findPointerIndex(trackedPointer);
                if (idx >= 0) events.add(new float[]{EV_MOVE, e.getX(idx), e.getY(idx)});
                break;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                int idx = e.getActionIndex();
                if (e.getPointerId(idx) == trackedPointer) {
                    events.add(new float[]{EV_UP, e.getX(idx), e.getY(idx)});
                    trackedPointer = -1;
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (trackedPointer >= 0) {
                    int idx = Math.max(0, e.findPointerIndex(trackedPointer));
                    events.add(new float[]{EV_UP, e.getX(idx), e.getY(idx)});
                }
                trackedPointer = -1;
                break;
            default:
                break;
        }
        return true;
    }

    // ----------------------------------------------------------------- loop

    @Override
    public void run() {
        initAssets();
        long last = System.nanoTime();
        while (running) {
            if (!surfaceReady || viewW <= 0) {
                sleep(20);
                last = System.nanoTime();
                continue;
            }
            long now = System.nanoTime();
            float dt = Math.min(0.05f, (now - last) / 1e9f);
            last = now;
            ensureBuffer();
            processEvents();
            update(dt);
            draw();
            synchronized (surfaceLock) {
                if (!surfaceReady) continue;
                Canvas c = null;
                try {
                    c = getHolder().lockHardwareCanvas();
                } catch (RuntimeException ex) {
                    c = null;
                }
                if (c == null) {
                    sleep(10);
                    continue;
                }
                try {
                    c.drawColor(0xFF000000);
                    blitDst.set(0, 0, W * pix, H * pix);
                    c.drawBitmap(fb, null, blitDst, blit);
                } finally {
                    getHolder().unlockCanvasAndPost(c);
                }
            }
        }
    }

    private void initAssets() {
        if (sprites != null) return;
        sprites = new Sprites();
        garage = new Garage();
        // traffic pictures for every size are rendered in the background; far cars come first
        Thread warm = new Thread(new Runnable() {
            @Override
            public void run() {
                garage.prewarm();
            }
        }, "garage");
        warm.setPriority(Thread.MIN_PRIORITY);
        warm.start();
        themes = new Theme[]{Theme.coast(sprites), Theme.city(sprites), Theme.country(sprites), Theme.jungle(sprites),
                Theme.desert(sprites), Theme.taiga(sprites), Theme.safari(sprites)};
        spaceTheme = Theme.space(sprites);
        floodTheme = Theme.flood(sprites);
        mainRenderer.setBoard(sprites.billboard);
        mainRenderer.setArt(sprites.vortex, sprites.blackHole);
        mainRenderer.setFinds(sprites.heart, sprites.wingsItem, sprites.wingL, sprites.wingR);
        previewRenderer.setArt(sprites.vortex, sprites.blackHole);
        emblem = Emblem.make();
        mainRenderer.setClouds(sprites.clouds);
        previewRenderer.setClouds(sprites.clouds);
        prefs.vehicle = Math.max(0, Math.min(garage.vehicles.length - 1, prefs.vehicle));
        demo = new Race(new Track(themes, sprites, 1978, false), garage.trafficWidths, true, 3);
        mount(demo);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    private void ensureBuffer() {
        int vw = viewW, vh = viewH;
        int p = Math.max(1, Math.round(vw / 215f));
        int w = (vw + p - 1) / p, h = (vh + p - 1) / p;
        if (fb != null && w == W && h == H && p == pix) return;
        pix = p;
        W = w;
        H = h;
        fb = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        fc = new Canvas(fb);
    }

    // ---------------------------------------------------------------- input

    private float steerRange() {
        return W * STEER_RANGE[prefs.steering];
    }

    private void processEvents() {
        float[] e;
        while ((e = events.poll()) != null) {
            int type = (int) e[0];
            if (type == EV_BACK) {
                onBack();
                continue;
            }
            float x = e[1] / pix, y = e[2] / pix;
            if (type == EV_DOWN) {
                touching = true;
                downX = x;
                downY = y;
                touchX = x;
                touchY = y;
                anchorX = x;
                steeringTouch = screen == RACE && !paused && race != null && race.state != Race.DONE && !race.hangarOpen && hit(x, y) != B_PAUSE && hit(x, y) != B_BANNER && hit(x, y) != B_PIT;
            } else if (type == EV_MOVE) {
                touchX = x;
                touchY = y;
                if (steeringTouch) {
                    // the wheel floats with the finger: overshoot drags the center along
                    float r = steerRange();
                    if (x - anchorX > r) anchorX = x - r;
                    if (anchorX - x > r) anchorX = x + r;
                }
            } else if (type == EV_UP) {
                boolean wasSteering = steeringTouch;
                touching = false;
                steeringTouch = false;
                if (!wasSteering && Math.abs(x - downX) < 8 && Math.abs(y - downY) < 8) tap(x, y);
            }
        }
    }

    private float steerValue() {
        if (!touching || !steeringTouch) return 0f;
        float v = (touchX - anchorX) / steerRange();
        return Math.max(-1f, Math.min(1f, v));
    }

    private int hit(float x, float y) {
        for (int i = buttons.size() - 1; i >= 0; i--) if (buttons.get(i).hit(x, y)) return buttons.get(i).id;
        return 0;
    }

    private void tap(float x, float y) {
        int id = hit(x, y);
        switch (id) {
            case B_START:
            case B_PREVIEW:
            case B_CONTINUE:
                startFree();
                break;
            case B_REPAIR:
                if (race != null) race.repair();
                break;
            case B_PIT:
                if (race != null) race.pit(race.track.nextStationSeg(race.playerSegment()));
                break;
            case B_BANNER:
                offerShow = 0f;
                startRally(offerType, System.nanoTime());
                break;
            case B_SCORES:
                screen = SCORES;
                break;
            case B_OPTIONS:
                screen = OPTIONS;
                break;
            case B_EXIT:
                act.quit();
                break;
            case B_BACK:
                screen = MENU;
                break;
            case B_STEER:
                prefs.steering = (prefs.steering + 1) % 3;
                prefs.save();
                break;
            case B_VIBRATION:
                prefs.vibration = !prefs.vibration;
                prefs.save();
                if (prefs.vibration) act.buzz(30);
                break;
            case B_PREV_CAR:
            case B_NEXT_CAR: {
                int n = garage.vehicles.length;
                prefs.vehicle = (prefs.vehicle + (id == B_NEXT_CAR ? 1 : n - 1)) % n;
                prefs.save();
                mount(demo);
                if (race != null && race.hangarOpen) mount(race);
                break;
            }
            case B_DRIVE:
                if (race != null) race.leaveHangar();
                break;
            case B_SOUND:
                prefs.sound = !prefs.sound;
                prefs.save();
                break;
            case B_UNITS:
                prefs.mph = !prefs.mph;
                prefs.save();
                break;
            case B_PAUSE:
                paused = true;
                break;
            case B_RESUME:
                paused = false;
                break;
            case B_MENU:
                screen = MENU;
                race = null;
                paused = false;
                break;
            case B_AGAIN:
                if (race != null && race.rally()) startRally(race.mode, rallySeed);
                else startFree();
                break;
            default:
                if (id >= B_ROW) {
                    // a rally from the list: the same road again
                    java.util.ArrayList<Prefs.Rally> list = prefs.rallies();
                    if (id - B_ROW < list.size()) startRally(list.get(id - B_ROW).type, list.get(id - B_ROW).seed);
                }
                break;
        }
    }

    private void onBack() {
        if (screen == RACE) {
            if (race == null || race.state == Race.DONE) {
                screen = MENU;
                race = null;
            } else {
                paused = !paused;
            }
        } else if (screen == MENU) {
            act.quit();
        } else {
            screen = MENU;
        }
    }

    /** A free ride on a road nobody has driven. */
    private void startFree() {
        begin(System.nanoTime(), Race.FREE);
    }

    /** A rally: its own road from its own seed, so repeating it means the very same road. */
    private void startRally(int type, long seed) {
        rallySeed = seed;
        begin(seed, type);
    }

    private void begin(long seed, int mode) {
        race = new Race(new Track(themes, sprites, seed, true), garage.trafficWidths, false, seed ^ 0x5DEECE66DL);
        mount(race);
        race.spaceTrack = new Track(new Theme[]{spaceTheme}, sprites, seed ^ 0x2545F4914F6CDD1DL, false);
        race.floodTrack = new Track(new Theme[]{floodTheme}, sprites, seed ^ 0x6A09E667F3BCC909L, false);
        race.setMode(mode);
        race.sens = STEER_SENS[prefs.steering];
        screen = RACE;
        paused = false;
        resultSaved = false;
        resultRank = -1;
        highlights = null;
        offerShow = 0f;
        offerTimer = 45f + offers.nextFloat() * 60f;
        shownSkin = fromSkin = Skin.DUSK;
        touching = false;
        steeringTouch = false;
    }

    /** Puts the chosen vehicle into a race and gives the engine its voice. */
    private void mount(Race r) {
        Garage.Model v = garage.vehicles[prefs.vehicle];
        r.vehicle = prefs.vehicle;
        r.vehicleW = v.worldW;
        r.hovers = v.hovers;
        r.toughness = Garage.toughness(prefs.vehicle);
        // another car at the pumps comes in new: the mechanics see to the one left behind
        if (r.hangarOpen) r.health = 1f;
        sound.voice = v.voice;
    }

    // --------------------------------------------------------------- update

    private void update(float dt) {
        clock += dt;
        boolean audible = false;
        if (screen == MENU) {
            demo.update(dt);
        } else if (screen == RACE && race != null && !paused) {
            race.steerInput = steerValue();
            race.touching = touching && steeringTouch;
            // a drag down is the brake: a little dead zone, full force a finger's width further
            race.brake = race.touching ? Math.max(0f, Math.min(1f, (touchY - downY - 6f) / 28f)) : 0f;
            race.update(dt);
            if (race.buzz > 0) {
                if (prefs.vibration) act.buzz(race.buzz);
                race.buzz = 0;
            }
            if (race.state == Race.DONE && !resultSaved) {
                if (race.rally()) {
                    float result = race.dnf || !race.finished ? 0f : race.mode == Race.RALLY_TIME ? race.elapsed : race.distanceKm;
                    bestBefore = prefs.record(race.mode, rallySeed, result);
                    if (!race.dnf) highlights = Highlights.of(race, garage, garage.trafficWidths, photoRenderer, W - 36, (int) (H * 0.2f));
                }
                resultSaved = true;
            }
            // in a free ride, now and then a rally is offered in the corner
            boolean calm = race.mode == Race.FREE && race.state == Race.RUN && !race.chase && !race.inSpace
                    && !race.hangarOpen && race.badgeTime <= 0 && race.abductTime <= 0 && race.washTime <= 0 && !race.waveOn;
            if (offerShow > 0) offerShow -= dt;
            else if (calm) {
                offerTimer -= dt;
                if (offerTimer <= 0) {
                    offerType = offers.nextBoolean() ? Race.RALLY_TIME : Race.RALLY_DIST;
                    offerShow = 8f;
                    offerTimer = 50f + offers.nextFloat() * 70f;
                }
            }
            if (!calm && offerShow > 0) offerShow = Math.min(offerShow, 0.4f);
            // the siren is for the police only; the saucer comes in silence
            sound.sirenVol = prefs.sound && race.chase && !race.alien && !race.flood && race.state != Race.DONE
                    ? (race.distant ? 0.03f : 0.16f) : 0f;
            sound.rainVol = !prefs.sound ? 0f : race.flood ? 0.3f : !race.inSpace ? race.atmos.rain() * 0.35f + race.atmos.snow() * 0.08f : 0f;
            if (prefs.sound && race.state != Race.DONE) {
                audible = true;
                float sp = race.speed / Race.VMAX;
                sound.engineHz = race.engineHz();
                sound.engineVol = 0.16f + 0.14f * sp;
                boolean rough = (race.offroad || race.inWater) && race.speed > 1f;
                sound.rumbleVol = rough ? 0.15f + 0.45f * sp : 0f;
                sound.cue(race.cues);
            }
            race.cues = 0;
        }
        if (!audible) {
            sound.engineVol = 0f;
            sound.rumbleVol = 0f;
            if (screen != RACE || paused) {
                sound.sirenVol = 0f;
                sound.rainVol = 0f;
            }
        }
    }

    // ----------------------------------------------------------------- draw

    private void draw() {
        buttons.clear();
        switch (screen) {
            case MENU:
                drawMenu();
                break;
            case OPTIONS:
                drawOptions();
                break;
            case SCORES:
                drawScores();
                break;
            default:
                drawRace();
                break;
        }
    }

    /** Dusk is the game's look; in a race the world can fall into another for a while. */
    private Skin skin() {
        if (screen == RACE && race != null) return Skin.ALL[shownSkin];
        return Skin.ALL[Skin.DUSK];
    }

    private int col(int slot) {
        return skin().c[slot];
    }

    private void drawGrid() {
        fc.drawColor(col(Skin.BG));
        paint.setColor(col(Skin.GRID));
        int step = 14;
        int off = (int) (clock * 6) % step;
        for (int x = (W % step) / 2; x < W; x += step) fc.drawRect(x, 0, x + 1, H, paint);
        for (int y = off - step; y < H; y += step) fc.drawRect(0, y, W, y + 1, paint);
        for (int i = 0; i < 40; i++) {
            int x = (i * 7919 + 13) % W, y = (i * 104729 + 7) % H;
            boolean on = ((int) (clock * 2 + i) % 5) != 0;
            paint.setColor(on ? col(Skin.STAR) : col(Skin.STAR_DIM));
            fc.drawRect(x, y, x + 1, y + 1, paint);
        }
    }

    /** The coat of arms at the left with the chosen car on its road, the name beside it: lopsided on purpose. */
    private void header(int y) {
        int ex = 6;
        fc.drawBitmap(emblem, ex, y + 2, blit);
        Garage.Sprite car = garage.vehicles[prefs.vehicle].views(16)[2][0][0];
        fc.drawBitmap(car.bmp, ex + 30 - car.ax, y + 2 + 47 - car.ay, blit);
        int scale = 2;
        int tx = ex + Emblem.W + 8;
        int tw = PixelFont.width(TITLE, scale);
        if (tx + tw > W - 4) tx = W - 4 - tw;
        int tyy = y + 14;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= scale; dy++)
                PixelFont.draw(fc, paint, TITLE, tx + dx, tyy + dy, scale, dy >= 1 ? col(Skin.TITLE_SHADOW) : col(Skin.TITLE_INK));
        PixelFont.draw(fc, paint, TITLE, tx, tyy, scale, 0, skin().title);
        textShadow("A NEW ROAD EVERY RIDE", tx, tyy + 22, 1, col(Skin.DIM));
    }

    private void drawTitle(int y) {
        int scale = PixelFont.width(TITLE, 3) <= W - 8 ? 3 : 2;
        int x = (W - PixelFont.width(TITLE, scale)) / 2;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= scale; dy++)
                PixelFont.draw(fc, paint, TITLE, x + dx, y + dy, scale, dy >= 1 ? col(Skin.TITLE_SHADOW) : col(Skin.TITLE_INK));
        PixelFont.draw(fc, paint, TITLE, x, y, scale, 0, skin().title);
    }

    private void drawMenu() {
        drawGrid();
        int bh = 26, bw = (W - 5 * 5) / 4;
        int pw = W - 12;
        java.util.ArrayList<Prefs.Rally> list = prefs.rallies();
        int rows = Math.min(3, list.size());
        // one window onto the road; title, window, the garage, the rallies and the buttons form one centered block
        int fixed = 52 + 10 + 30 + (rows > 0 ? 14 + rows * 20 : 12) + 14 + bh;
        int ph = Math.max((int) (pw * 0.7f), Math.min((int) (pw * 1.25f), (int) (H * 0.9f) - fixed));
        int ty = Math.max((int) (H * 0.04f), (H - fixed - ph) / 2);
        int by = ty + fixed - bh + ph;
        header(ty);
        int top = ty + 52 + 10;
        int px = 6, iw = pw - 4, ih = ph - 4;
        if (previewBmp == null || previewBmp.getWidth() != iw || previewBmp.getHeight() != ih) {
            previewBmp = Bitmap.createBitmap(iw, ih, Bitmap.Config.ARGB_8888);
            previewCanvas = new Canvas(previewBmp);
        }
        previewRenderer.render(previewCanvas, iw, ih, demo, garage, true);
        skin().apply(previewBmp, clock, 0.36f);
        paint.setColor(((int) (clock * 3)) % 2 == 0 ? col(Skin.FRAME) : col(Skin.FRAME_ALT));
        fc.drawRect(px, top, px + pw, top + ph, paint);
        paint.setColor(col(Skin.BG));
        fc.drawRect(px + 1, top + 1, px + pw - 1, top + ph - 1, paint);
        fc.drawBitmap(previewBmp, px + 2, top + 2, blit);
        buttons.add(new Btn(B_PREVIEW, px, top, pw, ph));
        int cx = W / 2;
        int sy = top + ph + 4;
        arrow(B_PREV_CAR, px, sy, false);
        arrow(B_NEXT_CAR, px + pw - 22, sy, true);
        textC(garage.vehicles[prefs.vehicle].name, cx, sy + 5, 1, col(Skin.ACCENT));

        int ry = sy + 34;
        if (rows > 0) {
            textC("YOUR RALLIES", cx, ry, 1, col(Skin.TEXT));
            ry += 12;
            for (int i = 0; i < rows; i++) {
                rallyRow(list.get(i), i, ry);
                ry += 20;
            }
        }
        button(B_START, 5, by, bw, bh, "DRIVE", null);
        button(B_SCORES, 5 + (bw + 5), by, bw, bh, "RALLIES", null);
        button(B_OPTIONS, 5 + 2 * (bw + 5), by, bw, bh, "OPTIONS", null);
        button(B_EXIT, 5 + 3 * (bw + 5), by, bw, bh, "EXIT", null);
        text(VERSION, W - PixelFont.width(VERSION, 1) - 3, H - 9, 1, col(Skin.FAINT));
    }

    /** One rally in a list: its kind, its best, and a button to drive that same road again. */
    private void rallyRow(Prefs.Rally r, int i, int y) {
        String kind = r.type == Race.RALLY_TIME ? "TIME" : "DIST";
        String best = r.best <= 0 ? "DNF" : r.type == Race.RALLY_TIME ? Race.clockText(r.best) : dist(r.best);
        text(kind, 8, y + 5, 1, col(Skin.DIM));
        text(best, 40, y + 5, 1, col(Skin.TEXT));
        text("X" + r.runs, 104, y + 5, 1, col(Skin.FAINT));
        button(B_ROW + i, W - 74, y, 68, 17, "REPEAT", null);
    }

    private void drawOptions() {
        drawGrid();
        textC("OPTIONS", W / 2, (int) (H * 0.07f), 2, col(Skin.ACCENT));
        int y = (int) (H * 0.15f);
        int bw = W - 24;
        button(B_STEER, 12, y, bw, 28, "STEERING: " + STEER_NAMES[prefs.steering], null);
        button(B_VIBRATION, 12, y + 36, bw, 28, "VIBRATION: " + (prefs.vibration ? "ON" : "OFF"), null);
        button(B_SOUND, 12, y + 72, bw, 28, "SOUND: " + (prefs.sound ? "ON" : "OFF"), null);
        button(B_UNITS, 12, y + 108, bw, 28, "SPEED: " + (prefs.mph ? "MPH" : "KM/H"), null);
        String[] help = {
                "DRAG LEFT OR RIGHT TO STEER.",
                "THE CAR PICKS ITS OWN SPEED.",
                "A STEADY LINE BUILDS FLOW,",
                "AND FLOW MULTIPLIES POINTS.",
                "",
                "FINGER DOWN: THE CAR DRIVES.",
                "FINGER UP: IT ROLLS AND SLOWS.",
                "DRAG DOWN TO BRAKE. BRAKE HARD",
                "AT FULL SPEED AND YOU SPIN.",
                "STEER HARD INTO A CROSSROADS",
                "AT LOW SPEED TO TURN.",
                "HIT A POLICE CAR, OR A CAR",
                "AT A RED LIGHT: THE POLICE.",
                "",
                "KEEP AN EYE ON THE ROAD.",
                "IT IS NOT ALWAYS A ROAD.",
        };
        int hy = y + 148;
        for (int i = 0; i < help.length; i++) textC(help[i], W / 2, hy + i * 11, 1, col(Skin.DIM));
        button(B_BACK, (W - 80) / 2, H - 26 - Math.max(10, (int) (H * 0.04f)), 80, 26, "BACK", null);
    }

    /** Every rally driven, newest first, each one repeatable. */
    private void drawScores() {
        drawGrid();
        textC("RALLIES", W / 2, (int) (H * 0.07f), 2, col(Skin.ACCENT));
        java.util.ArrayList<Prefs.Rally> list = prefs.rallies();
        int y = (int) (H * 0.14f);
        if (list.isEmpty()) {
            textC("NONE YET.", W / 2, y + 10, 1, col(Skin.TEXT));
            textC("WHILE YOU DRIVE, A RALLY IS", W / 2, y + 26, 1, col(Skin.DIM));
            textC("SOMETIMES OFFERED IN THE CORNER.", W / 2, y + 37, 1, col(Skin.DIM));
            textC("TAP IT TO TAKE IT ON.", W / 2, y + 48, 1, col(Skin.DIM));
        }
        int bottom = H - 26 - Math.max(10, (int) (H * 0.04f)) - 10;
        for (int i = 0; i < list.size() && y + 20 < bottom; i++) {
            rallyRow(list.get(i), i, y);
            y += 22;
        }
        button(B_BACK, (W - 80) / 2, H - 26 - Math.max(10, (int) (H * 0.04f)), 80, 26, "BACK", null);
    }

    private void drawRace() {
        mainRenderer.render(fc, W, H, race, garage, false);
        int want = race.skin();
        if (want != shownSkin) {
            fromSkin = shownSkin;
            shownSkin = want;
            fadeStart = clock;
        }
        float fade = (clock - fadeStart) / 1.2f;
        if (fade < 1f && fromSkin != shownSkin) {
            // the old look laid over the new one, thinning out
            if (fadeBmp == null || fadeBmp.getWidth() != W || fadeBmp.getHeight() != H) {
                fadeBmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
                fadeCanvas = new Canvas(fadeBmp);
            }
            fadeCanvas.drawBitmap(fb, 0, 0, null);
            Skin.ALL[fromSkin].apply(fadeBmp, race.clock, 0.36f);
            Skin.ALL[shownSkin].apply(fb, race.clock, 0.36f);
            fadePaint.setAlpha((int) (255 * (1f - fade)));
            fc.drawBitmap(fadeBmp, 0, 0, fadePaint);
        } else {
            Skin.ALL[shownSkin].apply(fb, race.clock, 0.36f);
        }
        if (race.pan > 0) whipPan();
        if (race.hitFlash > 0) fc.drawColor(((int) (race.hitFlash / 0.3f * 90) << 24) | 0xC02010);
        if (race.warpFlash > 0) fc.drawColor(((int) (race.warpFlash / 0.6f * 220) << 24) | 0xF4ECFF);

        // the top line: what this mode counts
        if (race.chase) {
            // the gap to the police, as a row of blocks that runs out
            String head = race.busted ? (race.flood ? "WIPEOUT" : race.alien ? "ABDUCTED" : "BUSTED") : race.flood ? "WAVE" : race.alien ? "ESCAPE" : "CHASE";
            textShadow(head, W / 2 - PixelFont.width(head, 2) / 2, 6, 2,
                    ((int) (race.clock * 4)) % 2 == 0 ? col(Skin.G_RED) : col(Skin.ACCENT));
            int blocks = Math.max(0, Math.min(10, Math.round(race.policeGap / Race.CHASE_GAP * 10f)));
            int bx = W / 2 - 10 * 5 / 2;
            for (int i = 0; i < 10; i++) {
                paint.setColor(i < blocks ? col(Skin.ACCENT) : col(Skin.SHADOW));
                fc.drawRect(bx + i * 5, 24, bx + i * 5 + 4, 29, paint);
            }
        } else if (race.mode == Race.FREE) {
            int sec = (int) race.elapsed;
            String ts = String.format(Locale.US, "%d:%02d", sec / 60, sec % 60);
            textShadow("FREE", W / 2 - PixelFont.width("FREE", 1) / 2, 6, 1, col(Skin.ACCENT));
            textShadow(ts, W / 2 - PixelFont.width(ts, 2) / 2, 16, 2, col(Skin.TEXT));
        } else if (race.mode == Race.RALLY_TIME) {
            String ts = Race.clockText(race.elapsed);
            textShadow("RALLY", W / 2 - PixelFont.width("RALLY", 1) / 2, 6, 1, col(Skin.ACCENT));
            textShadow(ts, W / 2 - PixelFont.width(ts, 2) / 2, 16, 2, col(Skin.TEXT));
            float left = Math.max(0f, (race.finishSeg - race.playerSegment()) * Race.KM_PER_SEG);
            String to = String.format(Locale.US, "%.1f KM TO GO", left);
            textShadow(to, W / 2 - PixelFont.width(to, 1) / 2, 34, 1, col(Skin.DIM));
        } else {
            int t = (int) Math.ceil(race.time);
            boolean low = t <= 10 && race.state == Race.RUN;
            String ts = String.format(Locale.US, "%d:%02d", t / 60, t % 60);
            textShadow("RALLY", W / 2 - PixelFont.width("RALLY", 1) / 2, 6, 1, col(Skin.ACCENT));
            textShadow(ts, W / 2 - PixelFont.width(ts, 2) / 2, 16, 2, low && ((int) (race.clock * 4)) % 2 == 0 ? col(Skin.G_RED) : col(Skin.TEXT));
        }
        textShadow(prefs.mph ? "MI" : "KM", 6, 6, 1, col(Skin.DIM));
        textShadow(String.format(Locale.US, "%.1f", prefs.mph ? race.distanceKm * 0.6214f : race.distanceKm), 6, 16, 2, col(Skin.TEXT));
        if (offerShow > 0) banner();
        pitButton();

        // the hour, the season and the weather, small in the corner
        if (!race.inSpace) {
            String sky = race.atmos.label();
            textShadow(sky, W - PixelFont.width(sky, 1) - 4, 27, 1, col(Skin.DIM));
        }

        // pause button
        paint.setColor(0x88000000);
        fc.drawRect(W - 20, 5, W - 6, 21, paint);
        paint.setColor(col(Skin.TEXT));
        fc.drawRect(W - 17, 8, W - 14, 18, paint);
        fc.drawRect(W - 12, 8, W - 9, 18, paint);
        buttons.add(new Btn(B_PAUSE, W - 30, 0, 30, 30));

        int rad = 26;
        int gcx = rad + 6, gcy = H - rad - Math.max(8, (int) (H * 0.03f));
        mainRenderer.gauge(fc, gcx, gcy, rad, race, true, skin().c);
        float kmh = race.speed / Race.VMAX * Race.VMAX_KMH;
        int shown = Math.round(prefs.mph ? kmh * 0.6214f : kmh);
        textShadow(String.valueOf(shown), gcx + rad + 6, gcy + rad - 22, 2, col(Skin.TEXT));
        textShadow(prefs.mph ? "MPH" : "KM/H", gcx + rad + 6, gcy + rad - 6, 1, col(Skin.DIM));
        if (!race.inSpace) {
            // the fuel gauge: green, then amber, then a blinking red
            int fx = gcx + rad + 6, fy = gcy - 12;
            textShadow("FUEL", fx, fy - 9, 1, col(Skin.DIM));
            // the car's condition, the same kind of gauge just above
            int cy0 = fy - 22;
            textShadow("CAR", fx, cy0 - 9, 1, col(Skin.DIM));
            paint.setColor(col(Skin.SHADOW));
            fc.drawRect(fx, cy0, fx + 44, cy0 + 4, paint);
            paint.setColor(race.health > 0.6f ? 0xFF4AD07A : race.health > 0.3f ? 0xFFFFC22A
                    : ((int) (race.clock * 4)) % 2 == 0 ? 0xFFFF3A2A : 0xFF7A1A14);
            fc.drawRect(fx, cy0, fx + 44 * Math.min(1f, race.health), cy0 + 4, paint);
            if (race.health > 1f) {
                // above a hundred: the extra a heart gave, in gold
                paint.setColor(0xFFFFD24A);
                fc.drawRect(fx + 44, cy0 - 1, fx + 44 + 44 * (race.health - 1f), cy0 + 5, paint);
            }
            // how far to the next pumps, always in sight
            int st = race.track.nextStationSeg(race.playerSegment());
            float km = Math.max(0, st - race.playerSegment()) * Race.KM_PER_SEG;
            String gas = km < 1f ? "GAS " + Math.round(km * 10) * 100 + " M" : String.format(Locale.US, "GAS %.1f KM", km);
            textShadow(gas, fx, fy + 7, 1, race.fuel < 0.25f ? col(Skin.ACCENT) : col(Skin.DIM));
            paint.setColor(col(Skin.SHADOW));
            fc.drawRect(fx, fy, fx + 44, fy + 4, paint);
            int fc0 = race.fuel > 0.4f ? 0xFF4AD07A : race.fuel > 0.2f ? 0xFFFFC22A
                    : ((int) (race.clock * 4)) % 2 == 0 ? 0xFFFF3A2A : 0xFF7A1A14;
            paint.setColor(fc0);
            fc.drawRect(fx, fy, fx + 44 * race.fuel, fy + 4, paint);
        }

        redLight();
        hints();
        if (race.state == Race.COUNT) {
            String n = String.valueOf((int) Math.ceil(race.countdown));
            textShadow(n, W / 2 - PixelFont.width(n, 5) / 2, (int) (H * 0.26f), 5, col(Skin.ACCENT));
        } else if (race.messageTime > 0 && race.message.length() > 0) {
            int s = PixelFont.width(race.message, 2) <= W - 10 ? 2 : 1;
            textShadow(race.message, W / 2 - PixelFont.width(race.message, s) / 2, (int) (H * 0.24f), s, col(Skin.ACCENT));
        }

        if (race.badgeTime > 0) drawBadge();
        if (race.hangarOpen && !stationWas) stationClock = clock;
        stationWas = race.hangarOpen;
        if (race.hangarOpen) drawHangar();
        if (paused) drawPause();
        else if (race.state == Race.DONE) drawResult();
    }

    /** The police badge, held up for a moment before a chase so it is clear what has happened. */
    private void drawBadge() {
        boolean red = ((int) (clock * 6)) % 2 == 0;
        fc.drawColor(red ? 0xA0400008 : 0xA0000840);
        int y = (int) (H * 0.18f) + (int) (Math.sin(clock * 8) * 2);
        int bh;
        if (race.flood) {
            // the billboard the car has landed on
            Bitmap b = sprites.billboard;
            int bw = Math.min(W - 40, b.getWidth() * 3), bhh = bw * b.getHeight() / b.getWidth();
            blitDst.set((W - bw) / 2, y, (W + bw) / 2, y + bhh);
            fc.drawBitmap(b, null, blitDst, blit);
            bh = bhh;
        } else if (race.alien) {
            // a saucer instead of a badge: it is not the police this time
            Garage.Sprite u = garage.vehicles[Garage.UFO_INDEX].single(Math.min(160, W - 40), 10f, (int) (clock * 6));
            fc.drawBitmap(u.bmp, W / 2 - u.ax, y + u.bmp.getHeight() - (u.bmp.getHeight() - u.ay), blit);
            bh = u.bmp.getHeight();
        } else {
            Bitmap b = sprites.badge;
            int scale = W >= 180 ? 2 : 1;
            int bw = b.getWidth() * scale;
            bh = b.getHeight() * scale;
            blitDst.set((W - bw) / 2, y, (W + bw) / 2, y + bh);
            fc.drawBitmap(b, null, blitDst, blit);
        }
        textC(race.flood ? "WASHED AWAY" : race.alien ? "ABDUCTED" : "POLICE", W / 2, y + bh + 10, race.flood ? 2 : 3, col(Skin.ACCENT));
        textC(race.chaseReason, W / 2, y + bh + 38, 1, col(Skin.TEXT));
        textC(race.flood ? "STAY AHEAD OF THE WAVE" : race.alien ? "RUN FOR THE END OF THE HOLE" : "THEY ARE COMING FOR YOU", W / 2, y + bh + 52, 1, col(Skin.DIM));
    }

    /**
     * A turn of the head: the frame slides away against the turn, smeared, and
     * comes round again from the other side. At its fastest the road ahead is
     * replaced, and nothing can be seen of it.
     */
    private void whipPan() {
        float t = 1f - race.pan / Race.PAN;
        float sh = (float) Math.sin(Math.PI * t) * W * 0.8f * -race.panDir;
        if (fadeBmp == null || fadeBmp.getWidth() != W || fadeBmp.getHeight() != H) {
            fadeBmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
            fadeCanvas = new Canvas(fadeBmp);
        }
        fadeCanvas.drawBitmap(fb, 0, 0, null);
        float wrap = sh > 0 ? sh - W : sh + W;
        fadePaint.setAlpha(255);
        fc.drawBitmap(fadeBmp, Math.round(sh), 0, fadePaint);
        fc.drawBitmap(fadeBmp, Math.round(wrap), 0, fadePaint);
        fadePaint.setAlpha(90);
        fc.drawBitmap(fadeBmp, Math.round(sh * 0.7f), 0, fadePaint);
        fc.drawBitmap(fadeBmp, Math.round(sh * 0.4f), 0, fadePaint);
        float k = (float) Math.sin(Math.PI * t);
        paint.setColor(((int) (k * 120) << 24) | 0xFFFFFF);
        for (int i = 0; i < 26; i++) {
            int hsh = i * 1103515245 + (int) (clock * 40) * 12345;
            int y = ((hsh >>> 8) & 1023) * H / 1024, len = (int) (k * (20 + ((hsh >>> 4) & 63)));
            int x = ((hsh >>> 18) & 1023) * W / 1024;
            fc.drawRect(x, y, x + len, y + 1, paint);
        }
    }

    /**
     * From the first GAS sign on, a button to pull in at the coming station.
     * Tapped, it goes away and the driver does the rest when the station comes;
     * left alone, it goes away once the station is passed.
     */
    private void pitButton() {
        if (race.pitIntent || race.chase || race.inSpace || race.hangarOpen || race.state != Race.RUN || race.badgeTime > 0) return;
        int p = race.playerSegment(), st = race.track.nextStationSeg(p);
        if (st - p > Track.GAS_SIGNS[0] + 20 || st - p < 4) return;
        // small, green, up out of the thumb's way, and breathing so it is noticed
        float breath = 0.5f + 0.5f * (float) Math.sin(clock * 3.2f);
        int grow = Math.round(breath * 1.5f);
        int bw = 88, bh = 24, x = W - bw - 6, y = 84;
        paint.setColor(Skin.mix(0xFF1E5A30, 0xFF8AF0A0, breath));
        fc.drawRect(x - grow, y - grow, x + bw + grow, y + bh + grow, paint);
        paint.setColor(0xFF1E7A3A);
        fc.drawRect(x + 2 - grow, y + 2 - grow, x + bw - 2 + grow, y + bh - 2 + grow, paint);
        // a little pump
        paint.setColor(0xFFF0F0EA);
        fc.drawRect(x + 5, y + 5, x + 11, y + 19, paint);
        paint.setColor(0xFF1E7A3A);
        fc.drawRect(x + 6, y + 7, x + 10, y + 10, paint);
        text("PULL IN", x + 15, y + 4, 1, 0xFFF4F4E8);
        float km = (st - p) * Race.KM_PER_SEG;
        String d = km < 1f ? "GAS " + Math.round(km * 10) * 100 + " M" : String.format(Locale.US, "GAS %.1f KM", km);
        text(d, x + 15, y + 13, 1, 0xFFB8F0C4);
        buttons.add(new Btn(B_PIT, x, y, bw, bh));
    }

    /** The offer of a rally, sliding in at the right, holding, sliding out; a tap takes it. */
    private void banner() {
        float t = 8f - offerShow;
        float in = Math.min(1f, Math.min(t / 0.4f, offerShow / 0.4f));
        int bw = 118, bh = 36;
        int x = W - 4 - (int) (bw * in), y = 40;
        paint.setColor(col(Skin.FRAME));
        fc.drawRect(x, y, x + bw, y + bh, paint);
        paint.setColor(col(Skin.PANEL));
        fc.drawRect(x + 2, y + 2, x + bw - 2, y + bh - 2, paint);
        text(RALLY_NAMES[offerType], x + 6, y + 6, 1, col(Skin.ACCENT));
        text(RALLY_LINES[offerType], x + 6, y + 16, 1, col(Skin.DIM));
        if (((int) (clock * 3)) % 2 == 0) text("TAP TO RACE", x + 6, y + 26, 1, col(Skin.TEXT));
        buttons.add(new Btn(B_BANNER, x, y, bw, bh));
    }

    /** What can be done right here: turn at a crossroads, take a branch, pull off to the garage. */
    private void hints() {
        if (race.state != Race.RUN || race.badgeTime > 0) return;
        String t = null;
        int p = race.playerSegment();
        float front = (float) ((race.position + Race.PLAYER_Z) / Track.SEG);
        if (race.inSpace) {
            Track.Seg a = race.track.get(p + 2);
            float g = a.forkL >= 0 ? a.forkL : a.forkR;
            if (g >= 0.1f && g < 3.5f) t = a.forkL >= 0 ? "< STEER INTO THE BRANCH" : "STEER INTO THE BRANCH >";
        } else if (!race.chase) {
            Track.Junction j = race.track.junctionAhead(p);
            boolean exitAhead = false;
            for (int k = 0; k < 120 && !exitAhead; k++) exitAhead = race.track.get(p + k).exit && race.track.get(p + k).forkR < 1.5f;
            Track.Fork fk = race.track.get(p + 40).fork;
            if (fk != null && !fk.decided && p + 40 - fk.start < Track.DIV)
                t = fk.side < 0 ? "< " + Track.NAMES[fk.other.id] + "   " + Track.NAMES[fk.straight.id] + " ^"
                        : "^ " + Track.NAMES[fk.straight.id] + "   " + Track.NAMES[fk.other.id] + " >";
            else if (race.pitAuto) t = "PULLING IN FOR GAS";
            else if (race.pitIntent) t = "PIT STOP AT THE NEXT STATION";
            else if (exitAhead && !race.garageIntent) t = "GAS: STEER RIGHT >";
            else if (race.fuel < 0.25f) {
                int st = race.track.stationAhead(p);
                t = st > 0 ? String.format(Locale.US, "GAS IN %.1f KM", Math.max(0, st - p) * Race.KM_PER_SEG) : "LOW FUEL";
            }
            else if (race.track.get(p).reverse) t = "WRONG WAY";
        }
        if (t != null && ((int) (race.clock * 3)) % 3 != 0)
            textShadow(t, W / 2 - PixelFont.width(t, 1) / 2, (int) (H * 0.31f), 1, col(Skin.ACCENT));
    }

    /** The gas station: the tank filling, the car on show, arrows to change it, and the way back. */
    private void drawHangar() {
        fc.drawColor(col(Skin.VEIL));
        textC("GAS STATION", W / 2, (int) (H * 0.1f), 2, col(Skin.ACCENT));
        float fill = Math.min(1f, race.fuelBefore + (clock - stationClock) * 0.5f);
        int fx = (W - 120) / 2, fy = (int) (H * 0.1f) + 22;
        paint.setColor(col(Skin.BTN_EDGE));
        fc.drawRect(fx - 1, fy - 1, fx + 121, fy + 9, paint);
        paint.setColor(col(Skin.SHADOW));
        fc.drawRect(fx, fy, fx + 120, fy + 8, paint);
        paint.setColor(fill < 1f ? 0xFFFFC22A : 0xFF4AD07A);
        fc.drawRect(fx, fy, fx + 120 * fill, fy + 8, paint);
        textC(fill < 1f ? "FILLING UP" : "FULL TANK", W / 2, fy + 14, 1, col(Skin.TEXT));
        // the car: its condition, and the mechanics' offer
        int ry = fy + 30;
        paint.setColor(col(Skin.BTN_EDGE));
        fc.drawRect(fx - 1, ry - 1, fx + 121, ry + 7, paint);
        paint.setColor(col(Skin.SHADOW));
        fc.drawRect(fx, ry, fx + 120, ry + 6, paint);
        paint.setColor(race.health > 0.6f ? 0xFF4AD07A : race.health > 0.3f ? 0xFFFFC22A : 0xFFFF3A2A);
        fc.drawRect(fx, ry, fx + 120 * race.health, ry + 6, paint);
        if (race.health < 0.995f) {
            String rep = race.mode == Race.RALLY_TIME ? "REPAIR +" + (int) Race.REPAIR_TIME + "S" : "REPAIR";
            button(B_REPAIR, (W - 96) / 2, ry + 12, 96, 22, rep, null);
        } else {
            textC("CAR IN GOOD SHAPE", W / 2, ry + 16, 1, col(Skin.TEXT));
        }
        Garage.Model m = garage.vehicles[prefs.vehicle];
        Garage.Sprite[][][] v = m.views(Math.min(110, W / 2));
        Garage.Sprite s = v[(int) (clock * 0.8f) % Garage.YAWS.length][0][((int) (clock * 6)) % v[0][0].length];
        int cy = (int) (H * 0.42f);
        fc.drawBitmap(s.bmp, W / 2 - s.ax, cy - s.ay, blit);
        int ay = cy + 30;
        arrow(B_PREV_CAR, 10, ay, false);
        arrow(B_NEXT_CAR, W - 32, ay, true);
        textC(m.name, W / 2, ay + 5, 1, col(Skin.TEXT));
        float tough = Garage.toughness(prefs.vehicle);
        if (tough < 1f) textC(tough <= 0.5f ? "BUILT TOUGH" : "STURDY", W / 2, ay + 16, 1, col(Skin.ACCENT));
        button(B_DRIVE, (W - 110) / 2, ay + 40, 110, 28, "DRIVE OUT", null);
    }

    /** A warning while a red light is coming: what to do about it. */
    private void redLight() {
        if (race.chase || race.state != Race.RUN) return;
        Track.Junction j = race.track.junctionAhead(race.playerSegment());
        if (j == null || j.light == Track.GREEN) return;
        int ahead = j.stopSeg - race.playerSegment();
        if (ahead < 0 || ahead > 90) return;
        int y = (int) (H * 0.17f);
        boolean blink = ((int) (race.clock * 5)) % 2 == 0;
        paint.setColor(blink ? 0xFFFF3A2A : 0xFF7A1A14);
        fc.drawCircle(W / 2f, y, 5, paint);
        String t = race.speed < Race.VMAX * 0.03f ? "WAIT FOR GREEN" : race.brake > 0.05f ? "BRAKING" : "DRAG DOWN TO STOP";
        textShadow(t, W / 2 - PixelFont.width(t, 1) / 2, y + 9, 1, col(Skin.TEXT));
    }

    private void drawPause() {
        fc.drawColor(col(Skin.VEIL));
        textC("PAUSED", W / 2, (int) (H * 0.32f), 3, col(Skin.ACCENT));
        if (race.mode == Race.FREE) {
            textC(dist(race.distanceKm) + "  " + race.overtakes + " PASSED", W / 2, (int) (H * 0.32f) + 28, 1, col(Skin.DIM));
        }
        button(B_RESUME, (W - 90) / 2, (int) (H * 0.45f), 90, 26, "RESUME", null);
        button(B_MENU, (W - 90) / 2, (int) (H * 0.45f) + 36, 90, 26, "MENU", null);
    }

    private void drawResult() {
        fc.drawColor(col(Skin.VEIL));
        int x0 = 10, y0 = (int) (H * 0.08f), x1 = W - 10, y1 = (int) (H * 0.92f);
        paint.setColor(col(Skin.BTN_EDGE));
        fc.drawRect(x0, y0, x1, y1, paint);
        paint.setColor(col(Skin.PANEL));
        fc.drawRect(x0 + 2, y0 + 2, x1 - 2, y1 - 2, paint);
        int cx = W / 2;
        int y = y0 + 10;
        String head = race.wrecked ? "WRECKED" : race.outOfFuel ? "OUT OF FUEL" : race.dnf ? "DNF" : race.busted ? (race.flood ? "WIPEOUT" : race.alien ? "ABDUCTED" : "BUSTED")
                : race.mode == Race.RALLY_TIME ? "FINISH" : "TIME UP";
        int s = PixelFont.width(head, 3) <= x1 - x0 - 8 ? 3 : 2;
        PixelFont.draw(fc, paint, head, cx - PixelFont.width(head, s) / 2, y, s, 0, skin().title);
        y += 7 * s + 8;
        if (race.rally()) textC(RALLY_NAMES[race.mode], cx, y, 1, col(Skin.DIM));
        y += 14;
        if (highlights != null && highlights.shots.length > 0) {
            // the photos the game "took": one at a time, with a flash between them
            int i = ((int) (clock / 2.2f)) % highlights.shots.length;
            Bitmap b = highlights.shots[i];
            int bx = cx - b.getWidth() / 2;
            paint.setColor(0xFFF4F0E6);
            fc.drawRect(bx - 3, y - 3, bx + b.getWidth() + 3, y + b.getHeight() + 3, paint);
            fc.drawBitmap(b, bx, y, blit);
            float since = clock % 2.2f;
            if (since < 0.15f) fc.drawColor(((int) ((0.15f - since) / 0.15f * 200) << 24) | 0xFFFFFF);
            y += b.getHeight() + 8;
            String cap = highlights.captions[i];
            int slash = cap.indexOf(" / ");
            textC(slash > 0 ? cap.substring(0, slash) : cap, cx, y, 1, col(Skin.TEXT));
            if (slash > 0) textC(cap.substring(slash + 3), cx, y + 10, 1, col(Skin.DIM));
            y += 26;
        }
        if (race.rally() && !race.dnf) {
            boolean time = race.mode == Race.RALLY_TIME;
            float result = time ? race.elapsed : race.distanceKm;
            String r = time ? Race.clockText(result) : dist(result);
            textC(r, cx, y, 3, col(Skin.TEXT));
            y += 28;
            boolean best = bestBefore <= 0 || Prefs.better(race.mode, result, bestBefore);
            if (best && bestBefore > 0 && ((int) (clock * 3)) % 2 == 0) textC("NEW BEST!", cx, y, 2, col(Skin.ACCENT));
            else if (bestBefore > 0) textC("BEST " + (time ? Race.clockText(bestBefore) : dist(bestBefore)), cx, y + 4, 1, col(Skin.DIM));
            y += 22;
        } else if (race.dnf) {
            textC(race.chaseReason, cx, y, 1, col(Skin.TEXT));
            textC("TOUCHING THE POLICE ENDS A RALLY", cx, y + 12, 1, col(Skin.DIM));
            y += 30;
        }
        y = stat("DISTANCE", dist(race.distanceKm), y);
        y = stat("TOP SPEED", Math.round(race.topSpeed / Race.VMAX * (prefs.mph ? Race.VMAX_KMH * 0.6214f : Race.VMAX_KMH)) + (prefs.mph ? " MPH" : " KM/H"), y);
        y = stat("OVERTAKES", String.valueOf(race.overtakes), y);
        stat("PEAK FLOW", Math.round(race.peakFlow * 100) + "%", y);
        int bw = (x1 - x0 - 18) / 2;
        if (race.rally()) {
            button(B_AGAIN, x0 + 6, y1 - 34, bw, 26, "REPEAT", null);
            button(B_CONTINUE, x1 - 6 - bw, y1 - 34, bw, 26, "DRIVE ON", null);
        } else {
            button(B_CONTINUE, x0 + 6, y1 - 34, bw, 26, "DRIVE AGAIN", null);
            button(B_MENU, x1 - 6 - bw, y1 - 34, bw, 26, "MENU", null);
        }
    }

    private int stat(String label, String value, int y) {
        text(label, 22, y, 1, col(Skin.DIM));
        text(value, W - 22 - PixelFont.width(value, 1), y, 1, col(Skin.TEXT));
        return y + 12;
    }

    // -------------------------------------------------------------- helpers

    private String dist(float km) {
        return prefs.mph ? String.format(Locale.US, "%.1f MI", km * 0.6214f) : String.format(Locale.US, "%.1f KM", km);
    }

    private static String pad(String s, int n) {
        StringBuilder b = new StringBuilder(s);
        while (b.length() < n) b.insert(0, ' ');
        return b.toString();
    }

    private void text(String s, int x, int y, int scale, int color) {
        PixelFont.draw(fc, paint, s, x, y, scale, color);
    }

    private void textC(String s, int cx, int y, int scale, int color) {
        text(s, cx - PixelFont.width(s, scale) / 2, y, scale, color);
    }

    private void textShadow(String s, int x, int y, int scale, int color) {
        PixelFont.draw(fc, paint, s, x + 1, y + 1, scale, col(Skin.SHADOW));
        PixelFont.draw(fc, paint, s, x, y, scale, color);
    }

    private void arrow(int id, int x, int y, boolean right) {
        paint.setColor(col(Skin.BTN_EDGE));
        fc.drawRect(x, y, x + 22, y + 17, paint);
        paint.setColor(col(Skin.BTN_FILL));
        fc.drawRect(x + 1, y + 1, x + 21, y + 16, paint);
        paint.setColor(col(Skin.ACCENT));
        for (int i = 0; i < 5; i++) {
            int ax = right ? x + 9 + i : x + 13 - i;
            fc.drawRect(ax, y + 4 + i, ax + 1, y + 13 - i, paint);
        }
        // the touch target is taller than the drawing, for a thumb
        buttons.add(new Btn(id, x - 4, y - 3, 30, 23));
    }

    private void button(int id, int x, int y, int w, int h, String l1, String l2) {
        paint.setColor(col(Skin.BTN_EDGE));
        fc.drawRect(x, y, x + w, y + h, paint);
        paint.setColor(col(Skin.BTN_INNER));
        fc.drawRect(x + 1, y + 1, x + w - 1, y + h - 1, paint);
        paint.setColor(col(Skin.BTN_FILL));
        fc.drawRect(x + 2, y + 2, x + w - 2, y + h - 2, paint);
        paint.setColor(col(Skin.BTN_TOP));
        fc.drawRect(x + 2, y + 2, x + w - 2, y + 3, paint);
        int cx = x + w / 2;
        if (l2 == null) {
            textC(l1, cx, y + (h - 7) / 2, 1, col(Skin.TEXT));
        } else {
            textC(l1, cx, y + h / 2 - 8, 1, col(Skin.TEXT));
            textC(l2, cx, y + h / 2 + 1, 1, col(Skin.TEXT));
        }
        buttons.add(new Btn(id, x, y, w, h));
    }
}
