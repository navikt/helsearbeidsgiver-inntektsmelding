package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.HttpStatusCode
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.felles.NotifikasjonLøsning
import no.nav.helsearbeidsgiver.felles.NotisType

abstract class RapidMock {

    private val rapid = TestRapid()
    internal val objectMapper: ObjectMapper = jacksonObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .registerModule(JavaTimeModule())
    private lateinit var løser: NotifikasjonLøser
    private lateinit var klient: ArbeidsgiverNotifikasjonKlient

    fun sendMessage(behovType: NotisType, packet: Map<String, Any>, response: String, status: HttpStatusCode): NotifikasjonLøsning {
        klient = buildClient(response, status)
        løser = NotifikasjonLøser(rapid, klient, "")
        rapid.reset()
        rapid.sendTestMessage(
            objectMapper.writeValueAsString(
                packet
            )
        )
        val losning: JsonNode = rapid.inspektør.message(0).path("@løsning")
        return objectMapper.readValue(losning.get(behovType.name).toString())
    }
}
