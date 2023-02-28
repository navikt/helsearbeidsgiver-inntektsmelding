package no.nav.helsearbeidsgiver.inntektsmelding.joark

import no.nav.helsearbeidsgiver.dokarkiv.AvsenderMottaker
import no.nav.helsearbeidsgiver.dokarkiv.Bruker
import no.nav.helsearbeidsgiver.dokarkiv.Dokument
import no.nav.helsearbeidsgiver.dokarkiv.DokumentVariant
import no.nav.helsearbeidsgiver.dokarkiv.IdType
import no.nav.helsearbeidsgiver.dokarkiv.Journalposttype
import no.nav.helsearbeidsgiver.dokarkiv.OpprettJournalpostRequest
import no.nav.helsearbeidsgiver.felles.inntektsmelding.db.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.json.toJsonStr
import no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument.PdfDokument
import java.time.LocalDate
import java.util.Base64

/**
 * Journalføring til dagens løsning:
 * https://github.com/navikt/dokmotaltinn/blob/master/app/src/test/resources/__files/journalpostapi/opprettjournalpostrequest.json
 */
fun mapOpprettJournalpostRequest(uuid: String, inntektsmelding: InntektsmeldingDokument, arbeidsgiver: String): OpprettJournalpostRequest {
    return OpprettJournalpostRequest(
        tema = "SYK",
        behandlingsTema = "ab0326",
        tittel = "Inntektsmelding",
        journalposttype = Journalposttype.INNGAAENDE,
        journalfoerendeEnhet = null,
        kanal = "NAV_NO",
        bruker = Bruker(inntektsmelding.identitetsnummer, IdType.FNR), //  Bruker.id er fnr til personen kravet gjelder for
        eksternReferanseId = "ARI-$uuid",
        avsenderMottaker = AvsenderMottaker(
            id = inntektsmelding.orgnrUnderenhet,
            idType = IdType.ORGNR,
            navn = arbeidsgiver
        ),
        dokumenter = listOf(
            Dokument(
                tittel = "Inntektsmelding",
                brevkode = "4936",
                dokumentVarianter = listOf(
                    DokumentVariant(
                        filtype = "JSON",
                        fysiskDokument = inntektsmelding.toJsonStr(InntektsmeldingDokument.serializer())
                            .toByteArray()
                            .let {
                                Base64.getEncoder().encodeToString(it)
                            },
                        variantFormat = "ORIGINAL",
                        filnavn = "ari-$uuid.json"
                    ),
                    DokumentVariant(
                        filtype = "PDFA",
                        variantFormat = "ARKIV",
                        fysiskDokument = Base64.getEncoder().encodeToString(PdfDokument(inntektsmelding).export()),
                        filnavn = "ari-$uuid.pdf"
                    )
                )
            )
        ),
        datoMottatt = LocalDate.now()
    )
}
