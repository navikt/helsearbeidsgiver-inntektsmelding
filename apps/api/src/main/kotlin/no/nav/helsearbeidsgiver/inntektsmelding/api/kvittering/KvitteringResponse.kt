@file:UseSerializers(OffsetDateTimeSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.api.kvittering

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Avsender
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Sykmeldt
import no.nav.helsearbeidsgiver.utils.json.serializer.OffsetDateTimeSerializer
import java.time.OffsetDateTime
import no.nav.hag.simba.utils.felles.domene.SkjemaInntektsmeldingIntern as SkjemaInntektsmelding

@Serializable
data class KvitteringResponse(
    val kvitteringNavNo: NavNo?,
    val kvitteringEkstern: Ekstern?,
) {
    @Serializable
    data class NavNo(
        val sykmeldt: Sykmeldt,
        val avsender: Avsender,
        val sykmeldingsperioder: List<Periode>,
        val skjema: SkjemaInntektsmelding,
        val mottatt: OffsetDateTime,
    )

    @Serializable
    data class Ekstern(
        val avsenderSystem: String,
        val referanse: String,
        val mottatt: OffsetDateTime,
    )
}
