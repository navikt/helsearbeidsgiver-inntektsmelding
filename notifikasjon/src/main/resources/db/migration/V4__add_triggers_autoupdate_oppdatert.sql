CREATE TRIGGER forespoersel_sak_autoupdate_column_oppdatert
    BEFORE UPDATE
    ON forespoersel_sak
    FOR EACH ROW
EXECUTE FUNCTION autoupdate_column_oppdatert();

CREATE TRIGGER forespoersel_oppgave_autoupdate_column_oppdatert
    BEFORE UPDATE
    ON forespoersel_oppgave
    FOR EACH ROW
EXECUTE FUNCTION autoupdate_column_oppdatert();

CREATE TRIGGER selvbestemt_sak_autoupdate_column_oppdatert
    BEFORE UPDATE
    ON selvbestemt_sak
    FOR EACH ROW
EXECUTE FUNCTION autoupdate_column_oppdatert();
