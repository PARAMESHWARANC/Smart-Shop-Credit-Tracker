package com.smartshop.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentRequest(
        @Positive long customerId,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
        LocalDate transactionDate,
        @NotBlank @Pattern(regexp = "CASH|UPI|OTHER") String paymentMethod,
        @Size(max = 500) String notes,
        @NotBlank @Size(max = 80) String requestKey) {
}
