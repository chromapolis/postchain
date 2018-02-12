CREATE TABLE r4_chains (
  chain_iid SERIAL PRIMARY KEY,
  chain_id BYTEA NOT NULL,
  nonce BYTEA NOT NULL,
  last_id BYTEA NOT NULL,
  UNIQUE (chain_id),
  UNIQUE (nonce)
);

CREATE TABLE r4_messages (
  message_iid SERIAL PRIMARY KEY,
  chain_iid bigint NOT NULL REFERENCES r4_chains(chain_iid),
  tx_iid BIGINT NOT NULL REFERENCES transactions(tx_iid),
  op_index INTEGER NOT NULL,
  message_id BYTEA NOT NULL,
  payload BYTEA NOT NULL,
  UNIQUE (message_id)
);

CREATE INDEX r4_messages_by_chain ON r4_messages(chain_iid, message_iid);

CREATE OR REPLACE FUNCTION r4_createChain
(p_nonce bytea, p_chain_id bytea, p_tx_iid BIGINT, p_op_index INTEGER, p_payload bytea)
RETURNS bigint AS $$
DECLARE
 chain_iid_ bigint;
BEGIN

 INSERT INTO r4_chains (chain_id, nonce, last_id)
 VALUES (p_chain_id, p_nonce, p_chain_id)
 RETURNING chain_iid INTO chain_iid_;

 INSERT INTO r4_messages (chain_iid, tx_iid, op_index, message_id, payload)
 VALUES (chain_iid_, p_tx_iid, p_op_index, p_chain_id, p_payload);

 RETURN chain_iid_;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION r4_postMessage
  (p_tx_iid BIGINT, p_op_index INTEGER, p_message_id BYTEA, p_prev_id BYTEA, p_payload  BYTEA)
  RETURNS VOID AS $$
DECLARE
  ret RECORD;
BEGIN
  SELECT
    c.chain_iid,
    c.last_id
  INTO ret
  FROM r4_messages m
   JOIN r4_chains c ON c.chain_iid = m.chain_iid
  WHERE m.message_id = p_prev_id;

  IF ret IS NULL
  THEN
    RAISE EXCEPTION 'USERERROR previous message id does not exist';
  END IF;
  IF ret.last_id != p_prev_id
  THEN
    RAISE EXCEPTION 'USERERROR previous message id is not last in chain';
  END IF;

  INSERT INTO r4_messages (chain_iid, tx_iid, op_index, message_id, payload)
  VALUES (ret.chain_iid, p_tx_iid, p_op_index, p_message_id, p_payload);

  UPDATE r4_chains
  SET last_id = p_message_id
  WHERE chain_iid = ret.chain_iid;
END;
$$ LANGUAGE plpgsql;


-- maxHits integer always present
-- recipient
-- sinceHash only include messages after this GTX hash. If null fetch
--    all the way back from genesis.
CREATE OR REPLACE FUNCTION r4_getMessages(p_chain_id BYTEA, since_message_id BYTEA, max_hits INTEGER)
  RETURNS TABLE(
    tx_data    BYTEA,
    tx_rid     BYTEA,
    op_indexes INTEGER[]
  )
AS $$
DECLARE
  since_message_iid BIGINT DEFAULT -1;
BEGIN
  IF (since_message_id IS NOT NULL)
  THEN
    SELECT m.message_iid
    INTO since_message_iid
    FROM r4_messages m
        JOIN r4_chains c on m.chain_iid = c.chain_iid
    WHERE m.message_id = since_message_id AND c.chain_id = p_chain_id;
    IF (since_message_iid IS NULL)
    THEN
      RAISE 'cannot find sinceMessageId % on chain %', since_message_id, p_chain_id;
    END IF;
  END IF;

  RETURN QUERY

  SELECT sub.tx_data as tx_data, sub.tx_rid as tx_rid, array_agg(sub.call_index ORDER BY sub.call_index) as op_indexes
  FROM
  (
  SELECT
    m.message_iid,
    t.tx_iid,
    t.tx_data,
    t.tx_rid,
    m.op_index
  FROM r4_chains c
    JOIN r4_messages m ON c.chain_iid = m.chain_iid
    JOIN transactions t ON m.tx_iid = t.tx_iid
  WHERE c.chain_id=p_chain_id AND m.message_iid > since_message_iid
  ORDER BY m.message_iid ASC
  LIMIT max_hits) sub
  GROUP BY sub.tx_rid, sub.tx_iid, sub.tx_data
  ORDER BY sub.tx_iid ASC;


END; $$
LANGUAGE 'plpgsql';



