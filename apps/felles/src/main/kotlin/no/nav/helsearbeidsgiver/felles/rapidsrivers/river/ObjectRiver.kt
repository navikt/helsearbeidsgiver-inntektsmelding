package no.nav.helsearbeidsgiver.felles.rapidsrivers.river

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.KafkaKey
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

/**
 * En forenklet river som minimerer håndtering av JSON til fordel for objekter.
 *
 * __Viktig!__: Den enkleste feilen er å ikke kalle [connect]. Gjøres ikke det vil ikke riveren være aktiv.
 *
 * Eksempel på bruk:
 * ```
 * fun main() {
 *     RapidApplication.create(System.getenv())
 *         .also {
 *             HobbitFoodRiver().connect(it)
 *         }
 *         .start()
 * }
 *
 * data class LotrCharacter(
 *     val race: Race,
 *     val name: String,
 *     val height: Int?
 * )
 *
 * class HobbitFoodRiver : ObjectRiver<LotrCharacter>() {
 *
 *     override fun les(json: Map<Key, JsonElement>): LotrCharacter =
 *         LotrCharacter(
 *             race = Key.RACE.krev(Race.HOBBIT, Race.serializer(), json),
 *             name = Key.NAME.les(String.serializer(), json),
 *             height = Key.HEIGHT.lesOrNull(Int.serializer(), json)
 *         )
 *
 *     override fun LotrCharacter.bestemNoekkel(): KafkaKey = KafkaKey(name)
 *
 *     override fun LotrCharacter.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
 *         val favouriteFood = when (name) {
 *             "Frodo" -> "\uD83C\uDF53"
 *             "Sam" -> "\uD83E\uDD54"
 *             "Merry" -> "\uD83C\uDF44"
 *             "Pippin" -> "🍎"
 *             else -> "\uD83E\uDD37"
 *         }
 *
 *         return mapOf(
 *             Key.RACE to race.toJson(Race.serializer()),
 *             Key.NAME to name.toJson(String.serializer()),
 *             Key.HEIGHT to height.toJson(Int.serializer().nullable),
 *             Key.FAVOURITE_FOOD to favouriteFood.toJson(String.serializer())
 *         )
 *     }
 *
 *     override fun LotrCharacter.haandterFeil(json: Map<Key, JsonElement>, error: Throwable): Map<Key, JsonElement> =
 *         // Sikkert lurt å logge noe her
 *         mapOf(
 *             Key.FEILMELDING to "Klarte ikke finne favorittmaten til hobbiten :(".toJson(String.serializer())
 *         )
 *
 *     override fun LotrCharacter.loggfelt(): Map<String, String> =
 *         mapOf(
 *             Log.race(race),
 *             Log.name(name)
 *         )
 * }
 * ```
 *
 * (NB. Denne klassen er ikke en "ekte" river fra rapids-and-rivers-pakken, men den fungerer likt og bruker en "ekte" under panseret.)
 */
abstract class ObjectRiver<Melding : Any> {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    /** __Viktig!__: Denne må kalles ved instansiering av implementerende klasse. */
    fun connect(rapid: RapidsConnection) {
        OpenRiver(rapid, this::lesOgHaandter)
    }

    /**
     * Filtrerer meldinger ved å kaste exception når JSON mangler påkrevde verdier.
     * Kastede exceptions plukkes opp, men tolkes ikke videre.
     *
     * @param json innkommende melding.
     *
     * @return
     * Verdi lest fra [json]. Brukes som input i [haandter].
     * Returneres '`null`' så vil melding ignoreres.
     */
    protected abstract fun les(json: Map<Key, JsonElement>): Melding?

    /**
     * @receiver [Melding] - output fra [les].

     * @return
     * Nøkkel som utgående melding sendes sammen med.
     * Meldinger sendt med samme nøkkel vil opprettholde rekkefølgen mellom dem (og konsumeres av samme pod).
     */
    protected abstract fun Melding.bestemNoekkel(): KafkaKey

    /**
     * Riverens hovedfunksjon. Agerer på innkommende melding.
     * Kastede exceptions håndteres i [haandterFeil].
     *
     * @receiver [Melding] - output fra [les].
     *
     * @return
     * Utgående melding som skal publiseres når innkommende melding er ferdig prosessert.
     * Returneres '`null`' så vil ingen utgående melding publiseres.
     */
    protected abstract fun Melding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement>?

    /**
     * Kalles ved exception under [haandter].
     *
     * @return
     * Utgående melding som skal publiseres når feil er ferdig prosessert.
     * Default implementasjon returnerer '`null`', som betyr at ingen utgående melding publiseres.
     */
    protected abstract fun Melding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement>?

    /**
     * Bestemmer loggfelt som logges i [haandter] og [haandterFeil].
     *
     * @return
     * Map for loggfelt med feltnavn og innhold.
     */
    protected abstract fun Melding.loggfelt(): Map<String, String>

    /** Brukes av [OpenRiver]. */
    private fun lesOgHaandter(json: JsonElement): Pair<KafkaKey, Map<Key, JsonElement>>? {
        val jsonMap = json.toMap().filterValues { it !is JsonNull }

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
