@file:UseSerializers(LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer

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
