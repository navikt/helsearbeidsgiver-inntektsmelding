DO
$$
BEGIN
        if not exists
            (select 1 from pg_publication where pubname = 'simba_publication')
        then
            CREATE PUBLICATION simba_publication for ALL TABLES;
end if;
end;
$$;
