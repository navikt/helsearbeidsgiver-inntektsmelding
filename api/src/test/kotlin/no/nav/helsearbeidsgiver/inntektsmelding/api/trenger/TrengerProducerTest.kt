package no.nav.helsearbeidsgiver.inntektsmelding.api.trenger

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainExactly
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

class TrengerProducerTest : FunSpec({
    val testRapid = TestRapid()
    val producer = TrengerProducer(testRapid)

    test("publiserer melding på forventet format") {
        val forespoerselId = UUID.randomUUID()
        val avsenderFnr = Fnr.genererGyldig().verdi

        val transaksjonId = producer.publish(HentForespoerselRequest(forespoerselId), avsenderFnr)

        testRapid.inspektør.size shouldBeExactly 1
        testRapid.firstMessage().toMap() shouldContainExactly mapOf(
            Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to "".toJson(),
            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
            Key.ARBEIDSGIVER_ID to avsenderFnr.toJson()
        )
    }
})
