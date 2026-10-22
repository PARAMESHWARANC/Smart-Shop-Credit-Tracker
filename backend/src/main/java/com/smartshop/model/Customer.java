package com.smartshop.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Customer(
        long id,
        String fullName,
        String phone,
        String address,
        boolean active,
        LocalDateTime createdAt,
        BigDecimal totalCredit,
        BigDecimal totalPayments,
        BigDecimal balance) {
}
