package no.nav.helsearbeidsgiver.felles.rapidsrivers.river

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.KafkaKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.utils.json.fromJsonMapFiltered
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

/**
 * En forenklet river for pri-topic. Leser fra pri-topic og skriver til Simbas interne topic.
 *
 * Se [ObjectRiver] for mer info om bruk.
 */
abstract class PriObjectRiver<Melding : Any> {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    /** __Viktig!__: Denne må kalles ved instansiering av implementerende klasse. */
    fun connect(rapid: RapidsConnection) {
        OpenRiver(rapid, this::lesOgHaandter)
    }

    protected abstract fun les(json: Map<Pri.Key, JsonElement>): Melding?

    protected abstract fun Melding.bestemNoekkel(): KafkaKey

    protected abstract fun Melding.haandter(json: Map<Pri.Key, JsonElement>): Map<Key, JsonElement>?

    protected abstract fun Melding.haandterFeil(
        json: Map<Pri.Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement>?

    protected abstract fun Melding.loggfelt(): Map<String, String>

    private fun lesOgHaandter(json: JsonElement): Pair<KafkaKey, Map<Key, JsonElement>>? {
        val jsonMap = json.fromJsonMapFiltered(Pri.Key.serializer()).filterValues { it !is JsonNull }

        val innkommende = runCatching { les(jsonMap) }.getOrNull()

        val loggfelt =
            innkommende
                ?.runCatching { loggfelt() }
                ?.getOrElse { error ->
                    "Klarte ikke lage loggfelt.".also {
                        logger.error(it)
                        sikkerLogger.error(it, error)
                    }
                    null
                }?.toList()
                ?.toTypedArray()
                .orEmpty()

        return MdcUtils.withLogFields(*loggfelt) {
            if (innkommende == null) {
                null
            } else {
                val key =
                    runCatching {
                        innkommende.bestemNoekkel()
                    }.getOrElse { e ->
                        "Klarte ikke bestemme Kafka-nøkkel. Melding prosesseres ikke.".also {
                            logger.error(it)
                            sikkerLogger.error(it, e)
                        }
                        null
                    }

                if (key == null) {
                    null
                } else {
                    val msg =
                        runCatching {
                            innkommende.haandter(jsonMap)
                        }.getOrElse { e ->
                            innkommende.haandterFeil(jsonMap, e)
                        }

                    if (msg.isNullOrEmpty()) {
                        null
                    } else {
                        key to msg
                    }
                }
            }
        }
    }
}
