package com.smartshop.repository;

import com.smartshop.dto.CreditRequest;
import com.smartshop.dto.CustomerRequest;
import com.smartshop.dto.PaymentRequest;
import com.smartshop.model.Customer;
import com.smartshop.model.LedgerEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Repository
public class ShopRepository {
    private final JdbcTemplate jdbc;

    public ShopRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<Customer> customerMapper = (rs, row) -> {
        BigDecimal credit = rs.getBigDecimal("total_credit");
        BigDecimal payments = rs.getBigDecimal("total_payments");
        return new Customer(rs.getLong("id"), rs.getString("full_name"), rs.getString("phone"),
                rs.getString("address"), rs.getBoolean("active"),
                rs.getTimestamp("created_at").toLocalDateTime(), credit, payments, credit.subtract(payments));
    };

    private static final String CUSTOMER_SELECT = """
            SELECT c.id, c.full_name, c.phone, c.address, c.active, c.created_at,
                   COALESCE(cr.total_credit, 0.00) AS total_credit,
                   COALESCE(py.total_payments, 0.00) AS total_payments
            FROM customers c
            LEFT JOIN (SELECT customer_id, SUM(amount) total_credit FROM credit_entries GROUP BY customer_id) cr
              ON cr.customer_id = c.id
            LEFT JOIN (SELECT customer_id, SUM(amount) total_payments FROM payments GROUP BY customer_id) py
              ON py.customer_id = c.id
            """;

    public List<Customer> findCustomers(String search) {
        String term = search == null ? "" : search.trim();
        return jdbc.query(CUSTOMER_SELECT + " WHERE (? = '' OR c.full_name LIKE ? OR c.phone LIKE ?) ORDER BY c.full_name",
                customerMapper, term, "%" + term + "%", "%" + term + "%");
    }

    public Customer findCustomer(long id) {
        List<Customer> result = jdbc.query(CUSTOMER_SELECT + " WHERE c.id = ?", customerMapper, id);
        return result.isEmpty() ? null : result.get(0);
    }

