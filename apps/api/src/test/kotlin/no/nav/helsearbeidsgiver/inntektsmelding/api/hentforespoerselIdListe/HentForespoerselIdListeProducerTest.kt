package no.nav.helsearbeidsgiver.inntektsmelding.api.hentforespoerselIdListe

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainExactly
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class HentForespoerselIdListeProducerTest :
    FunSpec({
        val testRapid = TestRapid()
        val producer = HentForespoerslerProducer(testRapid)

        test("publiserer melding på forventet format") {
            val kontekstId = UUID.randomUUID()
            val vedtaksperiodeIdListe = listOf(UUID.randomUUID(), UUID.randomUUID())

            producer.publish(kontekstId, HentForespoerslerRequest(vedtaksperiodeIdListe))

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to EventName.FORESPOERSLER_REQUESTED.toJson(),
                    Key.KONTEKST_ID to kontekstId.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.VEDTAKSPERIODE_ID_LISTE to vedtaksperiodeIdListe.toJson(UuidSerializer),
                        ).toJson(),
                )
        }
    })
