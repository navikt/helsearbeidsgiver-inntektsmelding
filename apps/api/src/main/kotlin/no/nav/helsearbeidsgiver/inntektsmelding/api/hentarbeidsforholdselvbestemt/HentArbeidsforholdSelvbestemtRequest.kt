@file:UseSerializers(LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.api.hentarbeidsforholdselvbestemt

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.time.LocalDate

@Serializable
data class HentArbeidsforholdSelvbestemtRequest(
    val orgnr: Orgnr,
    val sykmeldtFnr: Fnr,
    val fom: LocalDate,
    val tom: LocalDate,
)
