package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.service

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStoreClassSpecific
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceMed1Steg
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

class OpprettOppgaveService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStoreClassSpecific,
) : ServiceMed1Steg<OpprettOppgaveService.Steg0, OpprettOppgaveService.Steg1>() {
    override val logger = logger()
    override val sikkerLogger = sikkerLogger()

    override val eventName = EventName.OPPGAVE_OPPRETT_REQUESTED
    override val startKeys =
        setOf(
            Key.UUID,
            Key.FORESPOERSEL_ID,
            Key.ORGNRUNDERENHET,
        )
    override val dataKeys =
        setOf(
            Key.VIRKSOMHET,
        )

    data class Steg0(
        val transaksjonId: UUID,
        val forespoerselId: UUID,
        val orgnr: Orgnr,
    )

    data class Steg1(
        val orgNavn: String,
    )

    override fun lesSteg0(melding: Map<Key, JsonElement>): Steg0 =
        Steg0(
            transaksjonId = Key.UUID.les(UuidSerializer, melding),
            forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding),
            orgnr = Key.ORGNRUNDERENHET.les(Orgnr.serializer(), melding),
        )

    override fun lesSteg1(melding: Map<Key, JsonElement>): Steg1 =
        Steg1(
            orgNavn = Key.VIRKSOMHET.les(String.serializer(), melding),
        )

    override fun utfoerSteg0(steg0: Steg0) {
        rapid.publish(
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to BehovType.VIRKSOMHET.toJson(),
            Key.UUID to steg0.transaksjonId.toJson(),
            Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
            Key.ORGNRUNDERENHET to steg0.orgnr.toJson(),
        )
    }

    override fun utfoerSteg1(
        steg0: Steg0,
        steg1: Steg1,
    ) {
        rapid.publish(
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to BehovType.OPPRETT_OPPGAVE.toJson(),
            Key.UUID to steg0.transaksjonId.toJson(),
            Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
            Key.ORGNRUNDERENHET to steg0.orgnr.toJson(),
            Key.VIRKSOMHET to steg1.orgNavn.toJson(),
        )
    }

    override fun onError(
        melding: Map<Key, JsonElement>,
        fail: Fail,
    ) {
        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(eventName),
            Log.transaksjonId(fail.transaksjonId),
        ) {
            val utloesendeBehov = Key.BEHOV.lesOrNull(BehovType.serializer(), fail.utloesendeMelding.toMap())
            if (utloesendeBehov == BehovType.VIRKSOMHET) {
                val defaultVirksomhetNavnJson = "Arbeidsgiver".toJson()

                redisStore.set(RedisKey.of(fail.transaksjonId, Key.VIRKSOMHET), defaultVirksomhetNavnJson)

                val meldingMedDefault = mapOf(Key.VIRKSOMHET to defaultVirksomhetNavnJson).plus(melding)

                return onData(meldingMedDefault)
            } else {
                redisStore.set(RedisKey.of(fail.transaksjonId), fail.feilmelding.toJson())
            }
        }
    }

    override fun Steg0.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@OpprettOppgaveService),
            Log.event(eventName),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId),
        )
}
