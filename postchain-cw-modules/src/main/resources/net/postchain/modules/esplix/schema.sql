CREATE TABLE IF NOT EXISTS mcs_r2_chains (
  chain_iid SERIAL PRIMARY KEY,
  chain_id BYTEA NOT NULL,
  nonce BYTEA NOT NULL,
  last_id BYTEA NOT NULL, -- Would be nice with referential integrity, but we would get circular dependnecies
  UNIQUE (chain_id),
  UNIQUE (nonce)
);

CREATE TABLE IF NOT EXISTS mcs_r2_messages (
  message_iid SERIAL PRIMARY KEY,
  chain_iid bigint NOT NULL REFERENCES mcs_r2_chains(chain_iid),
  tx_iid BIGINT NOT NULL REFERENCES transactions(tx_iid),
  call_index INTEGER NOT NULL,
  message_id BYTEA NOT NULL,
  payload BYTEA NOT NULL,
  UNIQUE (message_id)
);

CREATE OR REPLACE FUNCTION mcs_r2_createChain
(p_nonce bytea, p_chain_id bytea, p_tx_iid BIGINT, p_call_index INTEGER, p_payload bytea)
RETURNS bigint AS $$
DECLARE
 chain_iid_ bigint;
BEGIN

 INSERT INTO mcs_r2_chains (chain_id, nonce, last_id)
 VALUES (p_chain_id, p_nonce, p_chain_id)
 RETURNING chain_iid INTO chain_iid_;

 INSERT INTO mcs_r2_messages (chain_iid, tx_iid, call_index, message_id, payload)
 VALUES (chain_iid_, p_tx_iid, p_call_index, p_chain_id, p_payload);

 RETURN chain_iid_;

END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION mcs_r2_postMessage
  (p_tx_iid BIGINT, p_call_index INTEGER, p_message_id BYTEA, p_prev_id BYTEA, p_payload  BYTEA)
  RETURNS VOID AS $$
DECLARE
  ret RECORD;
BEGIN
  SELECT
    c.chain_iid,
    c.last_id
  INTO ret
  FROM mcs_r2_chains c
    JOIN mcs_r2_messages m ON c.chain_iid = m.chain_iid
  WHERE m.message_id = p_prev_id;

  IF ret IS NULL
  THEN
    RAISE EXCEPTION 'USERERROR previous message id does not exist';
  END IF;
  IF ret.last_id != p_prev_id
  THEN
    RAISE EXCEPTION 'USERERROR previous message id is not last in chain';
  END IF;

  INSERT INTO mcs_r2_messages (chain_iid, tx_iid, call_index, message_id, payload)
  VALUES (ret.chain_iid, p_tx_iid, p_call_index, p_message_id, p_payload);

  UPDATE mcs_r2_chains
  SET last_id = p_message_id
  WHERE chain_iid = ret.chain_iid;
END;
$$ LANGUAGE plpgsql;


-- maxHits integer always present
-- recipient
-- sinceHash only include messages after this GTX hash. If null fetch
--    all the way back from genesis.
CREATE OR REPLACE FUNCTION findRatatoskMessages(p_chain_id BYTEA, since_message_id BYTEA, max_hits INTEGER)
  RETURNS TABLE(
    gtx        BYTEA,
    gtx_id     BYTEA,
    call_index INTEGER[]
  )
AS $$
DECLARE
  since_message_iid BIGINT DEFAULT -1;
BEGIN
  IF (since_message_id IS NOT NULL)
  THEN
    SELECT m.message_iid
    INTO since_message_iid
    FROM mcs_r2_messages m
        JOIN mcs_r2_chains c on m.chain_iid = c.chain_iid
    WHERE m.message_id = since_message_id AND c.chain_id = p_chain_id;
    IF (since_message_iid IS NULL)
    THEN
      RAISE 'cannot find sinceMessageId % on chain %', since_message_id, p_chain_id;
    END IF;
  END IF;

  RETURN QUERY

  SELECT sub.tx_data gtx, sub.tx_rid gtx_id, array_agg(sub.call_index ORDER BY sub.call_index) call_index
  FROM
  (
  SELECT
    t.tx_iid,
    t.tx_data,
    t.tx_rid,
    m.call_index
  FROM mcs_r2_messages m
    JOIN mcs_r2_chains c ON m.chain_iid = c.chain_iid
    JOIN transactions t ON m.tx_iid = t.tx_iid
  WHERE c.chain_id=p_chain_id AND m.message_iid > since_message_iid
  ORDER BY t.tx_iid, m.call_index ASC
  LIMIT max_hits) sub
  GROUP BY sub.tx_rid, sub.tx_iid, sub.tx_data
  ORDER BY sub.tx_iid ASC;


END; $$
LANGUAGE 'plpgsql';



CREATE OR REPLACE FUNCTION create_index_if_not_exists(t_name TEXT, i_name TEXT, index_sql TEXT)
  RETURNS VOID AS $$
DECLARE
  full_index_name VARCHAR;
  schema_name     VARCHAR;
BEGIN

  full_index_name = t_name || '_' || i_name;

  SELECT current_schema()
  INTO schema_name;
  --schema_name = 'public';

  IF NOT EXISTS(
      SELECT 1
      FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
      WHERE c.relname = full_index_name
            AND n.nspname = schema_name
  )
  THEN

    EXECUTE 'CREATE INDEX ' || full_index_name || ' ON ' || schema_name || '.' || t_name || ' ' ||
            index_sql;
  END IF;
END
$$
LANGUAGE plpgsql VOLATILE;

