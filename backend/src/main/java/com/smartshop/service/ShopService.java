package com.smartshop.service;

import com.smartshop.dto.CreditRequest;
import com.smartshop.dto.CustomerRequest;
import com.smartshop.dto.PaymentRequest;
import com.smartshop.exception.BusinessException;
import com.smartshop.exception.NotFoundException;
import com.smartshop.model.Customer;
import com.smartshop.repository.ShopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class ShopService {
    private final ShopRepository repository;

    public ShopService(ShopRepository repository) {
        this.repository = repository;
    }

    public List<Map<String, Object>> allCredits() {
        return repository.allCredits();
    }

    public List<Customer> customers(String search) {
        return repository.findCustomers(search);
    }

    public List<Map<String, Object>> allPayments() {
        return repository.allPayments();
    }

    public Customer customer(long id) {
        Customer customer = repository.findCustomer(id);
        if (customer == null) {
            throw new NotFoundException("Customer not found");
        }
        return customer;
    }

    @Transactional
    public Customer createCustomer(CustomerRequest request) {
        return customer(repository.insertCustomer(request));
    }

    @Transactional
    public Customer updateCustomer(long id, CustomerRequest request) {
        customer(id);
        repository.updateCustomer(id, request);
        return customer(id);
    }

    @Transactional
    public Customer changeCustomerStatus(long id, boolean active) {
        customer(id);
        repository.updateCustomerStatus(id, active);
        return customer(id);
    }

    @Transactional
    public Map<String, Object> addCredit(CreditRequest request) {
        validateTransactionDate(request.transactionDate());
        if (!repository.lockCustomer(request.customerId())) {
            throw new NotFoundException("Customer not found");
        }
        Customer customer = customer(request.customerId());
        if (!customer.active()) {
            throw new BusinessException("Archived customers cannot receive new credit entries");
        }
        long id = repository.insertCredit(request, request.transactionDate() == null ? LocalDate.now() : request.transactionDate());
        return Map.of("id", id, "customerId", request.customerId(),
                "balance", repository.getBalance(request.customerId()), "message", "Credit recorded");
    }

    @Transactional
    public Map<String, Object> addPayment(PaymentRequest request) {
        validateTransactionDate(request.transactionDate());
        if (!repository.lockCustomer(request.customerId())) {
            throw new NotFoundException("Customer not found");
        }
        Long priorCustomerId = repository.findPaymentCustomerIdByRequestKey(request.requestKey());
        if (priorCustomerId != null) {
            if (priorCustomerId != request.customerId()) {
                throw new BusinessException("This request key was already used for another customer");
            }
            return Map.of("id", repository.findPaymentIdByRequestKey(request.requestKey()),
                    "customerId", request.customerId(), "balance", repository.getBalance(request.customerId()),
                    "message", "This payment was already recorded");
        }
        BigDecimal currentBalance = repository.getBalance(request.customerId());
        if (request.amount().compareTo(currentBalance) > 0) {
            throw new BusinessException("Payment exceeds the current outstanding balance of " + currentBalance);
        }
        long id = repository.insertPayment(request, request.transactionDate() == null ? LocalDate.now() : request.transactionDate());
        return Map.of("id", id, "customerId", request.customerId(),
                "balance", repository.getBalance(request.customerId()), "message", "Payment recorded");
    }

    public List<Map<String, Object>> credits(long customerId) {
        customer(customerId);
        return repository.getCredits(customerId);
    }

    public List<Map<String, Object>> payments(long customerId) {
        customer(customerId);
        return repository.getPayments(customerId);
    }

    public Map<String, Object> ledger(long customerId) {
        Customer customer = customer(customerId);
        return Map.of("customer", customer, "totals", repository.customerTotals(customerId),
                "entries", repository.getLedger(customerId));
    }

    public Map<String, Object> dashboard() {
        return Map.of("summary", repository.dashboard(), "recentTransactions", repository.recentTransactions());
    }

    public List<Map<String, Object>> outstanding() {
        return repository.outstandingReport();
    }

    public List<Map<String, Object>> dues() {
        return repository.duesReport();
    }

    public List<Map<String, Object>> creditsReport(LocalDate from, LocalDate to) {
        validateDateRange(from, to);
        return repository.creditsReport(from, to);
    }

    public List<Map<String, Object>> paymentsReport(LocalDate from, LocalDate to) {
        validateDateRange(from, to);
        return repository.paymentsReport(from, to);
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessException("The start date must be on or before the end date");
        }
        if (to.isAfter(LocalDate.now())) {
            throw new BusinessException("Future report dates are not allowed");
        }
    }

    private void validateTransactionDate(LocalDate date) {
        if (date != null && date.isAfter(LocalDate.now())) {
            throw new BusinessException("Future transaction dates are not allowed");
        }
    }
}
