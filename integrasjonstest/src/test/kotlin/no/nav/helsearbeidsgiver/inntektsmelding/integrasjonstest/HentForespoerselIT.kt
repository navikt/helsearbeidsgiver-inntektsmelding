package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.HentForespoerselResultat
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.mock.mockForespoerselSvarSuksess
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HentForespoerselIT : EndToEndTest() {
    @Test
    fun `forespørsel hentes`() {
        val transaksjonId: UUID = UUID.randomUUID()
        val forespoerselId: UUID = UUID.randomUUID()

        mockForespoerselSvarFraHelsebro(
            forespoerselId = forespoerselId,
            forespoerselSvar = mockForespoerselSvarSuksess().copy(forespoerselId = forespoerselId),
        )

        publish(
            Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(),
            Key.KONTEKST_ID to transaksjonId.toJson(UuidSerializer),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(UuidSerializer),
                    Key.ARBEIDSGIVER_FNR to Fnr.genererGyldig().toJson(),
                ).toJson(),
        )

        messages
            .filter(EventName.TRENGER_REQUESTED)
            .filter(BehovType.HENT_TRENGER_IM)
            .firstAsMap()
            .let {
                it[Key.KONTEKST_ID]?.fromJson(UuidSerializer) shouldBe transaksjonId
            }

        messages
            .filter(EventName.TRENGER_REQUESTED)
            .filter(BehovType.HENT_VIRKSOMHET_NAVN)
            .firstAsMap()
            .let {
                it[Key.KONTEKST_ID]?.fromJson(UuidSerializer) shouldBe transaksjonId
            }

        messages
            .filter(EventName.TRENGER_REQUESTED)
            .filter(BehovType.HENT_PERSONER)
            .firstAsMap()
            .let {
                it[Key.KONTEKST_ID]?.fromJson(UuidSerializer) shouldBe transaksjonId
            }

        messages
            .filter(EventName.TRENGER_REQUESTED)
            .filter(BehovType.HENT_INNTEKT)
            .firstAsMap()
            .let {
                it[Key.KONTEKST_ID]?.fromJson(UuidSerializer) shouldBe transaksjonId
            }

        val resultJson =
            redisConnection
                .get(RedisPrefix.HentForespoersel, transaksjonId)
                ?.fromJson(ResultJson.serializer())
                .shouldNotBeNull()

        resultJson.failure.shouldBeNull()

        val hentForespoerselResultat = resultJson.success.shouldNotBeNull().fromJson(HentForespoerselResultat.serializer())

        hentForespoerselResultat.shouldNotBeNull().apply {
            sykmeldtNavn.shouldNotBeNull()
            avsenderNavn.shouldNotBeNull()
            orgNavn.shouldNotBeNull()
            inntekt.shouldNotBeNull()
            forespoersel.shouldNotBeNull()
            feil.shouldBeEmpty()
        }
    }

    @Test
    fun `dersom forespørsel ikke blir funnet så settes sak og oppgave til utgått`() {
        val transaksjonId: UUID = UUID.randomUUID()
        val forespoerselId: UUID = UUID.randomUUID()

        mockForespoerselSvarFraHelsebro(
            forespoerselId = forespoerselId,
            forespoerselSvar = null,
        )

        publish(
            Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(),
            Key.KONTEKST_ID to transaksjonId.toJson(UuidSerializer),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(UuidSerializer),
                    Key.ARBEIDSGIVER_FNR to Fnr.genererGyldig().toJson(),
                ).toJson(),
        )

        messages
            .filter(EventName.TRENGER_REQUESTED)
            .filter(BehovType.HENT_TRENGER_IM)
            .firstAsMap()
            .let {
                Key.KONTEKST_ID.lesOrNull(UuidSerializer, it) shouldBe transaksjonId
            }

        messages
            .filter(EventName.SAK_OG_OPPGAVE_UTGAATT)
            .firstAsMap()
            .let {
                Key.KONTEKST_ID.lesOrNull(UuidSerializer, it) shouldBe transaksjonId
                Key.FORESPOERSEL_ID.lesOrNull(UuidSerializer, it) shouldBe forespoerselId
            }

        val resultJson =
            redisConnection
                .get(RedisPrefix.HentForespoersel, transaksjonId)
                ?.fromJson(ResultJson.serializer())
                .shouldNotBeNull()

        resultJson.success.shouldBeNull()
        resultJson.failure.shouldNotBeNull()
    }
}
