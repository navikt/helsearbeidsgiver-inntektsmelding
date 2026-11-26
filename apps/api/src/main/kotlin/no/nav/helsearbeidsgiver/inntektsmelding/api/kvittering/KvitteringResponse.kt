@file:UseSerializers(OffsetDateTimeSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.api.kvittering

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Avsender
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Sykmeldt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.utils.json.serializer.OffsetDateTimeSerializer
import java.time.OffsetDateTime

@Serializable
data class KvitteringResponse(
    val kvitteringNavNo: NavNo?,
    val visGeneriskKvittering: Boolean,
) {
    @Serializable
    data class NavNo(
        val sykmeldt: Sykmeldt,
        val avsender: Avsender,
        val sykmeldingsperioder: List<Periode>,
        val skjema: SkjemaInntektsmelding,
        val mottatt: OffsetDateTime,
    )
}
