package no.nav.helsearbeidsgiver.inntektsmelding.joark

import no.nav.helsearbeidsgiver.dokarkiv.AvsenderMottaker
import no.nav.helsearbeidsgiver.dokarkiv.Bruker
import no.nav.helsearbeidsgiver.dokarkiv.Dokument
import no.nav.helsearbeidsgiver.dokarkiv.DokumentVariant
import no.nav.helsearbeidsgiver.dokarkiv.IdType
import no.nav.helsearbeidsgiver.dokarkiv.Journalposttype
import no.nav.helsearbeidsgiver.dokarkiv.OpprettJournalpostRequest
import no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument.PdfDokument
import no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument.mapXmlDokument
import java.time.LocalDateTime
import java.util.Base64

/**
 * Journalføring til dagens løsning:
 * https://github.com/navikt/dokmotaltinn/blob/master/app/src/test/resources/__files/journalpostapi/opprettjournalpostrequest.json
 */
fun mapOpprettJournalpostRequest(uuid: String, inntektsmelding: Inntektsmelding): OpprettJournalpostRequest {
    return OpprettJournalpostRequest(
        tema = "FOR",
        // behandlingsTema = "ab0326", TODO Skal vi ha med behandlingstema?
        tittel = "Inntektsmelding",
        journalposttype = Journalposttype.INNGAAENDE,
        kanal = "NAV_NO",
        bruker = Bruker(inntektsmelding.identitetsnummer, IdType.FNR), //  Bruker.id er fnr til personen kravet gjelder for
        eksternReferanseId = "ARI-$uuid", // TODO Hva skal vi bruke som ekstern referanse?
        avsenderMottaker = AvsenderMottaker(
            id = inntektsmelding.orgnrUnderenhet,
            idType = IdType.ORGNR,
            navn = "Arbeidsgiver" // TODO Skal vi bruke ekte navn på arbeidsgiver?
        ),
        dokumenter = listOf(
            Dokument(
                dokumentVarianter = listOf(
                    DokumentVariant(
                        filtype = "PDFA",
                        variantFormat = "ARKIV",
                        fysiskDokument = Base64.getEncoder().encodeToString(PdfDokument().export(inntektsmelding))
                    ),
                    DokumentVariant(
                        filtype = "XML",
                        fysiskDokument = Base64.getEncoder().encodeToString(mapXmlDokument(inntektsmelding).toByteArray()),
                        variantFormat = "ORIGINAL"
                    )
                ),
                brevkode = "4936",
                tittel = "journalfoeringsTittel"
            )
        ),
        datoMottatt = LocalDateTime.now()
    )
}
