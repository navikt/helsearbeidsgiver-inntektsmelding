@file:UseSerializers(YearMonthSerializer::class, LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.api.hentforespoersel

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.hag.simba.utils.kontrakt.domene.forespoersel.ForespurtData
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
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
    val forespurtData: ForespurtData,
    val erBesvart: Boolean,
    val erBegrenset: Boolean,
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
