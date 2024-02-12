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

CREATE TRIGGER aapen_sak_autoupdate_column_oppdatert
    BEFORE UPDATE
    ON aapen_sak
    FOR EACH ROW
EXECUTE FUNCTION autoupdate_column_oppdatert();
