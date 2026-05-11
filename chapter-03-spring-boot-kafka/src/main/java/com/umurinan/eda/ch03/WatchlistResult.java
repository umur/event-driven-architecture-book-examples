package com.umurinan.eda.ch03;

import java.time.Instant;

public record WatchlistResult(
        String movieId,
        String userId,
        Instant updatedAt
) {}
