CREATE TABLE IF NOT EXISTS certificate (
  certificate_iid SERIAL PRIMARY KEY,
  id        TEXT   NOT NULL,
  name      TEXT   NOT NULL,
  pubkey    BYTEA  NOT NULL,
  expires   BIGINT NOT NULL,
  authority BYTEA  NOT NULL,
  reason    BYTEA  NOT NULL
);
