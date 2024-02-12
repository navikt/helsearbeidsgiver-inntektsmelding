@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.api.hentaapenim

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer

@Serializable
data class HentAapenImResponseSuccess(
    val aapenInntektsmelding: Inntektsmelding
)

@Serializable
data class HentAapenImResponseFailure(
    val error: String
)
