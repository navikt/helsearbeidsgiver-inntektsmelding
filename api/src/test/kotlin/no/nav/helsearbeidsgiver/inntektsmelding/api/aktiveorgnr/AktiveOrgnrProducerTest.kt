package no.nav.helsearbeidsgiver.inntektsmelding.api.aktiveorgnr

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

class AktiveOrgnrProducerTest :
    FunSpec({
        val testRapid = TestRapid()
        val producer = AktiveOrgnrProducer(testRapid)

        test("publiserer melding på forventet format") {
            val transaksjonId = UUID.randomUUID()
            val arbeidsgiverFnr = Fnr.genererGyldig()
            val arbeidstagerFnr = Fnr.genererGyldig()

            producer.publish(transaksjonId, arbeidsgiverFnr, arbeidstagerFnr)

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to EventName.AKTIVE_ORGNR_REQUESTED.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.FNR to arbeidstagerFnr.toJson(),
                            Key.ARBEIDSGIVER_FNR to arbeidsgiverFnr.toJson(),
                        ).toJson(),
                )
        }
    })
