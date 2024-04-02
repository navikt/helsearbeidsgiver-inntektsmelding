DO
$$
BEGIN
        IF EXISTS
            (SELECT 1 from pg_roles where rolname = 'im-db')
        THEN
            ALTER USER "im-db" WITH REPLICATION;
END IF;
END
$$;
DO
$$
BEGIN
        IF EXISTS
            (SELECT 1 from pg_roles where rolname = 'simba_datastream_bruker')
        THEN
            ALTER USER "simba_datastream_bruker" WITH REPLICATION;
            ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO "simba_datastream_bruker";
GRANT USAGE ON SCHEMA public TO "simba_datastream_bruker";
            GRANT SELECT ON ALL TABLES IN SCHEMA public TO "simba_datastream_bruker";
END IF;
END
$$;
