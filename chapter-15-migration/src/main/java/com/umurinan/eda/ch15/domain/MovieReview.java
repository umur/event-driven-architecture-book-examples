package com.umurinan.eda.ch15.domain;

import java.time.Instant;

public record MovieReview(
        String reviewId,
        String movieId,
        String userId,
        int rating,
        Instant reviewedAt
) {}
