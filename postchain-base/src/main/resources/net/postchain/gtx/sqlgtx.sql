CREATE DOMAIN gtx_signer as bytea;
CREATE DOMAIN gtx_signers as bytea[];

CREATE TYPE gtx_ctx as (
    chain_id BIGINT,
    tx_iid BIGINT,
    op_index INT
);

CREATE TABLE gtx_sqlm_operations(
    id regproc,
    allow_no_signers boolean
);

CREATE TABLE gtx_sqlm_queries(
    id regproc
);

CREATE FUNCTION gtx_sqlm_get_functions()
RETURNS TABLE (
    name text,
    argnames text[],
    argtypes text[],
    allow_no_signers boolean
) AS $$
    SELECT  p.proname::text as name, p.proargnames as argnames,
            regexp_split_to_array(oidvectortypes(proargtypes), E', ') as argtypes,
            f.allow_no_signers as allow_no_signers
    FROM gtx_sqlm_operations f
    INNER JOIN pg_proc p ON f.id = p.oid;
$$ LANGUAGE SQL;

CREATE FUNCTION gtx_define_operation(opid regproc, _allow_no_signers boolean = false) RETURNS VOID AS $$
DECLARE
    argtypes text[];
BEGIN
   SELECT regexp_split_to_array(oidvectortypes(proargtypes), E', ') into argtypes
    FROM pg_proc p WHERE oid = opid;

    IF pg_get_function_result(opid) <> 'boolean' THEN
        RAISE EXCEPTION 'Operation must return boolean';
    END IF;

    IF argtypes[0] <> 'gtx_ctx' THEN
        RAISE EXCEPTION 'Operation first parameter must be gtx_ctx';
    END IF;

    IF NOT(_allow_no_signers OR (ARRAY['gtx_signer', 'gtx_signers'] && argtypes)) THEN
        RAISE EXCEPTION 'No signer parameter founds and allow_no_signers flag is not set';
    END IF;

    INSERT INTO gtx_sqlm_operations(id, allow_no_signers)
    VALUES (opid, _allow_no_signers);

END;
$$ LANGUAGE plpgsql;