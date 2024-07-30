package no.nav.helsearbeidsgiver.inntektsmelding.api

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainExactly
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Innsending
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.test.mock.gyldigInnsendingRequest
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.inntektsmelding.api.innsending.InnsendingProducer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

class InnsendingProducerTest :
    FunSpec({
        val testRapid = TestRapid()
        val producer = InnsendingProducer(testRapid)

        test("publiserer melding på forventet format") {
            val transaksjonId = UUID.randomUUID()
            val forespoerselId = UUID.randomUUID()
            val avsenderFnr = Fnr.genererGyldig()

            producer.publish(transaksjonId, forespoerselId, gyldigInnsendingRequest, avsenderFnr)
            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to EventName.INSENDING_STARTED.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    Key.DATA to "".toJson(),
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    Key.ORGNRUNDERENHET to gyldigInnsendingRequest.orgnrUnderenhet.toJson(),
                    Key.IDENTITETSNUMMER to gyldigInnsendingRequest.identitetsnummer.toJson(),
                    Key.ARBEIDSGIVER_ID to avsenderFnr.toJson(),
                    Key.SKJEMA_INNTEKTSMELDING to gyldigInnsendingRequest.toJson(Innsending.serializer()),
                )
        }
    })
