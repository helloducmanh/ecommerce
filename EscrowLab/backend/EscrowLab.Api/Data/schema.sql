-- ============================================================
-- EscrowLab schema. Chạy lại nhiều lần vẫn an toàn (idempotent).
-- ============================================================

CREATE TABLE IF NOT EXISTS users (
  id         uuid PRIMARY KEY,
  name       text NOT NULL,
  role       text NOT NULL CHECK (role IN ('buyer','seller','admin')),
  created_at timestamptz NOT NULL DEFAULT now()
);

-- Mỗi "nơi chứa tiền" là một account: ví người dùng và các tài khoản hệ thống.
-- Quy ước dấu: amount > 0 làm balance tăng, amount < 0 làm balance giảm.
-- Tổng balance của MỌI account luôn bằng 0 (EXTERNAL_BANK âm = tiền đã vào hệ thống).
CREATE TABLE IF NOT EXISTS accounts (
  id             uuid PRIMARY KEY,
  code           text NOT NULL UNIQUE,
  owner_id       uuid REFERENCES users(id),
  type           text NOT NULL CHECK (type IN ('WALLET','ESCROW','FEE','EXTERNAL','WITHDRAWAL_HOLD')),
  currency       text NOT NULL DEFAULT 'VND',
  allow_negative boolean NOT NULL DEFAULT false,
  balance        bigint NOT NULL DEFAULT 0,          -- số dư cache, luôn = SUM(ledger_entries.amount)
  created_at     timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT balance_non_negative CHECK (allow_negative OR balance >= 0)
);

CREATE TABLE IF NOT EXISTS ledger_transactions (
  id              uuid PRIMARY KEY,
  idempotency_key text NOT NULL UNIQUE,
  type            text NOT NULL,
  reference_type  text,
  reference_id    uuid,
  description     text,
  created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS ledger_entries (
  id             bigserial PRIMARY KEY,
  transaction_id uuid   NOT NULL REFERENCES ledger_transactions(id),
  account_id     uuid   NOT NULL REFERENCES accounts(id),
  amount         bigint NOT NULL CHECK (amount <> 0),
  balance_after  bigint NOT NULL,
  created_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_entries_account ON ledger_entries(account_id, id DESC);
CREATE INDEX IF NOT EXISTS ix_entries_tx ON ledger_entries(transaction_id);

-- Sổ cái chỉ được ghi thêm (append-only): cấm UPDATE/DELETE.
CREATE OR REPLACE FUNCTION forbid_mutation() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'ledger is append-only (% on %)', TG_OP, TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS ledger_entries_immutable ON ledger_entries;
CREATE TRIGGER ledger_entries_immutable BEFORE UPDATE OR DELETE ON ledger_entries
  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

DROP TRIGGER IF EXISTS ledger_transactions_immutable ON ledger_transactions;
CREATE TRIGGER ledger_transactions_immutable BEFORE UPDATE OR DELETE ON ledger_transactions
  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

-- Lớp bảo vệ cuối cùng: lúc COMMIT, tổng các dòng của mỗi giao dịch phải bằng 0.
CREATE OR REPLACE FUNCTION check_tx_balanced() RETURNS trigger AS $$
DECLARE s bigint;
BEGIN
  SELECT COALESCE(SUM(amount), 0) INTO s FROM ledger_entries WHERE transaction_id = NEW.transaction_id;
  IF s <> 0 THEN
    RAISE EXCEPTION 'unbalanced ledger transaction %', NEW.transaction_id;
  END IF;
  RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS ledger_entries_balanced ON ledger_entries;
CREATE CONSTRAINT TRIGGER ledger_entries_balanced AFTER INSERT ON ledger_entries
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_tx_balanced();

CREATE TABLE IF NOT EXISTS orders (
  id         uuid PRIMARY KEY,
  buyer_id   uuid NOT NULL REFERENCES users(id),
  seller_id  uuid NOT NULL REFERENCES users(id),
  title      text NOT NULL,
  amount     bigint NOT NULL CHECK (amount > 0),
  fee        bigint NOT NULL CHECK (fee >= 0),
  status     text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  shipped_at timestamptz,
  CHECK (buyer_id <> seller_id)
);
CREATE INDEX IF NOT EXISTS ix_orders_status ON orders(status);

CREATE TABLE IF NOT EXISTS order_events (
  id          bigserial PRIMARY KEY,
  order_id    uuid NOT NULL REFERENCES orders(id),
  from_status text,
  to_status   text NOT NULL,
  actor_id    uuid,
  note        text,
  created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS payment_intents (
  id           uuid PRIMARY KEY,
  user_id      uuid NOT NULL REFERENCES users(id),
  amount       bigint NOT NULL CHECK (amount > 0),
  status       text NOT NULL,           -- PENDING | SUCCEEDED | FAILED | REVIEW
  provider_ref text NOT NULL UNIQUE,
  created_at   timestamptz NOT NULL DEFAULT now(),
  updated_at   timestamptz NOT NULL DEFAULT now()
);

-- Mỗi event_id của gateway chỉ được xử lý một lần.
CREATE TABLE IF NOT EXISTS webhook_events (
  event_id    text PRIMARY KEY,
  payload     text NOT NULL,
  received_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS withdrawals (
  id           uuid PRIMARY KEY,
  user_id      uuid NOT NULL REFERENCES users(id),
  amount       bigint NOT NULL CHECK (amount > 0),
  bank_account text NOT NULL,
  status       text NOT NULL,           -- PENDING | COMPLETED | REJECTED
  created_at   timestamptz NOT NULL DEFAULT now(),
  updated_at   timestamptz NOT NULL DEFAULT now()
);
