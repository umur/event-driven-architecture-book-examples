package com.umurinan.eda.ch16.events;

import java.time.Instant;

public record ContentPublished(
        String contentId,
        String title,
        String tenantId,
        Instant publishedAt
) {}
