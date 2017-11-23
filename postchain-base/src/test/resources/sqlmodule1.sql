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
        INSERT INTO test_hello (chain_id, key, value, owner)
        VALUES (ctx.chain_id, key, value, owner);
    ELSIF current_owner = _owner THEN
        UPDATE test_kv SET value = _value WHERE chain_id = ctx.chain_id AND key = _key AND owner = _owner;
    ELSE
        RAISE EXCEPTION 'Record is owned by another user';
    END IF;
END;
$$ LANGUAGE plpgsql;

SELECT gtx_define_operation('test_set_value');




