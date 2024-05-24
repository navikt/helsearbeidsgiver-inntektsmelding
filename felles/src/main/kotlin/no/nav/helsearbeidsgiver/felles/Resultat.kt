@file:UseSerializers(LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import java.time.LocalDate

@Serializable
data class TilgangResultat(
    val tilgang: Tilgang? = null,
    // TODO denne kan sendes til frontend når det støttes der
    val feilmelding: String? = null
)

@Serializable
data class TrengerData(
    val forespoersel: Forespoersel,
    val fnr: String? = null,
    val orgnr: String? = null,
    val personDato: PersonDato? = null,
    val arbeidsgiver: PersonDato? = null,
    val virksomhetNavn: String? = null,
    val skjaeringstidspunkt: LocalDate? = null,
    val fravarsPerioder: List<Periode>? = null,
    val egenmeldingsPerioder: List<Periode>? = null,
    val forespurtData: ForespurtData? = null,
    val bruttoinntekt: Double? = null,
    val tidligereinntekter: List<InntektPerMaaned>? = null,
    val feilReport: FeilReport? = null
)

@Serializable
data class InntektData(
    val inntekt: Inntekt? = null,
    val feil: FeilReport? = null
)
