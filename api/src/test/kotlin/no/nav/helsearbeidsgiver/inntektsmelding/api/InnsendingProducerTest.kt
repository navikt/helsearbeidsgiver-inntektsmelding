package no.nav.helsearbeidsgiver.inntektsmelding.api

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.test.mock.mockSkjemaInntektsmelding
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.shouldContainAllExcludingTempKey
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
            val avsenderFnr = Fnr.genererGyldig()
            val skjema = mockSkjemaInntektsmelding()

            producer.publish(transaksjonId, skjema, avsenderFnr)

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.firstMessage().toMap() shouldContainAllExcludingTempKey
                mapOf(
                    Key.EVENT_NAME to EventName.INSENDING_STARTED.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.ARBEIDSGIVER_FNR to avsenderFnr.toJson(),
                            Key.SKJEMA_INNTEKTSMELDING to skjema.toJson(SkjemaInntektsmelding.serializer()),
                        ).toJson(),
                )
        }
    })
