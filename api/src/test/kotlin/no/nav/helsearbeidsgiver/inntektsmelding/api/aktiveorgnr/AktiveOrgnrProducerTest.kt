package no.nav.helsearbeidsgiver.inntektsmelding.api.aktiveorgnr

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

class AktiveOrgnrProducerTest : FunSpec({
    val testRapid = TestRapid()
    val producer = AktiveOrgnrProducer(testRapid)

    test("publiserer melding på forventet format") {
        val clientId = UUID.randomUUID()
        val arbeidsgiverFnr = Fnr.genererGyldig().verdi
        val arbeidstagerFnr = Fnr.genererGyldig().verdi

        producer.publish(clientId, arbeidsgiverFnr, arbeidstagerFnr)

        testRapid.inspektør.size shouldBeExactly 1
        testRapid.firstMessage().toMap() shouldContainExactly mapOf(
            Key.EVENT_NAME to EventName.AKTIVE_ORGNR_REQUESTED.toJson(),
            Key.CLIENT_ID to clientId.toJson(),
            Key.ARBEIDSGIVER_FNR to arbeidsgiverFnr.toJson(),
            Key.FNR to arbeidstagerFnr.toJson()
        )
    }
})
