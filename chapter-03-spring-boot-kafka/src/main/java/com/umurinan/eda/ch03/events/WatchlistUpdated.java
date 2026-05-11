package com.umurinan.eda.ch03.events;

import java.time.Instant;

public record WatchlistUpdated(
        String movieId,
        String userId,
        Instant watchedAt,
        int progressPercent
) {}
