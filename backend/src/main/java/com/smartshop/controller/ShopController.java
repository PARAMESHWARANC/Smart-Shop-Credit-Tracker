package com.smartshop.controller;

import com.smartshop.dto.CreditRequest;
import com.smartshop.dto.CustomerRequest;
import com.smartshop.dto.PaymentRequest;
import com.smartshop.dto.StatusRequest;
import com.smartshop.model.Customer;
import com.smartshop.service.ShopService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ShopController {
    private final ShopService service;

    public ShopController(ShopService service) {
        this.service = service;
    }

    @GetMapping("/customers")
    public List<Customer> customers(@RequestParam(required = false) String search) {
        return service.customers(search);
    }

    @GetMapping("/customers/{id}")
    public Customer customer(@PathVariable long id) {
        return service.customer(id);
    }

    @PostMapping("/customers")
    @ResponseStatus(HttpStatus.CREATED)
    public Customer createCustomer(@Valid @RequestBody CustomerRequest request) {
        return service.createCustomer(request);
    }

    @PutMapping("/customers/{id}")
    public Customer updateCustomer(@PathVariable long id, @Valid @RequestBody CustomerRequest request) {
        return service.updateCustomer(id, request);
    }

    @PatchMapping("/customers/{id}/status")
    public Customer customerStatus(@PathVariable long id, @Valid @RequestBody StatusRequest request) {
        return service.changeCustomerStatus(id, request.active());
    }

    @GetMapping("/customers/{id}/credits")
    public List<Map<String, Object>> credits(@PathVariable long id) {
        return service.credits(id);
    }

    @PostMapping("/credits")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createCredit(@Valid @RequestBody CreditRequest request) {
        return service.addCredit(request);
    }

    @GetMapping("/credits")
    public List<Map<String, Object>> allCredits() {
        return service.allCredits();
    }

    @GetMapping("/customers/{id}/payments")
    public List<Map<String, Object>> payments(@PathVariable long id) {
        return service.payments(id);
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createPayment(@Valid @RequestBody PaymentRequest request) {
        return service.addPayment(request);
    }

    @GetMapping("/payments")
    public List<Map<String, Object>> allPayments() {
        return service.allPayments();
    }

    @GetMapping("/customers/{id}/ledger")
    public Map<String, Object> ledger(@PathVariable long id) {
        return service.ledger(id);
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return service.dashboard();
    }

    @GetMapping("/reports/outstanding")
    public List<Map<String, Object>> outstanding() {
        return service.outstanding();
    }

    @GetMapping("/reports/dues")
    public List<Map<String, Object>> dues() {
        return service.dues();
    }

    @GetMapping("/reports/credits")
    public List<Map<String, Object>> creditsReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.creditsReport(from, to);
    }

    @GetMapping("/reports/payments")
    public List<Map<String, Object>> paymentsReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.paymentsReport(from, to);
    }
}
