DO
$$
BEGIN
        if not exists
            (select 1 from pg_replication_slots where slot_name = 'simba_replication')
        then
            PERFORM PG_CREATE_LOGICAL_REPLICATION_SLOT ('simba_replication', 'pgoutput');
end if;
end;
$$;
