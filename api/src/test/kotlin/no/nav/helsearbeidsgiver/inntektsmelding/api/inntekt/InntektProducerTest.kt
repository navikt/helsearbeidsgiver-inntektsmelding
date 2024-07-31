package no.nav.helsearbeidsgiver.inntektsmelding.api.inntekt

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainExactly
import no.nav.helse.rapids_rivers.testsupport.TestRapid
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
            val transaksjonId = UUID.randomUUID()
            val request = InntektRequest(UUID.randomUUID(), 18.januar)

            inntektProducer.publish(transaksjonId, request)

            val publisert = testRapid.firstMessage().toMap()

            publisert shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to EventName.INNTEKT_REQUESTED.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    Key.DATA to "".toJson(),
                    Key.FORESPOERSEL_ID to request.forespoerselId.toJson(),
                    Key.SKJAERINGSTIDSPUNKT to request.skjaeringstidspunkt.toJson(),
                    Key.INNTEKTSDATO to request.skjaeringstidspunkt.toJson(),
                )
        }
    })
