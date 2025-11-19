@file:UseSerializers(UuidSerializer::class, OffsetDateTimeSerializer::class)

package no.nav.hag.simba.utils.felles.domene

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.AarsakInnsending
import no.nav.helsearbeidsgiver.utils.json.serializer.OffsetDateTimeSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import java.time.OffsetDateTime
import java.util.UUID
import no.nav.hag.simba.utils.felles.domene.InntektsmeldingIntern as Inntektsmelding

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class InnsendingIntern(
    val innsendingId: UUID,
    val skjema: SkjemaInntektsmeldingIntern,
    val aarsakInnsending: AarsakInnsending,
    val type: Inntektsmelding.Type,
    val innsendtTid: OffsetDateTime,
    @EncodeDefault
    val versjon: Int = 1,
)
