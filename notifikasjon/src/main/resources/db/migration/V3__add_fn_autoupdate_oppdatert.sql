CREATE FUNCTION autoupdate_column_oppdatert()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.oppdatert = now();
    RETURN NEW;
END;
$$
    language 'plpgsql';
