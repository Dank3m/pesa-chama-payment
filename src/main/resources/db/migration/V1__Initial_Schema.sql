-- V1__Initial_Schema.sql
-- Payment Service Database Schema

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Payment Transactions (IPN received from bank)
CREATE TABLE payment_transactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    
    -- Bank transaction details
    txn_reference VARCHAR(100) NOT NULL UNIQUE,  -- Bank's unique reference (015BAAT202620003)
    collection_account VARCHAR(50) NOT NULL,
    
    -- Payer details
    payer_identifier VARCHAR(100) NOT NULL,      -- ID_NUMBER, MSISDN, etc.
    payer_identifier_type VARCHAR(50) NOT NULL,
    payer_name VARCHAR(200),
    payer_phone VARCHAR(20),
    
    -- Member mapping (after validation)
    member_id UUID,
    member_name VARCHAR(200),
    group_id UUID,
    
    -- Transaction details
    txn_amount DECIMAL(15, 2) NOT NULL,
    payment_mode VARCHAR(50) NOT NULL,           -- CASH, CHEQUE, ACCOUNTTRANSFER, EFT, SWIFT
    txn_narration TEXT,
    txn_date_time TIMESTAMP WITH TIME ZONE NOT NULL,
    
    -- Processing status
    status VARCHAR(50) NOT NULL DEFAULT 'RECEIVED',
    status_description TEXT,
    
    -- Allocation tracking
    amount_to_contribution DECIMAL(15, 2) DEFAULT 0,
    amount_to_loan DECIMAL(15, 2) DEFAULT 0,
    amount_unallocated DECIMAL(15, 2) DEFAULT 0,
    contribution_id UUID,
    loan_id UUID,
    
    -- Our acknowledgment reference
    payment_ref VARCHAR(100),
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP WITH TIME ZONE,
    
    CONSTRAINT valid_payment_status CHECK (status IN (
        'RECEIVED', 'VALIDATING', 'VALIDATED', 'VALIDATION_FAILED',
        'PROCESSING', 'ALLOCATED', 'PARTIALLY_ALLOCATED', 
        'COMPLETED', 'FAILED', 'REVERSED'
    )),
    CONSTRAINT valid_identifier_type CHECK (payer_identifier_type IN (
        'ID_NUMBER', 'MSISDN', 'ACCOUNT_NUMBER', 'BILL_NUMBER', 'REG_NUMBER', 'INVOICE_NUMBER'
    ))
);

-- Disbursement Batches (for bulk payments to bank)
CREATE TABLE disbursement_batches (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    
    -- Batch identification
    batch_ref VARCHAR(100) NOT NULL UNIQUE,
    
    -- Debit account
    account_dr VARCHAR(50) NOT NULL,
    
    -- Batch details
    narration TEXT,
    value_date DATE NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'KES',
    total_amount DECIMAL(15, 2) NOT NULL,
    transaction_count INTEGER NOT NULL DEFAULT 0,
    
    -- CBS response
    cbs_ref VARCHAR(100),
    
    -- Status tracking
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    status_description TEXT,
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    
    CONSTRAINT valid_batch_status CHECK (status IN (
        'PENDING', 'QUEUED', 'FAILED', 
        'CBS_POSTED', 'CBS_ACK', 'CBS_COMPLETED', 'CBS_FAILED'
    ))
);

