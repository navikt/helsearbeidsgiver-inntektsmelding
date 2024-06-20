package no.nav.helsearbeidsgiver.inntektsmelding.api.lagreselvbestemtim

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.mockk.every
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmeldingSelvbestemt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.test.mock.mockSkjemaInntektsmeldingSelvbestemt
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.mock.mockStatic
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

class LagreSelvbestemtImProducerTest : FunSpec({

    val testRapid = TestRapid()
    val producer = LagreSelvbestemtImProducer(testRapid)

    test("publiserer melding på forventet format") {
        val clientId = UUID.randomUUID()
        val avsenderFnr = Fnr.genererGyldig().verdi
        val skjema = mockSkjemaInntektsmeldingSelvbestemt()

        mockStatic(UUID::randomUUID) {
            every { UUID.randomUUID() } returns clientId

            producer.publish(skjema, avsenderFnr)
        }

        testRapid.inspektør.size shouldBeExactly 1
        testRapid.firstMessage().toMap() shouldContainExactly mapOf(
            Key.EVENT_NAME to EventName.SELVBESTEMT_IM_MOTTATT.toJson(),
            Key.CLIENT_ID to clientId.toJson(),
            Key.SKJEMA_INNTEKTSMELDING to skjema.toJson(SkjemaInntektsmeldingSelvbestemt.serializer()),
            Key.ARBEIDSGIVER_FNR to avsenderFnr.toJson()
        )
    }
})
