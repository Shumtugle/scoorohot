package com.shumtugle.scoorohot;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A tiny synthesizer on its own thread; no recorded sound anywhere.
 * The engine is a pulse plus a saw an octave below, low-passed, pitched by
 * the gearbox; a diesel is lower and rougher, a hover craft hums and
 * warbles, a rolling ball is a dark rumble that knocks once per turn.
 * Grass is filtered noise chopped into a rumble. Short cues
 * (countdown, gates, hits, close passes) are one-shot voices on top.
 */
final class Sound implements Runnable {
    static final int RATE = 22050;
    private static final int VOICES = 10; // one per cue bit, in the order of the race cues
    private static final float[] DURATION = {0.14f, 0.45f, 0.36f, 0.5f, 0.35f, 0.15f, 0.7f, 0.9f, 1.1f, 0.7f};

    volatile float engineHz = 38f, engineVol, rumbleVol;
    volatile int voice;
    volatile float sirenVol;   // a two-tone wail while the police are behind
    volatile float rainVol;    // a hiss of rain on the roof
    private final AtomicInteger pending = new AtomicInteger();
    private volatile boolean running;
    private Thread thread;

    private float hz = 38f, vol, rum, ph1, ph2, ph3, lp, lpRumble, rumblePh, lpCue;
    private float siren, sirenPh, sirenLfo, lpSiren, rainLvl, lpRain;
    private final float[] age = new float[VOICES];
    private final float[] phase = new float[VOICES];
    private int seed = 0x2545F491;

    Sound() {
        for (int i = 0; i < VOICES; i++) age[i] = -1f;
    }

    void cue(int bits) {
        if (bits == 0) return;
        int o;
        do {
            o = pending.get();
        } while (!pending.compareAndSet(o, o | bits));
    }

    void start() {
        if (running) return;
        running = true;
        thread = new Thread(this, "sound");
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
    }

    void stop() {
        running = false;
        if (thread != null) {
            try {
                thread.join(800);
            } catch (InterruptedException ignored) {
            }
            thread = null;
        }
    }

    @Override
    public void run() {
        AudioTrack t;
        try {
            int min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            t = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(Math.max(min, 4096))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
            t.play();
        } catch (RuntimeException e) {
            running = false;
            return;
        }
        short[] buf = new short[512];
        try {
            while (running) {
                render(buf, buf.length);
                t.write(buf, 0, buf.length);
            }
        } finally {
            try {
                t.pause();
                t.flush();
            } catch (RuntimeException ignored) {
            }
            t.release();
        }
    }

    private float noise() {
        seed = seed * 1103515245 + 12345;
        return ((seed >>> 16) & 0x7FFF) / 16384f - 1f;
    }

    /** Fills n samples; also usable off-device to inspect the output. */
    void render(short[] out, int n) {
        int c = pending.getAndSet(0);
        for (int i = 0; i < VOICES; i++)
            if ((c & (1 << i)) != 0) {
                age[i] = 0f;
                phase[i] = 0f;
            }
        float dt = 1f / RATE;
        float targetHz = engineHz, targetVol = engineVol, targetRum = rumbleVol;
        int vc = voice;
        for (int k = 0; k < n; k++) {
            hz += (targetHz - hz) * 0.0015f;
            vol += (targetVol - vol) * 0.002f;
            rum += (targetRum - rum) * 0.002f;

            float s = lp * vol;
            switch (vc) {
                case Garage.VOICE_DIESEL: {
                    float f = hz * 0.62f;
                    ph1 += f * dt;
                    if (ph1 >= 1f) ph1 -= 1f;
                    ph2 += f * 0.5f * dt;
                    if (ph2 >= 1f) ph2 -= 1f;
                    float e = (ph1 < 0.2f ? 1.2f : -0.35f) + (ph2 * 2f - 1f) * 0.5f + noise() * 0.3f;
                    lp += (e - lp) * 0.16f;
                    break;
                }
                case Garage.VOICE_HOVER: {
                    ph3 += 6f * dt;
                    if (ph3 >= 1f) ph3 -= 1f;
                    float f = hz * 3f * (1f + 0.02f * (float) Math.sin(ph3 * 6.2832f));
                    ph1 += f * dt;
                    if (ph1 >= 1f) ph1 -= 1f;
                    ph2 += f * 1.5f * dt;
                    if (ph2 >= 1f) ph2 -= 1f;
                    float e = (float) Math.sin(ph1 * 6.2832f) * 0.7f + (float) Math.sin(ph2 * 6.2832f) * 0.3f;
                    lp += (e - lp) * 0.5f;
                    break;
                }
                case Garage.VOICE_ROLL: {
                    ph1 += hz * 0.08f * dt;
                    if (ph1 >= 1f) ph1 -= 1f;
                    float knock = ph1 < 0.06f ? 1.4f : 0.7f;
                    lp += (noise() * knock * 1.6f - lp) * (0.02f + hz * 0.0002f);
                    break;
                }
                default: {
                    ph1 += hz * dt;
                    if (ph1 >= 1f) ph1 -= 1f;
                    ph2 += hz * 0.5f * dt;
                    if (ph2 >= 1f) ph2 -= 1f;
                    float e = (ph1 < 0.32f ? 1f : -0.5f) + (ph2 * 2f - 1f) * 0.6f + noise() * 0.15f;
                    lp += (e - lp) * 0.22f;
                    break;
                }
            }

            lpRumble += (noise() - lpRumble) * 0.06f;
            rumblePh += 17f * dt;
            if (rumblePh >= 1f) rumblePh -= 1f;
            s += lpRumble * rum * (rumblePh < 0.5f ? 1.6f : 0.6f);

            siren += (sirenVol - siren) * 0.001f;
            if (siren > 0.002f) {
                sirenLfo += 0.55f * dt;
                if (sirenLfo >= 1f) sirenLfo -= 1f;
                float f = 650f + 450f * (sirenLfo < 0.5f ? 1f : 0f);
                sirenPh += f * dt;
                if (sirenPh >= 1f) sirenPh -= 1f;
                lpSiren += ((sirenPh < 0.5f ? 1f : -1f) - lpSiren) * 0.25f;
                s += lpSiren * siren;
            }
            rainLvl += (rainVol - rainLvl) * 0.0005f;
            if (rainLvl > 0.002f) {
                float nz = noise();
                lpRain += (nz - lpRain) * 0.3f;
                s += (nz - lpRain) * rainLvl;
            }
            s += voices(dt);
            if (s > 1f) s = 1f;
            if (s < -1f) s = -1f;
            out[k] = (short) (s * 26000);
        }
    }

