CREATE TABLE stk_push_requests (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    collection_ref VARCHAR(100) NOT NULL UNIQUE,
    collection_type VARCHAR(50) NOT NULL,
    source_id UUID NOT NULL,
    member_id UUID NOT NULL,
    group_id UUID,
    amount DECIMAL(15, 2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'KES',
    phone_number VARCHAR(20) NOT NULL,
    account_reference VARCHAR(50),
    transaction_desc VARCHAR(255),
    merchant_request_id VARCHAR(100),
    checkout_request_id VARCHAR(100),
    mpesa_receipt_number VARCHAR(50),
    result_code INTEGER,
    result_desc TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'INITIATED',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT valid_stk_status CHECK (status IN ('INITIATED','STK_SENT','COMPLETED','FAILED','CANCELLED','TIMEOUT')),
    CONSTRAINT valid_stk_type CHECK (collection_type IN ('LOAN_REPAYMENT','CONTRIBUTION'))
);
CREATE INDEX idx_stk_collection_ref ON stk_push_requests(collection_ref);
CREATE INDEX idx_stk_checkout ON stk_push_requests(checkout_request_id);
CREATE INDEX idx_stk_member ON stk_push_requests(member_id);
CREATE INDEX idx_stk_source ON stk_push_requests(source_id, collection_type);
CREATE TRIGGER update_stk_push_requests_updated_at BEFORE UPDATE ON stk_push_requests
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
