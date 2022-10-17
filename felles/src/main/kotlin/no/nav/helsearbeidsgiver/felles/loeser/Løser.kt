package no.nav.helsearbeidsgiver.felles.loeser

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.Key

/**
 * Grunnlaget for en enkel løser som lytter etter [behovType] og bruker [løs] for å finne løsningen på behovet.
 * [createReaders] kan brukes dersom man trenger å lese verdier fra behovet.
 *
 * __Viktig!__: Den enkleste feilen i en implementerende klasse er å ikke kalle [start] i `init`. Gjøres ikke det vil løseren ikke være aktiv.
 *
 * Eksempel på bruk:
 * ```
 * class SecondBreakfastLøser : Løser() {
 *     override val behovType = BehovType.SECOND_BREAKFAST
 *
 *     lateinit var hobbit(): Behov.() -> String
 *
 *     init {
 *         start()
 *     }
 *
 *     override fun BehovReader.createReaders() {
 *         hobbit = readFn(Key.HOBBIT)
 *     }
 *
 *     override fun Behov.løs(): String =
 *         if (hobbit() == "Pippin") "🍎"
 *         else ""
 * ```
 *
 * Implementasjonsdetaljer (ikke viktig for å bruke klassen):
 * [Behov] og [BehovReader] brukes for å forsikre oss om at samtlige nøkler som brukes til uthente verdier fra behov faktisk eksisterer i behovet.
 * Det kan garanteres basert på følgende:
 * - [Behov] wrapper et behov og gjør det utilgjengelig utenfor modulen.
 * - [BehovReader] gir muligheten til å opprette funksjoner som kan lese verdier fra [Behov]. Nøkler brukt til lesing registreres.
 * - [BehovReader] er kun tilgjengelig (utenfor modulen) i [createReaders].
 * - [Behov] er kun tilgjengelig (utenfor modulen) i [løs].
 * - [PacketSolver] markerer nøkler registrert i [BehovReader] som påkrevd for innkommende behov.
 */
abstract class Løser {
    /** Hvilken [BehovType] som løseren skal lytte etter og løse. */
    abstract val behovType: BehovType

    /** Opprett funksjoner for å lese verdier fra [Behov]. */
    abstract fun BehovReader.createReaders()

    /** Finner løsningen på behovet uttrykket i [Behov]. */
    abstract fun Behov.løs(): Any

    /** Nøkler som blir brukt for å lese verdier fra [Behov] (packet). */
    internal val behovReadingKeys: MutableSet<Key> = mutableSetOf()

    /** __Viktig!__: Denne må kalles i implementerende klasses `init`-funksjon. */
    fun start() {
        BehovReader().createReaders()
        PacketSolver(this)
    }

    /** Tilgjengeliggjør løsefunksjon [løs] for [PacketSolver] (kan ikke kalles direkte siden den extender intern klasse). */
    internal fun løsBehov(packet: JsonMessage): Any =
        Behov(packet).løs()

    /** Tilgjengeliggjør opprettelse av funksjoner for å lese verdier fra [Behov]. */
    inner class BehovReader internal constructor() {
        /** @return Funksjon for å lese verdi for [key] fra [Behov]. */
        fun readFn(key: Key): Behov.() -> String {
            behovReadingKeys.add(key)

            return { value(key) }
        }
    }

    /** Tilgjengeliggjør funksjoner opprettet i [createReaders]. */
    inner class Behov internal constructor(
        private val packet: JsonMessage
    ) {
        /** Les verdi for [key] fra [Behov]. */
        internal fun value(key: Key): String =
            packet[key.str].asText()
    }
}

/**
 * Implementerer logikken for rapids-and-rivers.
 *
 * Bruker [Løser] for å lytte etter behov og løse dem.
 */
private class PacketSolver(
    private val løser: Løser
) : River.PacketListener {
    val defaultErrorMessage = "Ukjent feil."

    init {
        val rapidsConnection = RapidApplication.create(System.getenv())

        River(rapidsConnection)
            .apply {
                validate { packet ->
                    packet.demandAll(Key.BEHOV.str, løser.behovType)
                    packet.rejectKey(Key.LØSNING.str)

                    løser.behovReadingKeys.forEach { packet.requireKey(it.str) }
                }
            }
            .register(this)

        rapidsConnection.start()
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val løsning = runCatching {
            løser.løsBehov(packet)
        }
            .map(::LøsningSuksess)
            .getOrElse {
                it.message
                    .orDefault(defaultErrorMessage)
                    .let(::Feilmelding)
                    .let(::LøsningFeil)
            }

        packet[Key.LØSNING.str] = løsning

        context.publish(packet.toJson())
    }
}

private sealed class Løsning

private class LøsningSuksess(
    val value: Any
) : Løsning()

private class LøsningFeil(
    val error: Feilmelding
) : Løsning()

private fun <T> T?.orDefault(default: T) =
    this ?: default
