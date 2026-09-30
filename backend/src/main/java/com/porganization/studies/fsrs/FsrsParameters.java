package com.porganization.studies.fsrs;

import java.util.Arrays;

/**
 * Parâmetros do FSRS-6: os 21 pesos padrão publicados pelo projeto open-spaced-repetition
 * (os mesmos do ts-fsrs 5.4.2), retenção desejada e intervalo máximo em dias.
 */
public record FsrsParameters(double[] w, double requestRetention, int maximumInterval) {

    public static final double DEFAULT_REQUEST_RETENTION = 0.9;
    public static final int DEFAULT_MAXIMUM_INTERVAL = 365;

    private static final double[] DEFAULT_W = {
        0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001, 1.8722, 0.1666, 0.796,
        1.4835, 0.0614, 0.2629, 1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658, 0.1542,
    };

    public FsrsParameters {
        if (w.length != 21) {
            throw new IllegalArgumentException("FSRS-6 usa 21 parâmetros, recebeu " + w.length);
        }
        if (requestRetention <= 0 || requestRetention > 1) {
            throw new IllegalArgumentException("retenção deve estar em (0, 1]");
        }
        if (maximumInterval < 1) {
            throw new IllegalArgumentException("intervalo máximo deve ser pelo menos 1 dia");
        }
        w = w.clone();
    }

    public static FsrsParameters defaults() {
        return new FsrsParameters(DEFAULT_W, DEFAULT_REQUEST_RETENTION, DEFAULT_MAXIMUM_INTERVAL);
    }

    public FsrsParameters withMaximumInterval(int days) {
        return new FsrsParameters(w, requestRetention, days);
    }

    @Override
    public double[] w() {
        return w.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FsrsParameters p && Arrays.equals(w, p.w)
                && requestRetention == p.requestRetention && maximumInterval == p.maximumInterval;
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(w) + Double.hashCode(requestRetention) + maximumInterval;
    }

    @Override
    public String toString() {
        return "FsrsParameters[w=" + Arrays.toString(w) + ", requestRetention=" + requestRetention
                + ", maximumInterval=" + maximumInterval + "]";
    }
}
