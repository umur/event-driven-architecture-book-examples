package com.umurinan.eda.ch03.commands;

public record UpdateWatchlistCommand(
        String movieId,
        String userId,
        int progressPercent
) {}
