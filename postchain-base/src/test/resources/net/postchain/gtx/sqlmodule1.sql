CREATE TABLE test_kv (
  chain_id BIGINT,
  key TEXT NOT NULL,
  value TEXT NOT NULL,
  owner BYTEA NOT NULL,
  UNIQUE (chain_id, key)
);

CREATE FUNCTION test_set_value(ctx gtx_ctx, _key text, _value text, _owner gtx_signer)
  RETURNS BOOLEAN AS $$
DECLARE
  current_owner bytea;
BEGIN
  SELECT owner INTO current_owner
  FROM test_kv
  WHERE chain_id = ctx.chain_id AND key = _key;

  IF current_owner is NULL THEN
    INSERT INTO test_kv (chain_id, key, value, owner)
    VALUES (ctx.chain_id, _key, _value, _owner);
  ELSIF current_owner = _owner THEN
    UPDATE test_kv SET value = _value WHERE chain_id = ctx.chain_id AND key = _key AND owner = _owner;
  ELSE
    RAISE EXCEPTION 'Record is owned by another user';
  END IF;
  RETURN TRUE;
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION test_get_value(q_chain_id bigint, q_key text)
  RETURNS TABLE (val text, owner bytea)
AS $$
SELECT value as val, owner
FROM test_kv
WHERE test_kv.chain_id = q_chain_id AND test_kv.key = q_key;
$$ LANGUAGE sql;

CREATE FUNCTION test_get_keys(q_chain_id bigint, q_value text)
  RETURNS TABLE (key text)
AS $$
SELECT key
FROM test_kv
WHERE test_kv.chain_id = q_chain_id AND test_kv.value = q_value;
$$ LANGUAGE sql;

SELECT gtx_define_operation('test_set_value');
SELECT gtx_define_query('test_get_value');
SELECT gtx_define_query('test_get_keys');


