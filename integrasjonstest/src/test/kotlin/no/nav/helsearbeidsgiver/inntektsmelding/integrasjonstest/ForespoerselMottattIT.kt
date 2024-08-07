package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ForespoerselMottattIT : EndToEndTest() {
    @Test
    fun `skal ta imot forespørsel ny inntektsmelding, deretter opprette sak og oppgave`() {
        publish(
            Pri.Key.NOTIS to Pri.NotisType.FORESPØRSEL_MOTTATT.toJson(Pri.NotisType.serializer()),
            Pri.Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
            Pri.Key.ORGNR to Mock.orgnr.toJson(),
            Pri.Key.FNR to Mock.fnr.toJson(),
        )

        messages
            .filter(EventName.FORESPØRSEL_MOTTATT)
            .filter(BehovType.LAGRE_FORESPOERSEL)
            .firstAsMap()
            .also {
                Key.UUID.lesOrNull(UuidSerializer, it).shouldNotBeNull()

                Key.EVENT_NAME.lesOrNull(EventName.serializer(), it) shouldBe EventName.FORESPØRSEL_MOTTATT
                Key.BEHOV.lesOrNull(BehovType.serializer(), it) shouldBe BehovType.LAGRE_FORESPOERSEL
                Key.FORESPOERSEL_ID.lesOrNull(UuidSerializer, it) shouldBe Mock.forespoerselId
                Key.ORGNRUNDERENHET.lesOrNull(Orgnr.serializer(), it) shouldBe Mock.orgnr
                Key.IDENTITETSNUMMER.lesOrNull(Fnr.serializer(), it) shouldBe Mock.fnr
            }

        messages
            .filter(EventName.FORESPØRSEL_LAGRET)
            .firstAsMap()
            .also {
                it shouldNotContainKey Key.BEHOV

                Key.EVENT_NAME.lesOrNull(EventName.serializer(), it) shouldBe EventName.FORESPØRSEL_LAGRET
                Key.FORESPOERSEL_ID.lesOrNull(UuidSerializer, it) shouldBe Mock.forespoerselId
                Key.ORGNRUNDERENHET.lesOrNull(Orgnr.serializer(), it) shouldBe Mock.orgnr
                Key.IDENTITETSNUMMER.lesOrNull(Fnr.serializer(), it) shouldBe Mock.fnr
            }
    }

    private object Mock {
        val forespoerselId: UUID = UUID.randomUUID()
        val orgnr = Orgnr.genererGyldig()
        val fnr = Fnr.genererGyldig()
    }
}
