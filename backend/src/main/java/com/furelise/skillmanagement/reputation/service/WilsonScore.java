package com.furelise.skillmanagement.reputation.service;

/**
 * Wilson score lower bound (z = 1.96, 95%) for k successes out of n trials —
 * the small-sample-safe rate used wherever a ratio enters a reputation score
 * (BA §9.1: "Wilson for rates"): AI News like/view (GĐ4) and install
 * success rate (GĐ6). 0 when there are no trials.
 *
 * Extracted from ReputationServiceImpl so it is unit-testable on its own.
 * Worked examples: 10/10 → 0.7225; 5/5 → 0.5655; 20/20 → 0.8389;
 * 30/30 → 0.8865; 9/10 → 0.5958; 8/10 → 0.4902.
 */
public final class WilsonScore {

    private WilsonScore() {
    }

    public static double lowerBound(long successes, long trials) {
        if (trials <= 0) {
            return 0.0;
        }
        double z = 1.96;
        double p = (double) successes / trials;
        double denom = 1.0 + z * z / trials;
        double centre = p + z * z / (2.0 * trials);
        double margin = z * Math.sqrt((p * (1.0 - p) + z * z / (4.0 * trials)) / trials);
        return Math.max(0.0, (centre - margin) / denom);
    }
}
