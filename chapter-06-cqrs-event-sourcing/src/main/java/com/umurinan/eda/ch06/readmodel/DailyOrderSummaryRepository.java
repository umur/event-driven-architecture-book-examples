package com.umurinan.eda.ch06.readmodel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface DailyOrderSummaryRepository extends JpaRepository<DailyOrderSummary, LocalDate> {
}