    private float voices(float dt) {
        float s = 0;
        for (int i = 0; i < VOICES; i++) {
            float t = age[i];
            if (t < 0) continue;
            float d = DURATION[i];
            float v;
            switch (i) {
                case 0: // countdown beep
                    phase[i] += 523f * dt;
                    v = (phase[i] % 1f < 0.5f ? 0.22f : -0.22f) * (t > d - 0.02f ? 0f : 1f);
                    break;
                case 1: // go
                    phase[i] += 1046f * dt;
                    v = (phase[i] % 1f < 0.5f ? 0.24f : -0.24f) * (1f - t / d);
                    break;
                case 2: { // checkpoint: three rising notes
                    float f = t < 0.12f ? 784f : t < 0.24f ? 988f : 1318f;
                    phase[i] += f * dt;
                    float p = phase[i] % 1f;
                    v = (p < 0.5f ? 4f * p - 1f : 3f - 4f * p) * 0.26f * (1f - 0.5f * t / d);
                    break;
                }
                case 3: // crash
                    lpCue += (noise() - lpCue) * 0.3f;
                    v = lpCue * 0.9f * (float) Math.exp(-t * 7f);
                    break;
                case 4: // close pass: a swell of air
                    lpCue += (noise() - lpCue) * (0.08f + 0.3f * t / d);
                    v = lpCue * 0.45f * (float) Math.sin(Math.PI * t / d);
                    break;
                case 6: // into the water
                    lpCue += (noise() - lpCue) * (0.5f - 0.4f * t / d);
                    v = lpCue * 0.7f * (float) Math.exp(-t * 4f);
                    break;
                case 7: { // tyres howling through a spin
                    phase[i] += (1500f + 300f * (float) Math.sin(t * 40f)) * dt;
                    lpCue += (noise() - lpCue) * 0.6f;
                    float sq = phase[i] % 1f < 0.5f ? 0.12f : -0.12f;
                    v = (sq + lpCue * 0.2f) * (1f - t / d);
                    break;
                }
                case 8: { // falling into a hole: a long downward sweep
                    float f = 900f * (float) Math.exp(-t * 2.4f) + 60f;
                    phase[i] += f * dt;
                    v = (float) Math.sin(phase[i] * 6.2832f) * 0.32f * (1f - t / d);
                    break;
                }
                case 9: { // a rainbow: quick bright arpeggio
                    float[] notes = {1046f, 1318f, 1568f, 2093f, 1568f, 2637f};
                    float f = notes[Math.min(notes.length - 1, (int) (t / d * notes.length))];
                    phase[i] += f * dt;
                    float p = phase[i] % 1f;
                    v = (p < 0.5f ? 4f * p - 1f : 3f - 4f * p) * 0.18f * (1f - t / d);
                    break;
                }
                default: { // side bump
                    phase[i] += 70f * dt;
                    v = ((float) Math.sin(phase[i] * 6.2832f) * 0.4f + noise() * 0.25f) * (float) Math.exp(-t * 22f);
                    break;
                }
            }
            s += v;
            age[i] = t + dt;
            if (age[i] > d) age[i] = -1f;
        }
        return s;
    }
}
