@file:UseSerializers(LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
data class PreutfyltResponse(
    // PDL
    val navn: String,
    val identitetsnummer: String,
    // Brreg
    val virksomhetsnavn: String,
    val orgnrUnderenhet: String,
    // TODO - fravaersperiode? sykemelding søknad - hente ut fra Team Flex
    val fravaersperiode: Map<String, List<MottattPeriode>>,
    // TODO - egenmeldingsperioder?  lag dummy - ikke klar ennå
    val egenmeldingsperioder: List<MottattPeriode>,
    // Inntektskomponenten
    val bruttoinntekt: Long,
    val tidligereinntekt: List<MottattHistoriskInntekt>,
    // TODO - behandlingsperiode? sykemelding søknad
    val behandlingsperiode: MottattPeriode,
    // Aareg
    val arbeidsforhold: List<MottattArbeidsforhold>
)
