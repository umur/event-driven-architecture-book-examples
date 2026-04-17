package com.umurinan.eda.ch06.unit;

import com.umurinan.eda.ch06.readmodel.DailyOrderSummary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DailyOrderSummaryTest {

    private static final LocalDate TODAY = LocalDate.of(2024, 6, 15);

    @Test
    void newSummary_startsWithZeroCountAndRevenue() {
        var summary = new DailyOrderSummary(TODAY);

        assertThat(summary.getSummaryDate()).isEqualTo(TODAY);
        assertThat(summary.getOrderCount()).isZero();
        assertThat(summary.getTotalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void incrementOrder_increasesCountByOne() {
        var summary = new DailyOrderSummary(TODAY);

        summary.incrementOrder(new BigDecimal("100.00"));

        assertThat(summary.getOrderCount()).isEqualTo(1);
    }

    @Test
    void incrementOrder_addsAmountToRevenue() {
        var summary = new DailyOrderSummary(TODAY);

        summary.incrementOrder(new BigDecimal("49.99"));

        assertThat(summary.getTotalRevenue()).isEqualByComparingTo(new BigDecimal("49.99"));
    }

    @Test
    void incrementOrder_calledTwice_accumulatesCountAndRevenue() {
        var summary = new DailyOrderSummary(TODAY);

        summary.incrementOrder(new BigDecimal("100.00"));
        summary.incrementOrder(new BigDecimal("200.50"));

        assertThat(summary.getOrderCount()).isEqualTo(2);
        assertThat(summary.getTotalRevenue()).isEqualByComparingTo(new BigDecimal("300.50"));
    }

    @Test
    void incrementOrder_withZeroAmount_incrementsCountButNotRevenue() {
        var summary = new DailyOrderSummary(TODAY);

        summary.incrementOrder(BigDecimal.ZERO);

        assertThat(summary.getOrderCount()).isEqualTo(1);
        assertThat(summary.getTotalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void incrementOrder_calledManyTimes_countMatchesInvocations() {
        var summary = new DailyOrderSummary(TODAY);

        for (int i = 0; i < 10; i++) {
            summary.incrementOrder(new BigDecimal("1.00"));
        }

        assertThat(summary.getOrderCount()).isEqualTo(10);
        assertThat(summary.getTotalRevenue()).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    void incrementOrder_preservesBigDecimalScale() {
        var summary = new DailyOrderSummary(TODAY);

        summary.incrementOrder(new BigDecimal("19.9999"));
        summary.incrementOrder(new BigDecimal("0.0001"));

        assertThat(summary.getTotalRevenue()).isEqualByComparingTo(new BigDecimal("20.0000"));
    }
}
