@file:UseSerializers(LocalDateSerializer::class, UuidSerializer::class)

package no.nav.helsearbeidsgiver.felles.domene

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer

// TODO erstatt med ResultJson
@Serializable
data class TilgangResultat(
    val tilgang: Tilgang? = null,
    val feilmelding: String? = null,
)

@Serializable
data class HentForespoerselResultat(
    val sykmeldtNavn: String,
    val avsenderNavn: String,
    val orgNavn: String,
    val inntekt: Inntekt?,
    val forespoersel: Forespoersel,
    val feil: Map<Key, String>,
)

@Serializable
data class HentForespoerselIderResultat(
    val ider: List<VedtaksperiodeIdForespoerselIdPar>,
)
