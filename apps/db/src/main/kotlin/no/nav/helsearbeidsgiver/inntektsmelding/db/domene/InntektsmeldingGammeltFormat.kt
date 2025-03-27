@file:Suppress("DEPRECATION")
@file:UseSerializers(LocalDateSerializer::class, UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.db.domene

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import java.time.LocalDate
import java.util.UUID

@Deprecated("Kun brukt for å lese gamle databaserader.")
@Serializable
data class InntektsmeldingGammeltFormat(
    val vedtaksperiodeId: UUID? = null,
    val orgnrUnderenhet: String,
    val identitetsnummer: String,
    val fraværsperioder: List<Periode>,
    val egenmeldingsperioder: List<Periode>,
    val arbeidsgiverperioder: List<Periode>,
    val fullLønnIArbeidsgiverPerioden: FullLoennIAgpGammeltFormat? = null,
    val inntekt: InntektGammeltFormat? = null,
    val inntektsdato: LocalDate? = null,
    val bestemmendeFraværsdag: LocalDate? = null,
    val naturalytelser: List<NaturalytelseGammeltFormat>? = null,
    val refusjon: RefusjonGammeltFormat,
    val innsenderNavn: String? = null,
    val telefonnummer: String? = null,
)

@Deprecated("Kun brukt for å lese gamle databaserader.")
@Serializable
enum class AarsakInnsendingGammeltFormat(
    val value: String,
) {
    NY("Ny"),
    ENDRING("Endring"),
}
