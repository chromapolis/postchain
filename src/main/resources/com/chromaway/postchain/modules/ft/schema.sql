CREATE TABLE ft_assets (
  chain_id BIGINT,
  asset_iid SERIAL PRIMARY KEY,
  asset_id TEXT
);

CREATE TABLE ft_accounts (
    account_iid SERIAL PRIMARY KEY,
    tx_iid BIGINT,
    op_index INT,
    chain_id BIGINT,
    account_id BYTEA,
    account_type INT,
    account_desc BYTEA,
    UNIQUE (chain_id, account_id)
);

CREATE TABLE ft_balances (
    account_iid BIGINT REFERENCES ft_accounts(account_iid),
    asset_iid BIGINT REFERENCES  ft_assets(asset_iid),
    balance BIGINT,
    PRIMARY KEY (account_iid, asset_iid)
);

CREATE TABLE ft_history (
    tx_iid BIGINT,
    op_index INT,
    account_iid BIGINT REFERENCES ft_accounts(account_iid),
    asset_iid BIGINT REFERENCES ft_assets(asset_iid),
    delta BIGINT
);


CREATE FUNCTION ft_register_account(chain_id BIGINT, tx_iid BIGINT, op_index INT, account_id BYTEA, account_type INT, account_desc BYTEA)
RETURNS VOID AS $$
BEGIN
    INSERT INTO ft_accounts(chain_id, tx_iid, op_index, account_id, account_type, account_desc)
    VALUES (chain_id, tx_iid, op_index, account_id, account_type, account_desc);
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION ft_register_asset(chain_id BIGINT, asset_id TEXT)
RETURNS VOID AS $$
BEGIN
    INSERT INTO ft_assets(chain_id, asset_id)
    VALUES (chain_id, asset_id);
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION ft_find_account(chain_id_ BIGINT, account_id_ BYTEA)
RETURNS BIGINT AS $$
BEGIN
  RETURN (SELECT account_iid FROM ft_accounts WHERE
    chain_id = chain_id_ AND account_id = account_id_);
 END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION ft_get_account_desc (chain_id_ BIGINT, account_id_ BYTEA)
RETURNS BYTEA AS $$
BEGIN
    RETURN (SELECT account_desc FROM ft_accounts WHERE
        chain_id = chain_id_ AND account_id = account_id_);
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION ft_find_asset(chain_id_ BIGINT, asset_id_ TEXT)
RETURNS BIGINT AS $$
BEGIN
  RETURN (SELECT asset_iid FROM ft_assets WHERE
   chain_id = chain_id_ AND asset_id = asset_id_);
END;
$$ LANGUAGE  plpgsql;


CREATE FUNCTION ft_update
(chain_id BIGINT, tx_iid BIGINT, op_index INT, account_id BYTEA, asset_id TEXT, delta BIGINT)
RETURNS VOID AS $$
BEGIN
    PERFORM ft_update_raw(chain_id, tx_iid, op_index, account_id, asset_id, delta, FALSE);
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION ft_update_raw
(chain_id BIGINT, tx_iid BIGINT, op_index INT, account_id BYTEA, asset_id TEXT, delta BIGINT, allowNegative BOOLEAN)
RETURNS VOID AS $$
DECLARE
  account_iid_ BIGINT;
  asset_iid_ BIGINT;
  balance_ BIGINT;
BEGIN
  asset_iid_ = ft_find_asset(chain_id, asset_id);
  account_iid_ = ft_find_account(chain_id, account_id);
  SELECT balance INTO balance_
    FROM ft_balances WHERE ft_balances.account_iid = account_iid_ AND ft_balances.asset_iid = asset_iid_;

  IF (delta < 0) AND NOT allowNegative THEN
    IF (balance_ is NULL) OR (balance_ + delta) < 0 THEN
      RAISE EXCEPTION 'Insufficient balance';
    END IF;
  END IF;

  IF balance_ IS NULL THEN
    INSERT INTO ft_balances(account_iid, asset_iid, balance) VALUES (account_iid_, asset_iid_, delta);
  ELSE
    UPDATE ft_balances SET balance = balance + delta
    WHERE (ft_balances.account_iid = account_iid_) AND (ft_balances.asset_iid = asset_iid_);
  END IF;

  INSERT INTO ft_history (tx_iid, op_index, account_iid, asset_iid, delta)
    VALUES (tx_iid, op_index, account_iid_, asset_iid_, delta);
END;
$$ LANGUAGE plpgsql;

--- Queries

CREATE FUNCTION ft_get_balance (chain_id BIGINT, account_id BYTEA, asset_id TEXT)
RETURNS BIGINT AS $$
DECLARE
  account_iid_ BIGINT;
  asset_iid_ BIGINT;
  balance_ BIGINT;
BEGIN
  asset_iid_ = ft_find_asset(chain_id, asset_id);
  account_iid_ = ft_find_account(chain_id, account_id);
  SELECT balance INTO balance_
    FROM ft_balances WHERE ft_balances.account_iid = account_iid_ AND ft_balances.asset_iid = asset_iid_;

  IF balance_ is NULL THEN
    balance_ = 0;
   END IF;

   RETURN balance_;
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION ft_get_history(chain_id BIGINT, account_id BYTEA, asset_id TEXT)
RETURNS TABLE (
    delta BIGINT,
    tx_rid BYTEA,
    op_index INT,
    block_header_data BYTEA
) AS $$
DECLARE
  account_iid_ BIGINT;
  asset_iid_ BIGINT;
BEGIN
  asset_iid_ = ft_find_asset(chain_id, asset_id);
  account_iid_ = ft_find_account(chain_id, account_id);

  RETURN QUERY SELECT ft_history.delta, transactions.tx_rid, ft_history.op_index, blocks.block_header_data FROM ft_history
  INNER JOIN transactions ON ft_history.tx_iid = transactions.tx_iid
  INNER JOIN blocks ON transactions.block_iid = blocks.block_iid
  WHERE account_iid = account_iid_ AND asset_iid = asset_iid_;

END;
$$ LANGUAGE plpgsql;