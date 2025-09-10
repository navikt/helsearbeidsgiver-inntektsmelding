package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.json.krev
import no.nav.hag.simba.utils.felles.json.les
import no.nav.hag.simba.utils.felles.model.Fail
import no.nav.hag.simba.utils.felles.utils.Log
import no.nav.hag.simba.utils.rr.KafkaKey
import no.nav.hag.simba.utils.rr.river.ObjectRiver
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.inntektsmelding.db.SelvbestemtImRepo
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

data class OppdaterImSomProsessertMelding(
    val eventName: EventName,
    val kontekstId: UUID,
    val inntektsmelding: Inntektsmelding,
)

class OppdaterImSomProsessertRiver(
    private val imRepo: InntektsmeldingRepository,
    private val selvbestemtImRepo: SelvbestemtImRepo,
) : ObjectRiver.Simba<OppdaterImSomProsessertMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): OppdaterImSomProsessertMelding? =
        if (setOf(Key.BEHOV, Key.DATA, Key.FAIL).any(json::containsKey)) {
            null
        } else {
            OppdaterImSomProsessertMelding(
                eventName = Key.EVENT_NAME.krev(EventName.INNTEKTSMELDING_DISTRIBUERT, EventName.serializer(), json),
                kontekstId = Key.KONTEKST_ID.les(UuidSerializer, json),
                inntektsmelding = Key.INNTEKTSMELDING.les(Inntektsmelding.serializer(), json),
            )
        }

    override fun OppdaterImSomProsessertMelding.bestemNoekkel(): KafkaKey = KafkaKey(inntektsmelding.type.id)

    override fun OppdaterImSomProsessertMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement>? {
        when (inntektsmelding.type) {
            is Inntektsmelding.Type.Forespurt,
            is Inntektsmelding.Type.ForespurtEkstern,
            -> imRepo.oppdaterSomProsessert(inntektsmelding.id)
            is Inntektsmelding.Type.Selvbestemt,
            is Inntektsmelding.Type.Fisker,
            is Inntektsmelding.Type.UtenArbeidsforhold,
            is Inntektsmelding.Type.Behandlingsdager,
            -> selvbestemtImRepo.oppdaterSomProsessert(inntektsmelding.id)
        }

        return null
    }

    override fun OppdaterImSomProsessertMelding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement> {
        val fail =
            Fail(
                feilmelding = "Klarte ikke markere inntektsmelding som prosessert i database.",
                kontekstId = kontekstId,
                utloesendeMelding = json,
            )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
    }

    override fun OppdaterImSomProsessertMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@OppdaterImSomProsessertRiver),
            Log.event(eventName),
            Log.kontekstId(kontekstId),
            Log.inntektsmeldingId(inntektsmelding.id),
            when (inntektsmelding.type) {
                is Inntektsmelding.Type.Forespurt,
                is Inntektsmelding.Type.ForespurtEkstern,
                -> Log.forespoerselId(inntektsmelding.type.id)
                is Inntektsmelding.Type.Selvbestemt,
                is Inntektsmelding.Type.Fisker,
                is Inntektsmelding.Type.UtenArbeidsforhold,
                is Inntektsmelding.Type.Behandlingsdager,
                ->
                    Log.selvbestemtId(
                        inntektsmelding.type.id,
                    )
            },
        )
}
