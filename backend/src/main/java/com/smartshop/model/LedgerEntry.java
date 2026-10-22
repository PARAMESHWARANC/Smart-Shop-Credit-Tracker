package com.smartshop.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LedgerEntry(
        long id,
        LocalDate transactionDate,
        String type,
        String description,
        BigDecimal credit,
        BigDecimal payment,
        BigDecimal balance) {
}
