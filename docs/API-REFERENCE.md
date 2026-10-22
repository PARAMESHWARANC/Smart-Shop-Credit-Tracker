# REST API Reference

Base URL for local use: `http://localhost:8080/api`. All routes except `GET /auth/csrf` and `POST /auth/login` require an authenticated session. JSON requests that modify data must include the `X-XSRF-TOKEN` header returned by the CSRF endpoint. Browser requests must preserve cookies (`credentials: "include"`).

## Common conventions

- IDs are positive integer values.
- Dates use ISO format `YYYY-MM-DD`.
- Currency values use decimal amounts and are validated on the server.
- Success responses are JSON unless logout returns HTTP 204.
- Errors use `{ "timestamp": "...", "status": 400, "error": "Bad Request", "message": "...", "fields": { "phone": "..." } }`.
- Typical statuses: 200 success; 201 created; 204 logout; 400 validation/request error; 401 sign-in required/invalid credentials; 404 record absent; 409 business rule/duplicate conflict.

## Authentication

### `GET /auth/csrf`

Get the CSRF token before logging in. The response also sets the CSRF cookie.

Example response: `{ "token": "csrf-token-value" }`

### `POST /auth/login`

Request:

```json
{ "username": "admin", "password": "configured-bootstrap-password" }
```

On success, returns the username and role and sets an authenticated session cookie. Invalid credentials return 401 with a friendly error. Include the CSRF token header on this request.

### `GET /auth/session`

Returns the signed-in user, for example `{ "username": "admin", "role": "ROLE_ADMIN" }`. Unsigned requests receive 401.

### `POST /auth/logout`

Ends the authenticated session and returns 204. Include the CSRF token.

## Customers

### `GET /customers?search=Ravi`

Returns customer rows, optionally matched against name or phone. Each row includes `id`, `fullName`, `phone`, optional `address`, `active`, `createdAt`, `totalCredit`, `totalPayments`, and calculated `balance`.

### `GET /customers/{id}`

Returns the same customer details for one ID; returns 404 if it does not exist.

### `POST /customers`

Request:

```json
{ "fullName": "Ravi Kumar", "phone": "9876543210", "address": "Chennai" }
```

Name is required (maximum 120 characters); phone is required and allows common phone formatting characters with 7–20 characters; address is optional (maximum 255). Success returns HTTP 201 and the created customer.

### `PUT /customers/{id}`

Uses the same body and validation as create. Updates name, phone and address; returns 404 if absent.

### `PATCH /customers/{id}/status`

Request: `{ "active": false }` to archive or `{ "active": true }` to restore. Financial records are never deleted. Existing dues remain reportable and can still be paid.

## Credit entries

### `GET /customers/{id}/credits`

Returns the selected customer's credits, newest date/ID first. Unknown IDs return 404.

### `GET /credits`

Returns up to the latest 100 credit rows across customers.

### `POST /credits`

Request:

```json
{
  "customerId": 1,
  "description": "Grocery purchase",
  "amount": 2000.00,
  "transactionDate": "2026-10-21",
  "notes": "Optional note"
}
```

Customer ID and description are required; amount must be greater than zero with at most two fractional digits and fit `DECIMAL(12,2)`; date defaults to today and cannot be in the future; notes are optional (maximum 500). Archived customers cannot receive new credit. Returns 201 with the new ID and database-calculated balance.

## Payments

### `GET /customers/{id}/payments`

Returns the selected customer's repayment history, newest date/ID first.

### `GET /payments`

Returns up to the latest 100 payments across customers.

### `POST /payments`

Request:

```json
{
  "customerId": 1,
  "amount": 800.00,
  "transactionDate": "2026-10-21",
  "paymentMethod": "CASH",
  "notes": "Optional note",
  "requestKey": "unique-id-for-this-payment-attempt"
}
```

Payment method must be `CASH`, `UPI`, or `OTHER`; amount must be positive, have at most two fractional digits, fit `DECIMAL(12,2)`, and cannot exceed current outstanding amount. A future date is rejected. `requestKey` is required and unique, so resending the same successful request does not create a second row. Payment to an archived customer is allowed to settle an existing balance. A rejected business rule returns 409 and does not insert a payment.

## Ledger and dashboard

### `GET /customers/{id}/ledger`

Returns `{ "customer": ..., "totals": { "totalCredit": ..., "totalPayments": ..., "balance": ... }, "entries": [...] }`. Each entry has a date, type, description, credit, payment and running balance. Ordering is chronological; same-day credit rows precede payments and IDs make the ordering deterministic.

### `GET /dashboard`

Returns database-backed summary counts/totals and up to 10 recent transactions. Balance totals are calculated from saved credit/payment rows, not browser-submitted values.

## Reports

### `GET /reports/outstanding`

Returns positive customer balances, including archived customers whose dues remain.

### `GET /reports/dues`

Returns active customer pending dues with oldest credit date and `daysSinceOldestCredit`. This date is an estimate because repayments are not allocated to individual purchases.

### `GET /reports/credits?from=2026-10-01&to=2026-10-31`

Returns credits whose transaction date is within the inclusive date range. Both dates are required; start must not be after end, and future end dates are rejected.

### `GET /reports/payments?from=2026-10-01&to=2026-10-31`

Returns payments within the inclusive date range, with the same date validation.

## Error response example

```json
{
  "timestamp": "2026-10-21T10:00:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Payment exceeds the current outstanding balance of 1200.00",
  "fields": {}
}
```

The API does not return stack traces or database credentials in normal error responses.
