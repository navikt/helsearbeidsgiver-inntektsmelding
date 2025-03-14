package no.nav.helsearbeidsgiver.inntektsmelding.api.hentforespoersel

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
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

class HentForespoerselProducerTest :
    FunSpec({
        val testRapid = TestRapid()
        val producer = HentForespoerselProducer(testRapid)

        test("publiserer melding på forventet format") {
            val kontekstId = UUID.randomUUID()
            val forespoerselId = UUID.randomUUID()
            val avsenderFnr = Fnr.genererGyldig()

            producer.publish(kontekstId, HentForespoerselRequest(forespoerselId), avsenderFnr)

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(),
                    Key.KONTEKST_ID to kontekstId.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                            Key.ARBEIDSGIVER_FNR to avsenderFnr.toJson(),
                        ).toJson(),
                )
        }
    })
