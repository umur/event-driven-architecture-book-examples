package com.umurinan.eda.ch04.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductListingRepository extends JpaRepository<ProductListing, String> {}
