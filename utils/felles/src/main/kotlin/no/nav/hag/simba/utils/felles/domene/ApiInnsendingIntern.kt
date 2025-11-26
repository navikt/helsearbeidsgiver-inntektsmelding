@file:UseSerializers(UuidSerializer::class, OffsetDateTimeSerializer::class)

package no.nav.hag.simba.utils.felles.domene

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntekt
import no.nav.helsearbeidsgiver.utils.json.serializer.OffsetDateTimeSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import java.time.OffsetDateTime
import java.util.UUID
import no.nav.hag.simba.utils.felles.domene.InntektsmeldingIntern as Inntektsmelding

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@Deprecated("Skal erstattes med im-domene 0.5.0")
data class ApiInnsendingIntern(
    val innsendingId: UUID,
    val skjema: ApiSkjemaInntektsmeldingIntern,
    val aarsakInnsending: AarsakInnsending,
    val type: Inntektsmelding.Type,
    val innsendtTid: OffsetDateTime,
    @EncodeDefault
    val versjon: Int = 1,
) {
    fun tilGammeltFormat(): InnsendingIntern =
        InnsendingIntern(
            innsendingId = this.innsendingId,
            skjema =
                SkjemaInntektsmeldingIntern(
                    forespoerselId = skjema.forespoerselId,
                    avsenderTlf = skjema.avsenderTlf,
                    agp = skjema.agp,
                    inntekt =
                        skjema.inntekt?.let {
                            Inntekt(
                                beloep = it.beloep,
                                inntektsdato = it.inntektsdato,
                                naturalytelser = skjema.naturalytelser,
                                endringAarsaker = it.endringAarsaker,
                            )
                        },
                    refusjon = skjema.refusjon,
                    naturalytelser = skjema.naturalytelser,
                ),
            aarsakInnsending = aarsakInnsending,
            type = type,
            innsendtTid = innsendtTid,
            versjon = versjon,
        )
}
