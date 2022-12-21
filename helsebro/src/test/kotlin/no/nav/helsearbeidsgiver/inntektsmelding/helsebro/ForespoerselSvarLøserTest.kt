package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.test.json.toJson
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.pritopic.sendJson
import org.junit.jupiter.api.Assertions

class ForespoerselSvarLøserTest : FunSpec({

    val loggerSikkerCollector = ListAppender<ILoggingEvent>().also {
        (loggerSikker as Logger).addAppender(it)
        it.start()
    }

    val testRapid = TestRapid()

    ForespoerselSvarLøser(testRapid)

    test("Ved løsning på behov så tolkes og logges løsningen") {
        val expectedIncoming = mockForespoerselSvar()

        testRapid.sendJson(
            Pri.Key.LØSNING to Pri.BehovType.TRENGER_FORESPØRSEL.toJson(),
            Pri.Key.ORGNR to expectedIncoming.orgnr.toJson(),
            Pri.Key.FNR to expectedIncoming.fnr.toJson(),
            Pri.Key.VEDTAKSPERIODE_ID to expectedIncoming.vedtaksperiodeId.toJson(),
            Pri.Key.FOM to expectedIncoming.fom.toJson(),
            Pri.Key.TOM to expectedIncoming.tom.toJson(),
            Pri.Key.FORESPURT_DATA to expectedIncoming.forespurtData.let(Json::encodeToJsonElement)
        )

        Assertions.assertEquals(2, loggerSikkerCollector.list.size)
        loggerSikkerCollector.list.single {
            it.message.contains("Oversatte melding:\n$expectedIncoming")
        }
    }
})
