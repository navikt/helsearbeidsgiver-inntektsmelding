package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.builtins.MapSerializer
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.mock.mockForespoerselListeSvarResultat
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HentForespoerslerForVedtaksperiodeIdListeIT : EndToEndTest() {
    @Test
    fun `Test meldingsflyt for henting av forespørsler for liste av vedtaksperiode-IDer`() {
        val transaksjonId: UUID = UUID.randomUUID()
        val vedtaksperiodeId1 = UUID.randomUUID()
        val vedtaksperiodeId2 = UUID.randomUUID()

        val forespoerselListeSvar = mockForespoerselListeSvarResultat(vedtaksperiodeId1, vedtaksperiodeId2)

        val forventetedeForespoersler = forespoerselListeSvar.associate { it.forespoerselId to it.toForespoersel() }

        mockForespoerselSvarFraHelsebro(
            forespoerselListeSvar = forespoerselListeSvar,
        )

        publish(
            Key.EVENT_NAME to EventName.FORESPOERSLER_REQUESTED.toJson(),
            Key.UUID to transaksjonId.toJson(UuidSerializer),
            Key.DATA to
                mapOf(
                    Key.VEDTAKSPERIODE_ID_LISTE to listOf(vedtaksperiodeId1, vedtaksperiodeId2).toJson(UuidSerializer),
                ).toJson(),
        )

        // Henter forespørsler
        messages
            .filter(EventName.FORESPOERSLER_REQUESTED)
            .filter(BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE)
            .firstAsMap()
            .let {
                it[Key.UUID]?.fromJson(UuidSerializer) shouldBe transaksjonId
            }

        // Forespørsler hentet
        messages
            .filter(EventName.FORESPOERSLER_REQUESTED)
            .filter(Key.FORESPOERSLER_SVAR)
            .firstAsMap()
            .let {
                // Verifiser transaksjon-ID
                it[Key.UUID]?.fromJson(UuidSerializer) shouldBe transaksjonId

                // Verifiser forespoersler
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                data[Key.FORESPOERSLER_SVAR]?.fromJson(MapSerializer(UuidSerializer, Forespoersel.serializer())) shouldBe forventetedeForespoersler
            }

        // API besvart gjennom redis
        val resultJson =
            redisConnection
                .get(RedisPrefix.HentForespoerslerForVedtaksperiodeIdListe, transaksjonId)
                ?.fromJson(ResultJson.serializer())
                .shouldNotBeNull()

        resultJson.failure.shouldBeNull()

        val hentForespoerslerResultat = resultJson.success.shouldNotBeNull().fromJson(MapSerializer(UuidSerializer, Forespoersel.serializer()))

        hentForespoerslerResultat.shouldNotBeNull().also {
            it shouldBe forventetedeForespoersler
        }
    }
}
