DO
$$
BEGIN
        IF NOT EXISTS
            (SELECT 1 FROM pg_replication_slots WHERE slot_name = 'simba_replication')
        THEN
            PERFORM PG_CREATE_LOGICAL_REPLICATION_SLOT ('simba_replication', 'pgoutput');
END IF;
END;
$$;
