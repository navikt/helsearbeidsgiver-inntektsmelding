import Jackson.toJsonNode
import com.fasterxml.jackson.databind.JsonNode
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.Ansettelsesperiode
import no.nav.helsearbeidsgiver.felles.Arbeidsforhold
import no.nav.helsearbeidsgiver.felles.ArbeidsforholdLøsning
import no.nav.helsearbeidsgiver.felles.Arbeidsgiver
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.ForespoerselType
import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.NavnLøsning
import no.nav.helsearbeidsgiver.felles.PeriodeNullable
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.TrengerInntekt
import no.nav.helsearbeidsgiver.felles.VirksomhetLøsning
import no.nav.helsearbeidsgiver.felles.app.LocalApp
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.PriProducer
import no.nav.helsearbeidsgiver.felles.rapidsrivers.toPretty
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespurtData
import no.nav.helsearbeidsgiver.felles.til
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.TrengerForespoerselLøser
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.TrengerForespoersel
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.test.date.januar
import java.time.LocalDate
import java.time.LocalDateTime

val logger = "helsearbeidsgiver-im-integrasjon-local".logger()

fun main() {
    val env = LocalApp().setupEnvironment("im-helsebro", 8083)

    val rapid = RapidApplication.create(env)
    // Dummyløser lar deg teste flyten, så du slipper å mocke hvert enkelt endepunkt
    DummyLøser(rapid, BehovType.HENT_TRENGER_IM)
    // enten::
//    DummyLøser(rapid, BehovType.PREUTFYLL, listOf(BehovType.FULLT_NAVN))
    // eller: start opp faktisk løser med LocalPreutfyltApp og lag dummy av resten:
    // HentPreutfyltLøser(rapid)
//    DummyLøser(rapid, BehovType.INNTEKT)
//    DummyLøser(rapid, BehovType.ARBEIDSFORHOLD)
//    DummyLøser(rapid, BehovType.VIRKSOMHET)
    DummyLøser(rapid, BehovType.FULLT_NAVN)
    // Hvis ønskelig kan man kjøre opp "ekte" løsere med eller uten mocking parallellt, sammen med DummyLøser:
    val priProducer = mockk<PriProducer<TrengerForespoersel>>()
    coEvery { priProducer.send(any()) } returns true
    TrengerForespoerselLøser(rapid, priProducer)

    rapid.start()
}

class DummyLøser(
    rapidsConnection: RapidsConnection,
    private val behov: BehovType,
    private val nesteBehov: List<BehovType> = emptyList()
) : River.PacketListener {
    private val rapid = rapidsConnection

    init {
        logger.info("Starter dummyløser for Behov $behov")
        River(rapidsConnection).apply {
            validate { msg ->
                msg.demandValues(Key.BEHOV to behov.name)
                msg.rejectKey(Key.LØSNING.str)
                msg.interestedIn(Key.FORESPOERSEL_ID.str, Key.INITIATE_ID.str, Key.BOOMERANG.str)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("Fikk pakke:\n${packet.toPretty()}")
        packet.setLøsning(behov, getLøsning())
        packet.nesteBehov(nesteBehov)
        JsonMessage.newMessage()
        logger.info("Publiserer løsning:\n${packet.toPretty()}")
        this.rapid.publish(packet.toJson())
    }

    private fun getLøsning(): JsonNode {
        val fnr = "123"
        val orgnr = "123"

        return when (behov) {
            BehovType.HENT_TRENGER_IM -> {
                TrengerInntekt(
                    type = ForespoerselType.KOMPLETT,
                    orgnr = orgnr,
                    fnr = fnr,
                    skjaeringstidspunkt = 11.januar(2018),
                    sykmeldingsperioder = listOf(2.januar til 3.januar),
                    egenmeldingsperioder = listOf(1.januar til 1.januar),
                    forespurtData = mockForespurtData(),
                    erBesvart = false
                ).toJsonNode()
            }
            BehovType.VIRKSOMHET -> {
                VirksomhetLøsning("Din Bedrift A/S").toJsonNode()
            }
            BehovType.FULLT_NAVN -> {
                NavnLøsning(PersonDato("Navn navnesen", LocalDate.now())).toJsonNode()
            }
            BehovType.INNTEKT -> {
                Inntekt(emptyList()).toJsonNode()
            }
            BehovType.ARBEIDSFORHOLD -> {
                ArbeidsforholdLøsning(
                    listOf(
                        Arbeidsforhold(
                            Arbeidsgiver("A/S", orgnr),
                            Ansettelsesperiode(PeriodeNullable(1.januar, 31.januar)),
                            LocalDateTime.now()
                        )
                    )
                ).toJsonNode()
            }
            else -> {
                error("Ukjent behov, ingen dummy-løsning!")
            }
        }
    }
}

private fun JsonMessage.setLøsning(nøkkel: BehovType, data: JsonNode) {
    this[Key.LØSNING.str] = mapOf(
        nøkkel.name to data
    )
}
private fun JsonMessage.nesteBehov(behov: List<BehovType>) {
    if (behov.isEmpty()) {
        return
    }
    this[Key.NESTE_BEHOV.str] = behov
}

private object Jackson {
    val objectMapper = customObjectMapper()

    fun <T : Any> T.toJsonNode(): JsonNode =
        objectMapper.valueToTree(this)
}
