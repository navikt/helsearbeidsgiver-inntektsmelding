package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river

import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.json.krev
import no.nav.hag.simba.utils.felles.json.les
import no.nav.hag.simba.utils.felles.json.lesOrNull
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.hag.simba.utils.felles.json.toPretty
import no.nav.hag.simba.utils.felles.model.Fail
import no.nav.hag.simba.utils.felles.utils.Log
import no.nav.hag.simba.utils.rr.KafkaKey
import no.nav.hag.simba.utils.rr.river.ObjectRiver
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
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
    val kontekstId: UUID,
    val forespoerselId: UUID,
)

class UtgaattForespoerselRiver(
    private val linkUrl: String,
    private val agNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
) : ObjectRiver.Simba<UtgaattForespoerselMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): UtgaattForespoerselMelding? =
        // Obs!: Ignorerer ikke fail blankt så lenge vi vil sette sak og oppgave til utgått for forespørsler som ikke ble funnet.
        if (setOf(Key.BEHOV, Key.DATA).any(json::containsKey)) {
            null
        } else {
            val fail = Key.FAIL.lesOrNull(Fail.serializer(), json)
            if (fail == null) {
                // Forespørsler som ble forkastet nylig matcher her
                UtgaattForespoerselMelding(
                    eventName = Key.EVENT_NAME.krev(EventName.FORESPOERSEL_FORKASTET, EventName.serializer(), json),
                    kontekstId = Key.KONTEKST_ID.les(UuidSerializer, json),
                    forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, json),
                )
            } else {
                // Forespørsler som ble forkastet for lenge siden matcher her dersom noen prøver å hente dem
                val eventName = Key.EVENT_NAME.les(EventName.serializer(), fail.utloesendeMelding)
                val behovType = Key.BEHOV.les(BehovType.serializer(), fail.utloesendeMelding)

                if (
                    behovType != BehovType.HENT_TRENGER_IM ||
                    fail.feilmelding != "Klarte ikke hente forespørsel. Feilet med kode 'FORESPOERSEL_IKKE_FUNNET'."
                ) {
                    null
                } else {
                    val data = fail.utloesendeMelding[Key.DATA]?.toMap().orEmpty()
                    val forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, data)

                    "Setter sak og oppgave til utgått for forespørsel '$forespoerselId' som ikke ble funnet.".also {
                        logger.info(it)
                        sikkerLogger.info(it)
                    }

                    UtgaattForespoerselMelding(eventName, fail.kontekstId, forespoerselId)
                }
            }
        }

    override fun UtgaattForespoerselMelding.bestemNoekkel(): KafkaKey = KafkaKey(forespoerselId)

    override fun UtgaattForespoerselMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        logger.info("Mottok melding med event '$eventName'.")
        sikkerLogger.info("Mottok melding:\n${json.toPretty()}")

        val lenke = NotifikasjonTekst.lenkeUtgaattForespoersel(linkUrl)

        agNotifikasjonKlient.settOppgaveUtgaatt(lenke, forespoerselId)
        agNotifikasjonKlient.avbrytSak(lenke, forespoerselId)

        return mapOf(
            Key.EVENT_NAME to EventName.SAK_OG_OPPGAVE_UTGAATT.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
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
                kontekstId = kontekstId,
                utloesendeMelding = json,
            )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
    }

    override fun UtgaattForespoerselMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@UtgaattForespoerselRiver),
            Log.event(eventName),
            Log.kontekstId(kontekstId),
            Log.forespoerselId(forespoerselId),
        )
}
