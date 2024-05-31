package no.nav.helsearbeidsgiver.inntektsmelding.api.inntektselvbestemt

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.mockk.every
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.april
import no.nav.helsearbeidsgiver.utils.test.mock.mockStatic
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

class InntektSelvbestemtProducerTest : FunSpec({
    val testRapid = TestRapid()
    val producer = InntektSelvbestemtProducer(testRapid)

    test("publiserer melding på forventet format") {
        val clientId = UUID.randomUUID()
        val sykmeldtFnr = Fnr.genererGyldig()
        val orgnr = Orgnr.genererGyldig()
        val inntektsdato = 12.april

        mockStatic(::randomUuid) {
            every { randomUuid() } returns clientId

            producer.publish(InntektSelvbestemtRequest(sykmeldtFnr, orgnr, inntektsdato))
        }

        testRapid.inspektør.size shouldBeExactly 1
        testRapid.firstMessage().toMap() shouldContainExactly mapOf(
            Key.EVENT_NAME to EventName.INNTEKT_SELVBESTEMT_REQUESTED.toJson(),
            Key.CLIENT_ID to clientId.toJson(),
            Key.FNR to sykmeldtFnr.toJson(Fnr.serializer()),
            Key.ORGNRUNDERENHET to orgnr.toJson(Orgnr.serializer()),
            Key.SKJAERINGSTIDSPUNKT to inntektsdato.toJson()
        )
    }
})
