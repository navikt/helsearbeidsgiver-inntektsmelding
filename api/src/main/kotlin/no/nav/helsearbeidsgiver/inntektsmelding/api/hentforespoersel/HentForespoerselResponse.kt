@file:UseSerializers(LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.api.hentforespoersel

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.felles.ForespurtData
import no.nav.helsearbeidsgiver.felles.InntektPerMaaned
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import java.time.LocalDate

@Serializable
data class HentForespoerselResponse(
    val navn: String,
    val orgNavn: String,
    val innsenderNavn: String,
    val identitetsnummer: String,
    val orgnrUnderenhet: String,
    @Deprecated("fjern når det ikke lenger brukes i frontend")
    val skjaeringstidspunkt: LocalDate?,
    val fravaersperioder: List<Periode>,
    val egenmeldingsperioder: List<Periode>,
    val bestemmendeFravaersdag: LocalDate,
    val eksternBestemmendeFravaersdag: LocalDate?,
    val bruttoinntekt: Double?,
    val tidligereinntekter: List<InntektPerMaaned>,
    @Deprecated("fjern når det ikke lenger brukes i frontend")
    val behandlingsperiode: Periode?,
    @Deprecated("fjern når det ikke lenger brukes i frontend")
    val behandlingsdager: List<LocalDate>,
    val forespurtData: ForespurtData?,
    val erBesvart: Boolean,
    val feilReport: FeilReport? = null,
    val success: JsonElement? = null,
    val failure: JsonElement? = null
)

@Deprecated("fjern når det ikke lenger brukes i frontend")
@Serializable
data class FeilReport(
    val feil: MutableList<Feilmelding> = mutableListOf()
)

@Deprecated("fjern når det ikke lenger brukes i frontend")
@Serializable
data class Feilmelding(
    val melding: String,
    // TODO fjern når frontend ikke lenger bruker
    val status: Int? = null,
    val datafelt: Key? = null
)
