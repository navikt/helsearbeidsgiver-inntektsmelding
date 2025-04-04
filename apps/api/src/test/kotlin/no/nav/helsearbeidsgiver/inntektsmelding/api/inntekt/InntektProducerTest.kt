package no.nav.helsearbeidsgiver.inntektsmelding.api.inntekt

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainExactly
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.januar
import java.util.UUID

class InntektProducerTest :
    FunSpec({
        val testRapid = TestRapid()
        val inntektProducer = InntektProducer(testRapid)

        test("Publiserer melding på forventet format") {
            val kontekstId = UUID.randomUUID()
            val request = InntektRequest(UUID.randomUUID(), 18.januar)

            inntektProducer.publish(kontekstId, request)

            val publisert = testRapid.firstMessage().toMap()

            publisert shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to EventName.INNTEKT_REQUESTED.toJson(),
                    Key.KONTEKST_ID to kontekstId.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.FORESPOERSEL_ID to request.forespoerselId.toJson(),
                            Key.INNTEKTSDATO to request.skjaeringstidspunkt.toJson(),
                        ).toJson(),
                )
        }
    })
