package no.nav.helsearbeidsgiver.inntektsmelding.api.kvittering

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
import java.util.UUID

class KvitteringProducerTest :
    FunSpec({
        val testRapid = TestRapid()
        val producer = KvitteringProducer(testRapid)

        test("publiserer melding på forventet format") {
            val transaksjonId = UUID.randomUUID()
            val forespoerselId = UUID.randomUUID()

            producer.publish(transaksjonId, forespoerselId)

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to EventName.KVITTERING_REQUESTED.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    Key.DATA to "".toJson(),
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                )
        }
    })
