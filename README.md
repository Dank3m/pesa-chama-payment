# Payment Service

A microservice for handling payments and disbursements for the Table Banking System, integrating with Family Bank's ESB APIs.

## Features

- **IPN (Instant Payment Notification)**: Receive payment notifications from Family Bank
- **Customer Validation**: Validate members before accepting payments
- **Smart Payment Allocation**: Automatically allocate payments to contributions first, then loans
- **Disbursements**: Send payments via Family Bank's Mass Payments API
- **Multiple Payment Types**: Support for MPESA, PesaLink, EFT, and bank transfers

## Architecture

```
                          ┌─────────────────────────┐
                          │      Family Bank        │
                          │  Collections API (IPN)  │
                          └───────────┬─────────────┘
                                      │ VALIDATION / PAYMENT_NOTIFICATION
                                      ▼
┌─────────────────┐     ┌─────────────────────────────┐     ┌─────────────────┐
│ Table Banking   │────▶│      Payment Service         │────▶│  Family Bank    │
│   Main App      │     │                              │     │ Mass Payments   │
└─────────────────┘     │  - IPN Processing            │     └─────────────────┘
        ▲               │  - Payment Allocation         │
        │               │  - Disbursement Service       │
        │               └─────────────────────────────┘
        │                             │
        └─────────────────────────────┘
              Kafka Events (contribution-events, loan-events)
```

## Payment Allocation Logic

When a payment is received, it's allocated in this priority order:

1. **Contribution Payment** - First, pay off any outstanding contribution for the current cycle
2. **Loan Repayment** - Remaining amount goes to active loan repayment
3. **Overpayment** - Any excess is held as unallocated (credit balance)

### Example

Member has:
- Outstanding contribution: KES 3,500
- Outstanding loan: KES 15,000
- Payment received: KES 10,000

Allocation:
- To contribution: KES 3,500 (contribution now PAID)
- To loan: KES 6,500 (loan balance now KES 8,500)
- Unallocated: KES 0

## API Endpoints

### IPN Endpoints (Called by Family Bank)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/ipn/validate` | Validate customer before payment |
| POST | `/api/v1/ipn/payment-notification` | Receive payment notification |
| GET | `/api/v1/ipn/payments/{id}` | Get payment status |
| GET | `/api/v1/ipn/payments/reference/{txnRef}` | Get payment by bank reference |

### Disbursement Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/disbursements` | Initiate single disbursement |
| POST | `/api/v1/disbursements/bulk` | Initiate bulk disbursement |
| GET | `/api/v1/disbursements/{id}` | Get disbursement by ID |
| GET | `/api/v1/disbursements/batches/{batchRef}` | Get batch by reference |
| POST | `/api/v1/disbursements/batches/{batchRef}/refresh` | Refresh status from bank |

## Configuration

### Environment Variables

```bash
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=payments
DB_USERNAME=postgres
DB_PASSWORD=postgres

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# Family Bank - Collections API
FBL_AUTH_URL=https://sandbox.familybank.co.ke:1045/connect/token
FBL_COLLECTIONS_CLIENT_ID=your-client-id
FBL_COLLECTIONS_CLIENT_SECRET=your-client-secret
FBL_COLLECTION_ACCOUNT=your-collection-account

# Family Bank - Mass Payments API
FBL_PAYMENTS_URL=https://sandbox.familybank.co.ke:1044
FBL_PAYMENTS_CLIENT_ID=your-client-id
FBL_PAYMENTS_CLIENT_SECRET=your-client-secret
FBL_DEBIT_ACCOUNT=your-debit-account
FBL_SENDER_BANK=TABLE BANKING CHAMA
FBL_SENDER_BRANCH=HEAD OFFICE

# Main App Integration
MAIN_APP_BASE_URL=http://localhost:8080
MAIN_APP_API_KEY=your-api-key
```

## Kafka Topics

### Produced Events

| Topic | Event | Description |
|-------|-------|-------------|
| `payment-events` | PAYMENT_ALLOCATED | Payment received and allocated |
| `contribution-events` | CONTRIBUTION_PAYMENT | Funds allocated to contribution |
| `loan-events` | LOAN_REPAYMENT | Funds allocated to loan |
| `disbursement-events` | DISBURSEMENT_COMPLETED/FAILED | Disbursement status updates |

### Consumed Events

| Topic | Event | Description |
|-------|-------|-------------|
| `disbursement-events` | LOAN_DISBURSEMENT_REQUEST | Request to disburse loan |

## Family Bank API Integration

### Collections API Flow

```
1. Customer initiates payment at bank
2. Bank calls /api/v1/ipn/validate → We validate member
3. Bank processes payment
4. Bank calls /api/v1/ipn/payment-notification → We process & allocate
5. We return acknowledgment with our payment reference
```

### Mass Payments API Flow

```
1. Main app requests loan disbursement (Kafka event)
2. Payment service creates batch
3. Submit to Family Bank Mass Payments API
4. Family Bank processes (async)
5. We poll for status updates or receive webhooks
6. Publish completion event to main app
```

## Running Locally

### Prerequisites
- Java 21
- Docker & Docker Compose
- Maven

### Start Dependencies

```bash
docker-compose up -d postgres-payments redis kafka
```

### Run the Service

```bash
mvn spring-boot:run
```

### Or use Docker

```bash
docker-compose up -d payment-service
```

## Testing IPN

### Validate Customer

```bash
curl -X POST http://localhost:8082/api/v1/ipn/validate \
  -H "Content-Type: application/json" \
  -d '{
    "action": "VALIDATION",
    "payload": {
      "identifier": "12345678",
      "identifier_type": "ID_NUMBER",
      "collection_account": "035000000001"
    }
  }'
```

### Payment Notification

```bash
curl -X POST http://localhost:8082/api/v1/ipn/payment-notification \
  -H "Content-Type: application/json" \
  -d '{
    "action": "PAYMENT_NOTIFICATION",
    "payload": {
      "customer_id": "member-uuid-here",
      "payer_name": "John Doe",
      "payer_phone": "0722000000",
      "txn_amount": 5000.00,
      "payment_mode": "CASH",
      "txn_reference": "015BAAT202620003",
      "collection_account": "035000000001",
      "txn_narration": "Monthly contribution",
      "date_time": "2024-12-09 10:30:00"
    }
  }'
```

## Database Schema

Key tables:

- `payment_transactions` - Incoming payments from bank
- `payment_allocations` - How payments were distributed
- `disbursement_batches` - Outgoing payment batches
- `disbursements` - Individual outgoing payments
- `idempotency_keys` - Prevent duplicate processing

## Health Check

```bash
curl http://localhost:8082/actuator/health
```

## Swagger UI

Access API documentation at: http://localhost:8082/swagger-ui.html

## License

MIT
