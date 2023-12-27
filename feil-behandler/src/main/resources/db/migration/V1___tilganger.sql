DO
$$
    BEGIN
        IF EXISTS (SELECT 1
                   FROM pg_roles
                   WHERE rolname = 'cloudsqliamuser')
        THEN
            ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO cloudsqliamuser;
            ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO cloudsqliamuser;
        END IF;
    END
$$;
