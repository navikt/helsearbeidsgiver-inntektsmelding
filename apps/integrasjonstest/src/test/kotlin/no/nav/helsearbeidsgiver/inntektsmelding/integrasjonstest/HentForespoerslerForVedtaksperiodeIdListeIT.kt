package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.builtins.MapSerializer
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.ResultJson
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.hag.simba.utils.kontrakt.domene.forespoersel.Forespoersel
import no.nav.hag.simba.utils.valkey.RedisPrefix
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
        val kontekstId: UUID = UUID.randomUUID()
        val vedtaksperiodeId1 = UUID.randomUUID()
        val vedtaksperiodeId2 = UUID.randomUUID()

        val forespoerselListeSvar = mockForespoerselListeSvarResultat(vedtaksperiodeId1, vedtaksperiodeId2)

        val forventetedeForespoersler = forespoerselListeSvar.associate { it.forespoerselId to it.toForespoersel() }

        mockForespoerselSvarFraHelsebro(
            forespoerselListeSvar = forespoerselListeSvar,
        )

        publish(
            Key.EVENT_NAME to EventName.FORESPOERSLER_REQUESTED.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(UuidSerializer),
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
                it[Key.KONTEKST_ID]?.fromJson(UuidSerializer) shouldBe kontekstId
            }

        // Forespørsler hentet
        messages
            .filter(EventName.FORESPOERSLER_REQUESTED)
            .filter(Key.FORESPOERSEL_MAP)
            .firstAsMap()
            .let {
                // Verifiser kontekst-ID
                it[Key.KONTEKST_ID]?.fromJson(UuidSerializer) shouldBe kontekstId

                // Verifiser forespoersler
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                data[Key.FORESPOERSEL_MAP]?.fromJson(MapSerializer(UuidSerializer, Forespoersel.serializer())) shouldBe forventetedeForespoersler
            }

        // API besvart gjennom redis
        val resultJson =
            redisConnection
                .get(RedisPrefix.HentForespoerslerForVedtaksperiodeIdListe, kontekstId)
                ?.fromJson(ResultJson.serializer())
                .shouldNotBeNull()

        resultJson.failure.shouldBeNull()

        val hentForespoerslerResultat = resultJson.success.shouldNotBeNull().fromJson(MapSerializer(UuidSerializer, Forespoersel.serializer()))

        hentForespoerslerResultat shouldBe forventetedeForespoersler
    }
}