-- Individual Disbursements
CREATE TABLE disbursements (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    
    -- Batch relationship
    batch_id UUID REFERENCES disbursement_batches(id),
    
    -- Payment identification
    payment_ref VARCHAR(100) NOT NULL UNIQUE,
    batch_ref VARCHAR(100) NOT NULL,
    
    -- Disbursement type
    payment_type VARCHAR(50) NOT NULL,  -- EFT, AAT, MPESA, PESALINK, SWIFT
    
    -- Source (our system)
    source_type VARCHAR(50) NOT NULL,   -- LOAN_DISBURSEMENT, REFUND, etc.
    source_id UUID,                      -- Reference to loan, contribution, etc.
    member_id UUID NOT NULL,
    group_id UUID,
    
    -- Sender details
    sender_account VARCHAR(100),
    sender_bank VARCHAR(100),
    sender_bank_branch VARCHAR(100),
    sender_details TEXT,
    
    -- Beneficiary details
    beneficiary_account VARCHAR(100) NOT NULL,
    beneficiary_bank VARCHAR(100),
    beneficiary_bank_branch VARCHAR(100),
    beneficiary_name VARCHAR(200) NOT NULL,
    beneficiary_phone VARCHAR(20),
    beneficiary_details TEXT,
    
    -- Transaction details
    currency VARCHAR(3) NOT NULL DEFAULT 'KES',
    amount DECIMAL(15, 2) NOT NULL,
    remarks TEXT,
    purpose VARCHAR(50) NOT NULL,  -- DirectCredit, SalaryPayment, etc.
    
    -- Bank response
    external_ref VARCHAR(100),     -- MPESA ref, etc.
    cbs_ref VARCHAR(100),
    
    -- Status tracking
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    status_description TEXT,
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    
    CONSTRAINT valid_disbursement_status CHECK (status IN (
        'PENDING', 'QUEUED', 'FAILED',
        'CBS_POSTED', 'CBS_ACK', 'CBS_COMPLETED', 'CBS_FAILED',
        'THIRDPARTY_POSTED', 'THIRDPARTY_ACK', 'THIRDPARTY_COMPLETED', 'THIRDPARTY_FAILED'
    )),
    CONSTRAINT valid_payment_type CHECK (payment_type IN (
        'EFT', 'AAT', 'MPESA', 'PESALINK', 'SWIFT', 'MPESAAGENCYFLOAT'
    )),
    CONSTRAINT valid_purpose CHECK (purpose IN (
        'DirectCredit', 'DividendPayment', 'PensionsPayment', 'SalaryPayment'
    ))
);

-- Payment Allocations (how payments were distributed)
CREATE TABLE payment_allocations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    
    payment_transaction_id UUID NOT NULL REFERENCES payment_transactions(id),
    
    allocation_type VARCHAR(50) NOT NULL,  -- CONTRIBUTION, LOAN_REPAYMENT, OVERPAYMENT
    allocation_order INTEGER NOT NULL,
    
    -- Target reference
    target_id UUID,                         -- contribution_id or loan_id
    target_type VARCHAR(50),                -- CONTRIBUTION, LOAN
    
    -- Amounts
    allocated_amount DECIMAL(15, 2) NOT NULL,
    
    -- Status
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    processed_at TIMESTAMP WITH TIME ZONE,
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT valid_allocation_type CHECK (allocation_type IN (
        'CONTRIBUTION', 'LOAN_REPAYMENT', 'OVERPAYMENT', 'REFUND'
    ))
);

-- Idempotency tracking
CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    request_hash VARCHAR(64),
    response_status INTEGER,
    response_body TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Bank API tokens cache (backup to Redis)
CREATE TABLE api_tokens (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    token_type VARCHAR(50) NOT NULL,  -- COLLECTIONS, PAYMENTS
    access_token TEXT NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX idx_payment_txn_reference ON payment_transactions(txn_reference);
CREATE INDEX idx_payment_member ON payment_transactions(member_id);
CREATE INDEX idx_payment_status ON payment_transactions(status);
CREATE INDEX idx_payment_created ON payment_transactions(created_at);
CREATE INDEX idx_payment_payer_identifier ON payment_transactions(payer_identifier, payer_identifier_type);

CREATE INDEX idx_batch_ref ON disbursement_batches(batch_ref);
CREATE INDEX idx_batch_status ON disbursement_batches(status);

CREATE INDEX idx_disbursement_batch ON disbursements(batch_id);
CREATE INDEX idx_disbursement_payment_ref ON disbursements(payment_ref);
CREATE INDEX idx_disbursement_member ON disbursements(member_id);
CREATE INDEX idx_disbursement_status ON disbursements(status);

CREATE INDEX idx_allocation_payment ON payment_allocations(payment_transaction_id);
CREATE INDEX idx_allocation_target ON payment_allocations(target_id, target_type);

CREATE INDEX idx_idempotency_key ON idempotency_keys(idempotency_key);
CREATE INDEX idx_idempotency_expires ON idempotency_keys(expires_at);

-- Trigger for updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_payment_transactions_updated_at 
    BEFORE UPDATE ON payment_transactions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_disbursement_batches_updated_at 
    BEFORE UPDATE ON disbursement_batches
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_disbursements_updated_at 
    BEFORE UPDATE ON disbursements
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
