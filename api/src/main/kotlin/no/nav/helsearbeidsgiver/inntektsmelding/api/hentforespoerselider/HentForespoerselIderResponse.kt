@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.api.hentforespoerselider

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.felles.domene.VedtaksperiodeIdForespoerselIdPar
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer

@Serializable
data class HentForespoerselIderResponse(
    val ider: List<VedtaksperiodeIdForespoerselIdPar>,
)
