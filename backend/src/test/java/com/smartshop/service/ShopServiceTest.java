package com.smartshop.service;

import com.smartshop.dto.PaymentRequest;
import com.smartshop.exception.BusinessException;
import com.smartshop.model.Customer;
import com.smartshop.repository.ShopRepository;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ShopServiceTest {
    private final ShopRepository repository = mock(ShopRepository.class);
    private final ShopService service = new ShopService(repository);

    @Test
    void rejectsPaymentAboveOutstandingBalance() {
        when(repository.lockCustomer(1)).thenReturn(true);
        when(repository.findPaymentCustomerIdByRequestKey("request-1")).thenReturn(null);
        when(repository.findCustomer(1)).thenReturn(customer(true));
        when(repository.getBalance(1)).thenReturn(new BigDecimal("1200.00"));

        PaymentRequest request = payment(new BigDecimal("1200.01"), "request-1");
        assertThrows(BusinessException.class, () -> service.addPayment(request));
        verify(repository, never()).insertPayment(any(), any());
    }

    @Test
    void recordsPartialPaymentAndReturnsUpdatedBalance() {
        when(repository.lockCustomer(1)).thenReturn(true);
        when(repository.findPaymentCustomerIdByRequestKey("request-2")).thenReturn(null);
        when(repository.findCustomer(1)).thenReturn(customer(true));
        when(repository.getBalance(1)).thenReturn(new BigDecimal("2000.00"), new BigDecimal("1200.00"));
        when(repository.insertPayment(any(), any())).thenReturn(14L);

        var result = service.addPayment(payment(new BigDecimal("800.00"), "request-2"));
        assertEquals(14L, result.get("id"));
        assertEquals(new BigDecimal("1200.00"), result.get("balance"));
        verify(repository).insertPayment(any(), any());
    }

    @Test
    void fullPaymentClearsRemainingBalance() {
        when(repository.lockCustomer(1)).thenReturn(true);
        when(repository.findPaymentCustomerIdByRequestKey("request-4")).thenReturn(null);
        when(repository.findCustomer(1)).thenReturn(customer(true));
        when(repository.getBalance(1)).thenReturn(new BigDecimal("1200.00"), BigDecimal.ZERO);
        when(repository.insertPayment(any(), any())).thenReturn(15L);

        var result = service.addPayment(payment(new BigDecimal("1200.00"), "request-4"));
        assertEquals(BigDecimal.ZERO, result.get("balance"));
    }

    @Test
    void duplicateSubmissionReturnsExistingPaymentWithoutInsertingAgain() {
        when(repository.lockCustomer(1)).thenReturn(true);
        when(repository.findPaymentCustomerIdByRequestKey("request-3")).thenReturn(1L);
        when(repository.findPaymentIdByRequestKey("request-3")).thenReturn(14L);
        when(repository.getBalance(1)).thenReturn(new BigDecimal("400.00"));

        var result = service.addPayment(payment(new BigDecimal("800.00"), "request-3"));
        assertEquals("This payment was already recorded", result.get("message"));
        verify(repository, never()).insertPayment(any(), any());
    }

    @Test
    void validatesPositiveCurrencyWithAtMostTwoDecimalPlaces() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            PaymentRequest zero = payment(BigDecimal.ZERO, "request-zero");
            PaymentRequest negative = payment(new BigDecimal("-1.00"), "request-negative");
            PaymentRequest tooPrecise = payment(new BigDecimal("1.001"), "request-precision");

            assertFalse(validator.validate(zero).isEmpty());
            assertFalse(validator.validate(negative).isEmpty());
            assertFalse(validator.validate(tooPrecise).isEmpty());
        }
    }

    private Customer customer(boolean active) {
        return new Customer(1, "Ravi Kumar", "9876543210", null, active,
                LocalDateTime.now(), new BigDecimal("2000.00"), new BigDecimal("800.00"), new BigDecimal("1200.00"));
    }

    private PaymentRequest payment(BigDecimal amount, String key) {
        return new PaymentRequest(1, amount, LocalDate.now(), "CASH", null, key);
    }
}
