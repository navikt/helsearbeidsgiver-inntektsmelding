package no.nav.helsearbeidsgiver.inntektsmelding.api.inntektselvbestemt

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
import no.nav.helsearbeidsgiver.utils.test.date.april
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

class InntektSelvbestemtProducerTest :
    FunSpec({
        val testRapid = TestRapid()
        val producer = InntektSelvbestemtProducer(testRapid)

        test("publiserer melding på forventet format") {
            val kontekstId = UUID.randomUUID()
            val sykmeldtFnr = Fnr.genererGyldig()
            val orgnr = Orgnr.genererGyldig()
            val inntektsdato = 12.april

            producer.publish(kontekstId, InntektSelvbestemtRequest(sykmeldtFnr, orgnr, inntektsdato))

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to EventName.INNTEKT_SELVBESTEMT_REQUESTED.toJson(),
                    Key.KONTEKST_ID to kontekstId.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.FNR to sykmeldtFnr.toJson(),
                            Key.ORGNR_UNDERENHET to orgnr.toJson(),
                            Key.INNTEKTSDATO to inntektsdato.toJson(),
                        ).toJson(),
                )
        }
    })