    public long insertCustomer(CustomerRequest request) {
        jdbc.update("INSERT INTO customers (full_name, phone, address) VALUES (?, ?, ?)",
                request.fullName().trim(), request.phone().trim(), blankToNull(request.address()));
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    public void updateCustomer(long id, CustomerRequest request) {
        jdbc.update("UPDATE customers SET full_name = ?, phone = ?, address = ? WHERE id = ?",
                request.fullName().trim(), request.phone().trim(), blankToNull(request.address()), id);
    }

    public void updateCustomerStatus(long id, boolean active) {
        jdbc.update("UPDATE customers SET active = ? WHERE id = ?", active, id);
    }

    public boolean lockCustomer(long id) {
        return !jdbc.query("SELECT id FROM customers WHERE id = ? FOR UPDATE",
                (rs, row) -> rs.getLong(1), id).isEmpty();
    }

    public long insertCredit(CreditRequest request, LocalDate date) {
        jdbc.update("INSERT INTO credit_entries (customer_id, description, amount, transaction_date, notes) VALUES (?, ?, ?, ?, ?)",
                request.customerId(), request.description().trim(), request.amount(), Date.valueOf(date), blankToNull(request.notes()));
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    public Long findPaymentIdByRequestKey(String requestKey) {
        List<Long> ids = jdbc.query("SELECT id FROM payments WHERE request_key = ?", (rs, row) -> rs.getLong(1), requestKey);
        return ids.isEmpty() ? null : ids.get(0);
    }

    public Long findPaymentCustomerIdByRequestKey(String requestKey) {
        List<Long> ids = jdbc.query("SELECT customer_id FROM payments WHERE request_key = ?",
                (rs, row) -> rs.getLong(1), requestKey);
        return ids.isEmpty() ? null : ids.get(0);
    }

    public long insertPayment(PaymentRequest request, LocalDate date) {
        jdbc.update("""
                INSERT INTO payments (customer_id, amount, transaction_date, payment_method, notes, request_key)
                VALUES (?, ?, ?, ?, ?, ?)
                """, request.customerId(), request.amount(), Date.valueOf(date), request.paymentMethod(),
                blankToNull(request.notes()), request.requestKey());
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    public BigDecimal getBalance(long customerId) {
        return jdbc.queryForObject("""
                SELECT COALESCE((SELECT SUM(amount) FROM credit_entries WHERE customer_id = ?), 0.00)
                     - COALESCE((SELECT SUM(amount) FROM payments WHERE customer_id = ?), 0.00)
                """, BigDecimal.class, customerId, customerId);
    }

    public List<Map<String, Object>> getCredits(long customerId) {
        return jdbc.queryForList("""
                SELECT id, description, amount, transaction_date AS transactionDate, notes
                FROM credit_entries WHERE customer_id = ? ORDER BY transaction_date DESC, id DESC
                """, customerId);
    }

    public List<Map<String, Object>> allCredits() {
        return jdbc.queryForList("""
                SELECT cr.id, cr.customer_id AS customerId, c.full_name AS customerName, c.phone,
                       cr.description, cr.amount, cr.transaction_date AS transactionDate, cr.notes
                FROM credit_entries cr JOIN customers c ON c.id = cr.customer_id
                ORDER BY cr.transaction_date DESC, cr.id DESC LIMIT 100
                """);
    }

    public List<Map<String, Object>> getPayments(long customerId) {
        return jdbc.queryForList("""
                SELECT id, amount, transaction_date AS transactionDate, payment_method AS paymentMethod, notes
                FROM payments WHERE customer_id = ? ORDER BY transaction_date DESC, id DESC
                """, customerId);
    }

    public List<Map<String, Object>> allPayments() {
        return jdbc.queryForList("""
                SELECT py.id, py.customer_id AS customerId, c.full_name AS customerName, c.phone,
                       py.amount, py.transaction_date AS transactionDate, py.payment_method AS paymentMethod, py.notes
                FROM payments py JOIN customers c ON c.id = py.customer_id
                ORDER BY py.transaction_date DESC, py.id DESC LIMIT 100
                """);
    }

    public List<LedgerEntry> getLedger(long customerId) {
        List<LedgerEntry> entries = jdbc.query("""
                SELECT id, transaction_date, type, description, amount, payment
                FROM (
                    SELECT id, transaction_date, 'CREDIT' AS type, description, amount, 0.00 AS payment, 0 AS sort_order
                    FROM credit_entries WHERE customer_id = ?
                    UNION ALL
                    SELECT id, transaction_date, 'PAYMENT' AS type, payment_method AS description,
                           0.00 AS amount, amount AS payment, 1 AS sort_order
                    FROM payments WHERE customer_id = ?
                ) entries
                ORDER BY transaction_date, sort_order, id
                """, (rs, row) -> new LedgerEntry(rs.getLong("id"), rs.getDate("transaction_date").toLocalDate(),
                rs.getString("type"), rs.getString("description"), rs.getBigDecimal("amount"),
                rs.getBigDecimal("payment"), BigDecimal.ZERO), customerId, customerId);
        BigDecimal running = BigDecimal.ZERO;
        java.util.ArrayList<LedgerEntry> result = new java.util.ArrayList<>();
        for (LedgerEntry entry : entries) {
            running = running.add(entry.credit()).subtract(entry.payment());
            result.add(new LedgerEntry(entry.id(), entry.transactionDate(), entry.type(), entry.description(),
                    entry.credit(), entry.payment(), running));
        }
        return result;
    }

    public Map<String, Object> dashboard() {
        return jdbc.queryForMap("""
                SELECT (SELECT COUNT(*) FROM customers WHERE active = TRUE) AS activeCustomers,
                       COALESCE((SELECT SUM(amount) FROM credit_entries), 0.00) AS totalCredit,
                       COALESCE((SELECT SUM(amount) FROM payments), 0.00) AS totalPayments,
                       COALESCE((SELECT SUM(amount) FROM credit_entries), 0.00)
                         - COALESCE((SELECT SUM(amount) FROM payments), 0.00) AS outstanding,
                       (SELECT COUNT(*) FROM (
                           SELECT c.id
                           FROM customers c
                           LEFT JOIN (SELECT customer_id, SUM(amount) amount FROM credit_entries GROUP BY customer_id) cr
                             ON cr.customer_id = c.id
                           LEFT JOIN (SELECT customer_id, SUM(amount) amount FROM payments GROUP BY customer_id) py
                             ON py.customer_id = c.id
                           WHERE COALESCE(cr.amount, 0) > COALESCE(py.amount, 0)
                       ) pending) AS customersWithDues
                """);
    }

    public List<Map<String, Object>> recentTransactions() {
        return jdbc.queryForList("""
                SELECT * FROM (
                  SELECT cr.id, cr.transaction_date AS transactionDate, 'CREDIT' AS type,
                         c.full_name AS customerName, cr.description, cr.amount
                  FROM credit_entries cr JOIN customers c ON c.id = cr.customer_id
                  UNION ALL
                  SELECT py.id, py.transaction_date AS transactionDate, 'PAYMENT' AS type,
                         c.full_name AS customerName, py.payment_method AS description, py.amount
                  FROM payments py JOIN customers c ON c.id = py.customer_id
                ) recent ORDER BY transactionDate DESC, id DESC LIMIT 10
                """);
    }

    public List<Map<String, Object>> outstandingReport() {
        return jdbc.queryForList("""
                SELECT c.id, c.full_name AS fullName, c.phone, c.active,
                       COALESCE(cr.total_credit, 0.00) AS totalCredit,
                       COALESCE(py.total_payments, 0.00) AS totalPayments,
                       COALESCE(cr.total_credit, 0.00) - COALESCE(py.total_payments, 0.00) AS balance
                FROM customers c
                LEFT JOIN (SELECT customer_id, SUM(amount) total_credit FROM credit_entries GROUP BY customer_id) cr
                  ON cr.customer_id = c.id
                LEFT JOIN (SELECT customer_id, SUM(amount) total_payments FROM payments GROUP BY customer_id) py
                  ON py.customer_id = c.id
                HAVING balance > 0
                ORDER BY balance DESC
                """);
    }

    public List<Map<String, Object>> creditsReport(LocalDate from, LocalDate to) {
        return jdbc.queryForList("""
                SELECT cr.id, cr.transaction_date AS transactionDate, cr.description, cr.amount, cr.notes,
                       c.id AS customerId, c.full_name AS customerName, c.phone
                FROM credit_entries cr JOIN customers c ON c.id = cr.customer_id
                WHERE cr.transaction_date BETWEEN ? AND ?
                ORDER BY cr.transaction_date DESC, cr.id DESC
                """, Date.valueOf(from), Date.valueOf(to));
    }

    public List<Map<String, Object>> paymentsReport(LocalDate from, LocalDate to) {
        return jdbc.queryForList("""
                SELECT py.id, py.transaction_date AS transactionDate, py.amount, py.payment_method AS paymentMethod, py.notes,
                       c.id AS customerId, c.full_name AS customerName, c.phone
                FROM payments py JOIN customers c ON c.id = py.customer_id
                WHERE py.transaction_date BETWEEN ? AND ?
                ORDER BY py.transaction_date DESC, py.id DESC
                """, Date.valueOf(from), Date.valueOf(to));
    }

    public List<Map<String, Object>> duesReport() {
        return jdbc.queryForList("""
                SELECT c.id AS customerId, c.full_name AS customerName, c.phone,
                       COALESCE(cr.total_credit, 0.00) AS totalCredit,
                       COALESCE(py.total_payments, 0.00) AS totalPayments,
                       COALESCE(cr.total_credit, 0.00) - COALESCE(py.total_payments, 0.00) AS balance,
                       cr.oldest_credit AS oldestCredit,
                       DATEDIFF(CURRENT_DATE, cr.oldest_credit) AS daysSinceOldestCredit
                FROM customers c
                JOIN (SELECT customer_id, SUM(amount) total_credit, MIN(transaction_date) oldest_credit
                      FROM credit_entries GROUP BY customer_id) cr ON cr.customer_id = c.id
                LEFT JOIN (SELECT customer_id, SUM(amount) total_payments FROM payments GROUP BY customer_id) py
                  ON py.customer_id = c.id
                HAVING balance > 0
                ORDER BY balance DESC
                """);
    }

    public Map<String, Object> customerTotals(long id) {
        return jdbc.queryForMap("""
                SELECT COALESCE((SELECT SUM(amount) FROM credit_entries WHERE customer_id = ?), 0.00) AS totalCredit,
                       COALESCE((SELECT SUM(amount) FROM payments WHERE customer_id = ?), 0.00) AS totalPayments,
                       COALESCE((SELECT SUM(amount) FROM credit_entries WHERE customer_id = ?), 0.00)
                         - COALESCE((SELECT SUM(amount) FROM payments WHERE customer_id = ?), 0.00) AS balance
                """, id, id, id, id);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
