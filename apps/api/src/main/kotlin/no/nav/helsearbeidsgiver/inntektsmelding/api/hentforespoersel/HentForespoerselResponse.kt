@file:UseSerializers(YearMonthSerializer::class, LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.api.hentforespoersel

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.felles.domene.ForespurtData
import no.nav.helsearbeidsgiver.felles.domene.InntektPerMaaned
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.YearMonthSerializer
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.time.LocalDate
import java.time.YearMonth

@Serializable
data class HentForespoerselResponse(
    val sykmeldt: Sykmeldt,
    val avsender: Avsender,
    val egenmeldingsperioder: List<Periode>,
    val sykmeldingsperioder: List<Periode>,
    val bestemmendeFravaersdag: LocalDate,
    val eksternInntektsdato: LocalDate?,
    val inntekt: Inntekt?,
    val forespurtData: ForespurtData?,
    val erBesvart: Boolean,
    // TODO utdaterte felt, slett etter overgangsperiode i frontend
    val navn: String?,
    val orgNavn: String?,
    val innsenderNavn: String?,
    val identitetsnummer: String,
    val orgnrUnderenhet: String,
    val fravaersperioder: List<Periode>,
    val eksternBestemmendeFravaersdag: LocalDate?,
    val bruttoinntekt: Double?,
    val tidligereinntekter: List<InntektPerMaaned>,
)

@Serializable
data class Sykmeldt(
    val fnr: Fnr,
    val navn: String?,
)

@Serializable
data class Avsender(
    val orgnr: Orgnr,
    val orgNavn: String?,
    val navn: String?,
)

@Serializable
data class Inntekt(
    val gjennomsnitt: Double,
    val historikk: Map<YearMonth, Double?>,
)
