@file:UseSerializers(LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.api.trenger

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.felles.ForespurtData
import no.nav.helsearbeidsgiver.felles.MottattHistoriskInntekt
import no.nav.helsearbeidsgiver.felles.Periode
import no.nav.helsearbeidsgiver.felles.serializers.LocalDateSerializer
import java.time.LocalDate

@Serializable
data class TrengerResponse(
    val navn: String,
    val orgNavn: String,
    val identitetsnummer: String,
    val orgnrUnderenhet: String,
    val fravaersperioder: List<Periode>,
    val egenmeldingsperioder: List<Periode>,
    val bruttoinntekt: Double?,
    val tidligereinntekter: List<MottattHistoriskInntekt>,
    val behandlingsperiode: Periode?,
    val behandlingsdager: List<LocalDate>,
    val forespurtData: List<ForespurtData>
)
