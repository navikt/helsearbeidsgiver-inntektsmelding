package no.nav.helsearbeidsgiver.felles.loeser

import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.Key
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
 *     override fun LotrCharacter.haandter(): Map<Key, JsonElement> {
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
        OpenRiver(rapid, this)
    }

    /**
     * Filtrerer meldinger ved å kaste exception når JSON mangler påkrevde verdier.
     * Kastede exceptions plukkes opp, men tolkes ikke videre.
     *
     * @param json innkommende melding.
     *
     * @return Verdi lest fra [json]. Brukes som input i [haandter].
     */
    protected abstract fun les(json: Map<Key, JsonElement>): Melding

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
    protected abstract fun Melding.haandter(): Map<Key, JsonElement>?

    /**
     * Kalles ved exception under [haandter].
     *
     * @return
     * Utgående melding som skal publiseres når feil er ferdig prosessert.
     * Default implementasjon returnerer '`null`', som betyr at ingen utgående melding publiseres.
     */
    protected open fun Throwable.haandterFeil(json: Map<Key, JsonElement>): Map<Key, JsonElement>? {
        "Ukjent feil.".also {
            logger.error(it)
            sikkerLogger.error(it, this)
        }
        return null
    }

    /** Brukes av [OpenRiver]. */
    internal fun lesOgHaandter(json: Map<Key, JsonElement>): Map<Key, JsonElement>? {
        val innkommende = runCatching { les(json) }.getOrNull()

        val utgaaende = runCatching {
            innkommende?.haandter()
        }
            .getOrElse {
                it.haandterFeil(json)
            }

        if (utgaaende != null && utgaaende.isEmpty()) {
            "Utgående melding er tom.".also {
                logger.error(it)
                sikkerLogger.error(it)
            }
        }

        return utgaaende
    }
}
