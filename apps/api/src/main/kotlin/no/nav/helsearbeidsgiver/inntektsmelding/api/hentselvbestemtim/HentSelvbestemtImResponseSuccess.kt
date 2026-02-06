@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.api.hentselvbestemtim

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.hag.simba.utils.felles.domene.InntektsmeldingIntern as Inntektsmelding

@Serializable
data class HentSelvbestemtImResponseSuccess(
    val selvbestemtInntektsmelding: Inntektsmelding,
)
