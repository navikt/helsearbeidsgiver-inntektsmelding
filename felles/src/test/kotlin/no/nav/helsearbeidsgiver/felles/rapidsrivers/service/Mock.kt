package no.nav.helsearbeidsgiver.felles.rapidsrivers.service

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Person
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.personMapSerializer
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.utils.json.serializer.set
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

class MockServiceMedRedis(
    override val redisStore: RedisStore,
) : MockService(),
    Service.MedRedis

open class MockService : ServiceMed1Steg<MockService.Steg0, MockService.Steg1>() {
    override val logger = logger()
    override val sikkerLogger = sikkerLogger()

    override val eventName = EventName.MANUELL_OPPRETT_SAK_REQUESTED
    override val startKeys =
        setOf(
            Key.FNR_LISTE,
            Key.ER_DUPLIKAT_IM,
        )
    override val dataKeys =
        setOf(
            Key.PERSONER,
            Key.VIRKSOMHETER,
        )

    data class Steg0(
        val fnrListe: Set<Fnr>,
        val erDuplikatIm: Boolean,
    )

    data class Steg1(
        val personer: Map<Fnr, Person>,
        val orgNavn: Map<String, String>,
    )

    override fun lesSteg0(melding: Map<Key, JsonElement>): Steg0 =
        Steg0(
            Key.FNR_LISTE.les(Fnr.serializer().set(), melding),
            Key.ER_DUPLIKAT_IM.les(Boolean.serializer(), melding),
        )

    override fun lesSteg1(melding: Map<Key, JsonElement>): Steg1 =
        Steg1(
            Key.PERSONER.les(personMapSerializer, melding),
            Key.VIRKSOMHETER.les(MapSerializer(String.serializer(), String.serializer()), melding),
        )

    override fun utfoerSteg1(
        steg0: Steg0,
        steg1: Steg1,
    ) {}

    override fun utfoerSteg0(steg0: Steg0) {}

    override fun onError(
        melding: Map<Key, JsonElement>,
        fail: Fail,
    ) {}

    override fun Steg0.loggfelt(): Map<String, String> = emptyMap()
}

object Mock {
    val fail =
        Fail(
            feilmelding = "Noen har blandet ut flybensinen med Red Bull.",
            event = EventName.INNTEKTSMELDING_MOTTATT,
            transaksjonId = UUID.randomUUID(),
            forespoerselId = UUID.randomUUID(),
            utloesendeMelding = JsonNull,
        )

    fun values(keys: Set<Key>): Map<Key, JsonElement> = keys.associateWith { "mock $it".toJson() }
}
