package no.nav.helsearbeidsgiver.inntektsmelding.api.hentforespoerselIder

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainExactly
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.json.vedtaksperiodeListeSerializer
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.inntektsmelding.api.hentforespoerselider.HentForespoerselIderProducer
import no.nav.helsearbeidsgiver.inntektsmelding.api.hentforespoerselider.HentForespoerselIderRequest
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

class HentForespoerselIderProducerTest :
    FunSpec({
        val testRapid = TestRapid()
        val producer = HentForespoerselIderProducer(testRapid)

        test("publiserer melding på forventet format") {
            val transaksjonId = UUID.randomUUID()
            val orgnr = Orgnr.genererGyldig()
            val vedtaksperiodeIder = listOf(UUID.randomUUID(), UUID.randomUUID())

            producer.publish(transaksjonId, HentForespoerselIderRequest(orgnr, vedtaksperiodeIder))

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.firstMessage().toMap() shouldContainExactly
                mapOf(
                    Key.EVENT_NAME to EventName.FORESPOERSEL_IDER_REQUESTED.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.VEDTAKSPERIODE_ID_LISTE to vedtaksperiodeIder.toJson(vedtaksperiodeListeSerializer),
                        ).toJson(),
                )
        }
    })
