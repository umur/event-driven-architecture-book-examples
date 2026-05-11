package com.umurinan.eda.ch15.legacy;

import com.umurinan.eda.ch15.domain.MovieReview;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryReviewRepository implements ReviewRepository {

    private final ConcurrentHashMap<String, MovieReview> store = new ConcurrentHashMap<>(); // (1)

    @Override
    public void save(MovieReview review) {
        store.put(review.reviewId(), review); // (2)
    }

    @Override
    public List<MovieReview> findAll() {
        return new ArrayList<>(store.values()); // (3)
    }
}
