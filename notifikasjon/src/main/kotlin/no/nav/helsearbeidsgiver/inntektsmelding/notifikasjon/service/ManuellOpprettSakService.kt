package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.service

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Forespoersel
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Person
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.personMapSerializer
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.Service
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceMed4Steg
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.pipe.orDefault
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

class ManuellOpprettSakService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStore,
) : ServiceMed4Steg<
        ManuellOpprettSakService.Steg0,
        ManuellOpprettSakService.Steg1,
        ManuellOpprettSakService.Steg2,
        ManuellOpprettSakService.Steg3,
        ManuellOpprettSakService.Steg4,
    >(),
    Service.MedRedis {
    override val logger = logger()
    override val sikkerLogger = sikkerLogger()

    override val eventName = EventName.MANUELL_OPPRETT_SAK_REQUESTED
    override val startKeys =
        setOf(
            Key.FORESPOERSEL_ID,
        )
    override val dataKeys =
        setOf(
            Key.FORESPOERSEL_SVAR,
            Key.PERSONER,
            Key.SAK_ID,
            Key.PERSISTERT_SAK_ID,
        )

    data class Steg0(
        val transaksjonId: UUID,
        val forespoerselId: UUID,
    )

    data class Steg1(
        val forespoersel: Forespoersel,
    )

    data class Steg2(
        val personer: Map<Fnr, Person>,
    )

    data class Steg3(
        val sakId: String,
    )

    data class Steg4(
        val persistertSakId: String,
    )

    override fun lesSteg0(melding: Map<Key, JsonElement>): Steg0 =
        Steg0(
            transaksjonId = Key.UUID.les(UuidSerializer, melding),
            forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding),
        )

    override fun lesSteg1(melding: Map<Key, JsonElement>): Steg1 =
        Steg1(
            forespoersel = Key.FORESPOERSEL_SVAR.les(Forespoersel.serializer(), melding),
        )

    override fun lesSteg2(melding: Map<Key, JsonElement>): Steg2 =
        Steg2(
            personer = Key.PERSONER.les(personMapSerializer, melding),
        )

    override fun lesSteg3(melding: Map<Key, JsonElement>): Steg3 =
        Steg3(
            sakId = Key.SAK_ID.les(String.serializer(), melding),
        )

    override fun lesSteg4(melding: Map<Key, JsonElement>): Steg4 =
        Steg4(
            persistertSakId = Key.PERSISTERT_SAK_ID.les(String.serializer(), melding),
        )

    override fun utfoerSteg0(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
    ) {
        rapid.publish(
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to BehovType.HENT_TRENGER_IM.toJson(),
            Key.UUID to steg0.transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
                ).toJson(),
        )
    }

    override fun utfoerSteg1(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
    ) {
        rapid.publish(
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to BehovType.HENT_PERSONER.toJson(),
            Key.UUID to steg0.transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
                    Key.FNR_LISTE to setOf(steg1.forespoersel.fnr).toJson(String.serializer()),
                ).toJson(),
        )
    }

    override fun utfoerSteg2(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
        steg2: Steg2,
    ) {
        val sykmeldt =
            steg2.personer[steg1.forespoersel.fnr.let(::Fnr)]
                ?.let {
                    PersonDato(
                        it.navn,
                        it.foedselsdato,
                        it.fnr.verdi,
                    )
                }.orDefault(PersonDato("Ukjent person", null, steg1.forespoersel.fnr))

        rapid.publish(
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to BehovType.OPPRETT_SAK.toJson(),
            Key.UUID to steg0.transaksjonId.toJson(),
            Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
            Key.ORGNRUNDERENHET to steg1.forespoersel.orgnr.toJson(),
            Key.ARBEIDSTAKER_INFORMASJON to sykmeldt.toJson(PersonDato.serializer()),
        )
    }

    override fun utfoerSteg3(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
        steg2: Steg2,
        steg3: Steg3,
    ) {
        rapid.publish(
            Key.EVENT_NAME to eventName.toJson(),
            Key.UUID to steg0.transaksjonId.toJson(),
            Key.BEHOV to BehovType.PERSISTER_SAK_ID.toJson(),
            Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
            Key.SAK_ID to steg3.sakId.toJson(),
        )

        if (steg1.forespoersel.erBesvart) {
            rapid.publish(
                Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
                Key.UUID to steg0.transaksjonId.toJson(),
                Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
                Key.SAK_ID to steg3.sakId.toJson(),
            )
        }
    }

    override fun utfoerSteg4(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
        steg2: Steg2,
        steg3: Steg3,
        steg4: Steg4,
    ) {
        rapid.publish(
            Key.EVENT_NAME to EventName.SAK_OPPRETTET.toJson(),
            Key.UUID to steg0.transaksjonId.toJson(),
            Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
            Key.SAK_ID to steg4.persistertSakId.toJson(),
        )
    }

    override fun onError(
        melding: Map<Key, JsonElement>,
        fail: Fail,
    ) {
        sikkerLogger.error("Mottok feil:\n$fail")
    }

    override fun Steg0.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@ManuellOpprettSakService),
            Log.event(eventName),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId),
        )
}
