@file:UseSerializers(LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.api.trenger

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.felles.FeilReport
import no.nav.helsearbeidsgiver.felles.ForespurtData
import no.nav.helsearbeidsgiver.felles.InntektPerMaaned
import no.nav.helsearbeidsgiver.felles.Periode
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import java.time.LocalDate

@Serializable
data class TrengerResponse(
    val navn: String,
    val orgNavn: String,
    val innsenderNavn: String,
    val identitetsnummer: String,
    val orgnrUnderenhet: String,
    val skjaeringstidspunkt: LocalDate?,
    val fravaersperioder: List<Periode>,
    val egenmeldingsperioder: List<Periode>,
    val bruttoinntekt: Double?,
    val tidligereinntekter: List<InntektPerMaaned>,
    val behandlingsperiode: Periode?,
    val behandlingsdager: List<LocalDate>,
    val forespurtData: ForespurtData?,
    val feilReport: FeilReport? = null
)
