package ac.grim.grimac.checks.impl.combat;

import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.player.GrimPlayer;

/**
 * Clicks with machine timing. An autoclicker with a fixed delay clicks at t0, t0 + d, t0 + 2d, ..., so the
 * ticks its clicks land in lie on a straight line (within the rounding to whole ticks). Every human click
 * is a little early or late and those errors add up like a random walk, so after ~100 clicks a human's clicks
 * have drifted well away from any straight line, even when the CPS looks steady.
 * <p>
 * Tested in a simulation: a fixed delay at 7-18 CPS is caught in 87-100% of 100-click windows, a human with
 * 12 ms of jitter in under 0.07% (very steady humans with 8 ms in 0.7%), and three caught windows are
 * needed for a flag. Randomized delays of +-10 ms or more are not caught by this check.
 * <p>
 * Fed with the real left clicks of each reliable tick by {@link AutoClickerA}.
 */
@CheckData(name = "AutoClickerB", stableKey = "grim.autoclicker.consistency", description = "Clicked with machine timing")
public class AutoClickerB extends Check {
    private static final Verbose V = Verbose.of("cps={f64}, deviation={f64}, clicks={sint}");
    private static final int SAMPLES = 100;
    private static final int MAX_GAP = 10; // ticks; a longer pause ends the click streak
    private static final double MAX_DEVIATION = 0.6; // ticks
    private static final double BUFFER_LIMIT = 2;

    private final long[] clickTicks = new long[SAMPLES];
    private int count;
    private long tick;
    private long lastClickTick = Long.MIN_VALUE;
    private double buffer;

    public AutoClickerB(GrimPlayer player) {
        super(player);
    }

    void onTick(int clicks) {
        tick++;
        if (clicks == 0) return;

        if (lastClickTick == Long.MIN_VALUE || tick - lastClickTick > MAX_GAP) count = 0;
        lastClickTick = tick;

        for (int i = 0; i < clicks; i++) {
            clickTicks[count++] = tick;
            if (count == SAMPLES) {
                evaluate();
                count = 0;
            }
        }
    }

    private void evaluate() {
        // Least squares line through (click number, tick)
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (int k = 0; k < SAMPLES; k++) {
            double y = clickTicks[k] - clickTicks[0];
            sx += k;
            sy += y;
            sxx += (double) k * k;
            sxy += k * y;
        }
        double slope = (SAMPLES * sxy - sx * sy) / (SAMPLES * sxx - sx * sx);
        double intercept = (sy - slope * sx) / SAMPLES;
        if (slope <= 0) return; // more than 20 clicks a tick on average, AutoClickerA handles that

        double deviation = 0;
        for (int k = 0; k < SAMPLES; k++) {
            double y = clickTicks[k] - clickTicks[0];
            deviation = Math.max(deviation, Math.abs(y - (intercept + slope * k)));
        }

        if (deviation < MAX_DEVIATION) {
            if (++buffer > BUFFER_LIMIT) {
                flag(V.write(verbose()).f64(20 / slope).f64(deviation).sint(SAMPLES));
            }
        } else {
            buffer = Math.max(0, buffer - 1);
        }
    }

    void reset() {
        count = 0;
        lastClickTick = Long.MIN_VALUE;
    }
}
