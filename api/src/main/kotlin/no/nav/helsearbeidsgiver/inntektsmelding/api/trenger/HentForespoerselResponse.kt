@file:UseSerializers(LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.api.trenger

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.felles.FeilReport
import no.nav.helsearbeidsgiver.felles.ForespurtData
import no.nav.helsearbeidsgiver.felles.InntektPerMaaned
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import java.time.LocalDate

@Serializable
data class HentForespoerselResponse(
    val navn: String,
    val orgNavn: String,
    val innsenderNavn: String,
    val identitetsnummer: String,
    val orgnrUnderenhet: String,
    // TODO fjern når frontend støtter det
    val skjaeringstidspunkt: LocalDate?,
    val fravaersperioder: List<Periode>,
    val egenmeldingsperioder: List<Periode>,
    val bestemmendeFravaersdag: LocalDate,
    val eksternBestemmendeFravaersdag: LocalDate?,
    val bruttoinntekt: Double?,
    val tidligereinntekter: List<InntektPerMaaned>,
    // TODO fjern når frontend støtter det
    val behandlingsperiode: Periode?,
    // TODO fjern når frontend støtter det
    val behandlingsdager: List<LocalDate>,
    val forespurtData: ForespurtData?,
    val feilReport: FeilReport? = null,
    val success: JsonElement? = null,
    val failure: JsonElement? = null
)
