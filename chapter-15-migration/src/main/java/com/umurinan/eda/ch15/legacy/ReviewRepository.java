package com.umurinan.eda.ch15.legacy;

import com.umurinan.eda.ch15.domain.MovieReview;

import java.util.List;

public interface ReviewRepository {

    void save(MovieReview review);

    List<MovieReview> findAll();
}
