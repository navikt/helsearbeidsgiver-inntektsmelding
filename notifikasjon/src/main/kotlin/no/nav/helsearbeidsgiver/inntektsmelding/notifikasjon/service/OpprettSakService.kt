package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.service

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStoreClassSpecific
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceMed3Steg
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

class OpprettSakService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStoreClassSpecific,
) : ServiceMed3Steg<
        OpprettSakService.Steg0,
        OpprettSakService.Steg1,
        OpprettSakService.Steg2,
        OpprettSakService.Steg3,
    >() {
    override val logger = logger()
    override val sikkerLogger = sikkerLogger()

    override val eventName = EventName.SAK_OPPRETT_REQUESTED
    override val startKeys =
        setOf(
            Key.UUID,
            Key.FORESPOERSEL_ID,
            Key.ORGNRUNDERENHET,
            Key.IDENTITETSNUMMER,
        )
    override val dataKeys =
        setOf(
            Key.ARBEIDSTAKER_INFORMASJON,
            Key.SAK_ID,
            Key.PERSISTERT_SAK_ID,
        )

    data class Steg0(
        val transaksjonId: UUID,
        val forespoerselId: UUID,
        val orgnr: Orgnr,
        val fnr: Fnr,
    )

    data class Steg1(
        val sykmeldt: PersonDato,
    )

    data class Steg2(
        val sakId: String,
    )

    data class Steg3(
        val persistertSakId: String,
    )

    override fun lesSteg0(melding: Map<Key, JsonElement>): Steg0 =
        Steg0(
            transaksjonId = Key.UUID.les(UuidSerializer, melding),
            forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding),
            orgnr = Key.ORGNRUNDERENHET.les(Orgnr.serializer(), melding),
            fnr = Key.IDENTITETSNUMMER.les(Fnr.serializer(), melding),
        )

    override fun lesSteg1(melding: Map<Key, JsonElement>): Steg1 =
        Steg1(
            sykmeldt = Key.ARBEIDSTAKER_INFORMASJON.les(PersonDato.serializer(), melding),
        )

    override fun lesSteg2(melding: Map<Key, JsonElement>): Steg2 =
        Steg2(
            sakId = Key.SAK_ID.les(String.serializer(), melding),
        )

    override fun lesSteg3(melding: Map<Key, JsonElement>): Steg3 =
        Steg3(
            persistertSakId = Key.PERSISTERT_SAK_ID.les(String.serializer(), melding),
        )

    override fun utfoerSteg0(steg0: Steg0) {
        rapid.publish(
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to BehovType.FULLT_NAVN.toJson(),
            Key.UUID to steg0.transaksjonId.toJson(),
            Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
            Key.IDENTITETSNUMMER to steg0.fnr.toJson(),
        )
    }

    override fun utfoerSteg1(
        steg0: Steg0,
        steg1: Steg1,
    ) {
        rapid.publish(
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to BehovType.OPPRETT_SAK.toJson(),
            Key.UUID to steg0.transaksjonId.toJson(),
            Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
            Key.ORGNRUNDERENHET to steg0.orgnr.toJson(),
            Key.ARBEIDSTAKER_INFORMASJON to steg1.sykmeldt.toJson(PersonDato.serializer()),
        )
    }

    override fun utfoerSteg2(
        steg0: Steg0,
        steg1: Steg1,
        steg2: Steg2,
    ) {
        rapid.publish(
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to BehovType.PERSISTER_SAK_ID.toJson(),
            Key.UUID to steg0.transaksjonId.toJson(),
            Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
            Key.SAK_ID to steg2.sakId.toJson(),
        )
    }

    override fun utfoerSteg3(
        steg0: Steg0,
        steg1: Steg1,
        steg2: Steg2,
        steg3: Steg3,
    ) {
        rapid.publish(
            Key.EVENT_NAME to EventName.SAK_OPPRETTET.toJson(),
            Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
            Key.SAK_ID to steg3.persistertSakId.toJson(),
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
            if (utloesendeBehov == BehovType.FULLT_NAVN) {
                val fnr = Key.IDENTITETSNUMMER.les(String.serializer(), melding)
                val ukjentPersonJson = PersonDato("Ukjent person", null, fnr).toJson(PersonDato.serializer())

                redisStore.set(RedisKey.of(fail.transaksjonId, Key.ARBEIDSTAKER_INFORMASJON), ukjentPersonJson)

                val meldingMedDefault = mapOf(Key.ARBEIDSTAKER_INFORMASJON to ukjentPersonJson).plus(melding)

                onData(meldingMedDefault)
            }
        }
    }

    override fun Steg0.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@OpprettSakService),
            Log.event(eventName),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId),
        )
}
