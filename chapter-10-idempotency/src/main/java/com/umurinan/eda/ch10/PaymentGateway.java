package com.umurinan.eda.ch10;

import com.umurinan.eda.ch10.domain.PaymentResult;

import java.math.BigDecimal;

public interface PaymentGateway {

    PaymentResult charge(String orderId, BigDecimal amount);
}
