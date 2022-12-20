package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.test.date.januar
import no.nav.helsearbeidsgiver.felles.test.json.tryToJson
import org.junit.jupiter.api.Assertions
import java.util.UUID

class ForespørselSvarLøserTest : FunSpec({

    val loggerSikkerCollector = ListAppender<ILoggingEvent>().also {
        (loggerSikker as Logger).addAppender(it)
        it.start()
    }

    val testRapid = TestRapid()

    ForespørselSvarLøser(testRapid)

    test("Løser mottar melding om mottatt forespørsel") {

        val expectedForespørselSvar = ForespørselSvar(
            orgnr = "123",
            fnr = "abc",
            vedtaksperiodeId = UUID.randomUUID(),
            fom = 1.januar,
            tom = 16.januar,
            forespurtData = mockForespurtDataListe()
        )

        testRapid.sendJson(
            "eventType" to "FORESPØRSEL_SVAR".tryToJson(),
            "orgnr" to expectedForespørselSvar.orgnr.tryToJson(),
            "fnr" to expectedForespørselSvar.fnr.tryToJson(),
            "vedtaksperiodeId" to expectedForespørselSvar.vedtaksperiodeId.toString().tryToJson(),
            "fom" to expectedForespørselSvar.fom.toString().tryToJson(),
            "tom" to expectedForespørselSvar.tom.toString().tryToJson(),
            "forespurtData" to expectedForespørselSvar.forespurtData.let(Json::encodeToJsonElement)
        )

        Assertions.assertEquals(2, loggerSikkerCollector.list.size)
        loggerSikkerCollector.list.single {
            it.message.contains("Oversatte melding:\n$expectedForespørselSvar")
        }
    }
})
