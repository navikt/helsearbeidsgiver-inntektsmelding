package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.json.toPretty
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.river.ObjectRiver
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.NotifikasjonTekst
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.avbrytSak
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.settOppgaveUtgaatt
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

data class UtgaattForespoerselMelding(
    val eventName: EventName,
    val transaksjonId: UUID,
    val forespoerselId: UUID,
)

class UtgaattForespoerselRiver(
    private val linkUrl: String,
    private val agNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
) : ObjectRiver<UtgaattForespoerselMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): UtgaattForespoerselMelding? =
        // Obs!: Ignorerer ikke fail blankt så lenge vi vil sette sak og oppgave til utgått for forespørsler som ikke ble funnet.
        if (setOf(Key.BEHOV, Key.DATA).any(json::containsKey)) {
            null
        } else {
            val eventName = Key.EVENT_NAME.les(EventName.serializer(), json)
            val transaksjonId = Key.UUID.les(UuidSerializer, json)

            when (eventName) {
                // Forespørsler som ble forkastet nylig matcher her
                EventName.FORESPOERSEL_FORKASTET -> {
                    if (Key.FAIL in json) {
                        null
                    } else {
                        UtgaattForespoerselMelding(
                            eventName = eventName,
                            transaksjonId = transaksjonId,
                            forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, json),
                        )
                    }
                }

                // Forespørsler som ble forkastet for lenge siden matcher her dersom noen prøver å hente dem
                EventName.TRENGER_REQUESTED -> {
                    val fail = Key.FAIL.les(Fail.serializer(), json)
                    val behovType = Key.BEHOV.les(BehovType.serializer(), fail.utloesendeMelding.toMap())

                    if (
                        behovType != BehovType.HENT_TRENGER_IM ||
                        fail.feilmelding != "Klarte ikke hente forespørsel. Feilet med kode 'FORESPOERSEL_IKKE_FUNNET'."
                    ) {
                        null
                    } else {
                        val data =
                            fail.utloesendeMelding
                                .toMap()[Key.DATA]
                                ?.toMap()
                                .orEmpty()
                        val forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, data)

                        "Setter sak og oppgave til utgått for forespørsel '$forespoerselId' som ikke ble funnet.".also {
                            logger.info(it)
                            sikkerLogger.info(it)
                        }

                        UtgaattForespoerselMelding(eventName, transaksjonId, forespoerselId)
                    }
                }

                // Alle andre eventer ignoreres
                else -> null
            }
        }

    override fun UtgaattForespoerselMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        logger.info("Mottok melding med event '$eventName'.")
        sikkerLogger.info("Mottok melding:\n${json.toPretty()}")

        val lenke = NotifikasjonTekst.lenkeUtgaattForespoersel(linkUrl)

        agNotifikasjonKlient.settOppgaveUtgaatt(lenke, forespoerselId)
        agNotifikasjonKlient.avbrytSak(lenke, forespoerselId)

        return mapOf(
            Key.EVENT_NAME to EventName.SAK_OG_OPPGAVE_UTGAATT.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
        )
    }

    override fun UtgaattForespoerselMelding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement> {
        val fail =
            Fail(
                feilmelding = "Klarte ikke sette oppgave til utgått og/eller avbryte sak for forespurt inntektmelding.",
                event = eventName,
                transaksjonId = transaksjonId,
                forespoerselId = forespoerselId,
                utloesendeMelding = json.toJson(),
            )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
    }

    override fun UtgaattForespoerselMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@UtgaattForespoerselRiver),
            Log.event(eventName),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId),
        )
}
