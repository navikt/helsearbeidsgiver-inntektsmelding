@file:UseSerializers(LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.felles

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer

@Serializable
data class TilgangData(
    val tilgang: Tilgang? = null,
    // TODO denne kan sendes til frontend når det støttes der
    val feil: FeilReport? = null
)

@Serializable
data class HentForespoerselResultat(
    val sykmeldtNavn: String,
    val avsenderNavn: String,
    val orgNavn: String,
    val inntekt: Inntekt?,
    val forespoersel: Forespoersel,
    val feil: Map<Key, String>
)
