package no.nav.hag.simba.utils.rr.service

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Fail
import no.nav.hag.simba.utils.felles.domene.Person
import no.nav.hag.simba.utils.felles.json.les
import no.nav.hag.simba.utils.felles.json.orgMapSerializer
import no.nav.hag.simba.utils.felles.json.personMapSerializer
import no.nav.hag.simba.utils.valkey.RedisStore
import no.nav.helsearbeidsgiver.utils.json.serializer.set
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr

class MockServiceMedRedis(
    override val redisStore: RedisStore,
) : MockService(),
    Service.MedRedis

open class MockService : ServiceMed1Steg<MockService.Steg0, MockService.Steg1>() {
    override val logger = logger()
    override val sikkerLogger = sikkerLogger()

    override val eventName = EventName.TILGANG_ORG_REQUESTED

    fun mockSteg0Data(): Map<Key, JsonElement> =
        setOf(
            Key.FNR_LISTE,
            Key.ER_DUPLIKAT_IM,
        ).mockValues()

    fun mockSteg1Data(): Map<Key, JsonElement> =
        setOf(
            Key.PERSONER,
            Key.VIRKSOMHETER,
        ).mockValues()

    private fun Set<Key>.mockValues(): Map<Key, JsonElement> = associateWith { "mock $it".toJson() }

    data class Steg0(
        val fnrListe: Set<Fnr>,
        val erDuplikatIm: Boolean,
    )

    data class Steg1(
        val personer: Map<Fnr, Person>,
        val orgNavn: Map<Orgnr, String>,
    )

    override fun lesSteg0(melding: Map<Key, JsonElement>): Steg0 =
        Steg0(
            Key.FNR_LISTE.les(Fnr.serializer().set(), melding),
            Key.ER_DUPLIKAT_IM.les(Boolean.serializer(), melding),
        )

    override fun lesSteg1(melding: Map<Key, JsonElement>): Steg1 =
        Steg1(
            Key.PERSONER.les(personMapSerializer, melding),
            Key.VIRKSOMHETER.les(orgMapSerializer, melding),
        )

    override fun utfoerSteg1(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
    ) {
    }

    override fun utfoerSteg0(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
    ) {}

    override fun onError(
        melding: Map<Key, JsonElement>,
        fail: Fail,
    ) {
    }

    override fun Steg0.loggfelt(): Map<String, String> = emptyMap()
}
