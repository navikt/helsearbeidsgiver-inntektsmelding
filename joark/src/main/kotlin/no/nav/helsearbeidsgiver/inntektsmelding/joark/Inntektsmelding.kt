@file:UseSerializers(LocalDateSerializer::class)
@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.joark

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.felles.LocalDateSerializer
import java.time.LocalDate

@Serializable
data class Inntektsmelding(
    val orgnrUnderenhet: String,
    val identitetsnummer: String,
    val behandlingsdagerFom: LocalDate,
    val behandlingsdagerTom: LocalDate,
    val behandlingsdager: List<LocalDate>,
    val egenmeldinger: List<Egenmelding>,
    val bruttonInntekt: Double,
    val bruttoBekreftet: Boolean,
    val utbetalerFull: Boolean,
    val begrunnelseRedusert: String?,
    val utbetalerHeleEllerDeler: Boolean,
    val refusjonPrMnd: Double?,
    val opphørerKravet: Boolean?,
    val opphørSisteDag: LocalDate?,
    val naturalytelser: List<Naturalytelse>,
    val bekreftOpplysninger: Boolean
)

@Serializable
data class Egenmelding(
    val fom: LocalDate,
    val tom: LocalDate
)

@Serializable
data class Naturalytelse(
    val naturalytelseKode: String,
    val dato: LocalDate,
    val beløp: Double
)
