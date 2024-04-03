DO
$$
BEGIN
    IF NOT EXISTS
        (SELECT 1 FROM pg_publication WHERE pubname = 'simba_publication')
    THEN
        CREATE PUBLICATION simba_publication FOR ALL TABLES;
    END IF;
END;
$$;
