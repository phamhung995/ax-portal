package com.furelise.skillmanagement.reputation;

import com.furelise.skillmanagement.reputation.service.WilsonScore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Wilson lower bound (z = 1.96) — the small-sample-safe rate for AI News
 * like/view (GĐ4) and install success rate (GĐ6). Values cross-checked
 * against the GĐ4 worked examples (10/10 → 72.25, 5/5 → 56.55).
 */
class WilsonScoreTest {

    @Test
    void noTrialsIsZero() {
        assertEquals(0.0, WilsonScore.lowerBound(0, 0), 1e-9);
    }

    @Test
    void perfectSmallSamplesArePenalised() {
        // A perfect rate from few trials is NOT trusted at face value.
        assertEquals(0.7225, WilsonScore.lowerBound(10, 10), 0.0001);
        assertEquals(0.5655, WilsonScore.lowerBound(5, 5), 0.0001);
        assertEquals(0.8389, WilsonScore.lowerBound(20, 20), 0.0001);
        assertEquals(0.8865, WilsonScore.lowerBound(30, 30), 0.0001);
    }

    @Test
    void partialRates() {
        assertEquals(0.5958, WilsonScore.lowerBound(9, 10), 0.0001);
        assertEquals(0.4902, WilsonScore.lowerBound(8, 10), 0.0001);
        assertEquals(0.3006, WilsonScore.lowerBound(3, 4), 0.0001);
    }

    @Test
    void zeroSuccessesIsZero() {
        assertEquals(0.0, WilsonScore.lowerBound(0, 10), 1e-9);
    }
}
