package no.nav.helsearbeidsgiver.felles.rapidsrivers.river

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.Key
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

    protected abstract fun Melding.haandter(json: Map<Pri.Key, JsonElement>): Map<Key, JsonElement>?

    protected abstract fun Melding.haandterFeil(
        json: Map<Pri.Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement>?

    protected abstract fun Melding.loggfelt(): Map<String, String>

    private fun lesOgHaandter(json: JsonElement): Map<Key, JsonElement>? {
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
            val utgaaende =
                innkommende?.let {
                    runCatching {
                        it.haandter(jsonMap)
                    }.getOrElse { e ->
                        it.haandterFeil(jsonMap, e)
                    }
                }

            utgaaende?.takeIf { it.isNotEmpty() }
        }
    }
}
