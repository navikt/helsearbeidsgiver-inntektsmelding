package no.nav.helsearbeidsgiver.inntektsmelding.api.hentselvbestemtim

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainExactly
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class HentSelvbestemtImProducerTest :
    FunSpec({

        val testRapid = TestRapid()
        val producer = HentSelvbestemtImProducer(testRapid)

        test("publiserer melding på forventet format") {
            val transaksjonId = UUID.randomUUID()
            val selvbestemtId = UUID.randomUUID()

            producer.publish(transaksjonId, selvbestemtId)

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to EventName.SELVBESTEMT_IM_REQUESTED.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.SELVBESTEMT_ID to selvbestemtId.toJson(),
                        ).toJson(),
                )
        }
    })
