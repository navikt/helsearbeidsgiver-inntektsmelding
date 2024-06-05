@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.api.hentselvbestemtim

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer

@Serializable
data class HentSelvbestemtImResponseSuccess(
    val selvbestemtInntektsmelding: Inntektsmelding
)

@Serializable
data class HentSelvbestemtImResponseFailure(
    val error: String
)
