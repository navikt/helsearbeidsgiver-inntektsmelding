package no.nav.helsearbeidsgiver.inntektsmelding.soeknad

import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.auth.AuthClient
import no.nav.hag.simba.utils.auth.IdentityProvider
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.rr.KafkaKey
import no.nav.hag.simba.utils.rr.river.ObjectRiver
import no.nav.helsearbeidsgiver.inntektsmelding.soeknad.klient.SoeknadKlient
import no.nav.helsearbeidsgiver.utils.cache.LocalCache
import java.util.UUID
import kotlin.time.Duration.Companion.hours

fun main() {
    createSoeknadKlient()
    ObjectRiver.connectToRapid {
        listOf(
            PlaceholderRiver(),
        )
    }
}

private fun createSoeknadKlient(): SoeknadKlient =
    SoeknadKlient(
        baseUrl = Env.soeknadBaseUrl,
        cacheConfig = LocalCache.Config(1.hours, 100),
        getAccessToken = AuthClient().tokenGetter(IdentityProvider.AZURE_AD, Env.soeknadScope),
    )

// Kun midlertidig for å holde appen i live
class PlaceholderRiver : ObjectRiver.Simba<PlaceholderRiver.Melding>() {
    data class Melding(
        val placeholder: String,
    )

    override fun les(json: Map<Key, JsonElement>): Melding? = null

    override fun Melding.bestemNoekkel(): KafkaKey = KafkaKey(UUID.randomUUID())

    override fun Melding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement>? = null

    override fun Melding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement>? = null

    override fun Melding.loggfelt(): Map<String, String> = emptyMap()
}
