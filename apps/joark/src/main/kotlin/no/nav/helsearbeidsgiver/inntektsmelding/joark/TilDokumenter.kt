package no.nav.helsearbeidsgiver.inntektsmelding.joark

import no.nav.helsearbeidsgiver.dokarkiv.domene.Dokument
import no.nav.helsearbeidsgiver.dokarkiv.domene.DokumentVariant
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument.PdfDokument
import no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument.transformToXML
import java.util.Base64
import java.util.UUID

private val base64 = Base64.getEncoder()

fun tilDokumenter(
    uuid: UUID,
    inntektsmelding: Inntektsmelding,
): List<Dokument> =
    listOf(
        Dokument(
            tittel = "Inntektsmelding",
            // TODO Denne må vi undersøke om vi vil bruke videre. Dette er koden til Altinn-service, som trolig brukes til å filtrere journalposter et sted.
            brevkode = "4936",
            dokumentVarianter =
                listOf(
                    DokumentVariant(
                        filtype = "XML",
                        fysiskDokument = transformToXML(inntektsmelding).toByteArray().encode(),
                        variantFormat = "ORIGINAL",
                        filnavn = "ari-$uuid.xml",
                    ),
//                DokumentVariant(
//                    filtype = "JSON",
//                    fysiskDokument = customObjectMapper().writeValueAsString(inntektsmelding)
//                        .toByteArray()
//                        .encode(),
//                    variantFormat = "ARKIV",
//                    filnavn = "ari-$uuid.json"
//                ),
                    DokumentVariant(
                        filtype = "PDFA",
                        fysiskDokument = PdfDokument(inntektsmelding).export().encode(),
                        variantFormat = "ARKIV",
                        filnavn = "ari-$uuid.pdf",
                    ),
                ),
        ),
    )

private fun ByteArray.encode(): String = base64.encodeToString(this)
