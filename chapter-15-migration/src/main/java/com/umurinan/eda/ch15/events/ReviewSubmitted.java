package com.umurinan.eda.ch15.events;

import java.time.Instant;

public record ReviewSubmitted(
        String reviewId,
        String movieId,
        String userId,
        int rating,
        Instant reviewedAt
) {}
