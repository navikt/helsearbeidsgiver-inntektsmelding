import io.mockk.coEvery
import io.mockk.mockkClass
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
import no.nav.helsearbeidsgiver.felles.HentTrengerImLøsning
import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.InntektLøsning
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Løsning
import no.nav.helsearbeidsgiver.felles.NavnLøsning
import no.nav.helsearbeidsgiver.felles.PeriodeNullable
import no.nav.helsearbeidsgiver.felles.PersonLink
import no.nav.helsearbeidsgiver.felles.PreutfyltLøsning
import no.nav.helsearbeidsgiver.felles.TrengerInntekt
import no.nav.helsearbeidsgiver.felles.VirksomhetLøsning
import no.nav.helsearbeidsgiver.felles.app.LocalApp
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandAll
import no.nav.helsearbeidsgiver.felles.test.date.januar
import no.nav.helsearbeidsgiver.felles.til
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.PriProducer
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.TrengerForespoerselLøser
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

val logger: Logger = LoggerFactory.getLogger("helsearbeidsgiver-im-integrasjon")

fun main() {
    val env = LocalApp().setupEnvironment("im-helsebro", 8083)

    val rapid = RapidApplication.create(env)
    // Dummyløser lar deg teste flyten, så du slipper å mocke hvert enkelt endepunkt
    DummyLøser(rapid, BehovType.HENT_TRENGER_IM)
    // enten::
    DummyLøser(rapid, BehovType.PREUTFYLL, listOf(BehovType.FULLT_NAVN))
    // eller: start opp faktisk løser med LocalPreutfyltApp og lag dummy av resten:
    // HentPreutfyltLøser(rapid)
//    DummyLøser(rapid, BehovType.INNTEKT)
//    DummyLøser(rapid, BehovType.ARBEIDSFORHOLD)
//    DummyLøser(rapid, BehovType.VIRKSOMHET)
    DummyLøser(rapid, BehovType.FULLT_NAVN)
    // Hvis ønskelig kan man kjøre opp "ekte" løsere med eller uten mocking parallellt, sammen med DummyLøser:
    val priProducer = mockkClass(PriProducer::class)
    coEvery { priProducer.send(any()) }.returns(true)
    TrengerForespoerselLøser(rapid, priProducer)

    rapid.start()
}

class DummyLøser(rapidsConnection: RapidsConnection, val behov: BehovType, val nesteBehov: List<BehovType> = emptyList()) : River.PacketListener {
    private val rapid = rapidsConnection

    init {
        logger.info("Starter dummyløser for Behov $behov")
        River(rapidsConnection).apply {
            validate { msg ->
                msg.demandAll(Key.BEHOV, listOf(behov))
                msg.rejectKey(Key.LØSNING.str)
                msg.interestedIn(Key.FORESPOERSEL_ID.str, Key.INITIATE_ID.str, Key.BOOMERANG.str)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("Fikk pakke: ${packet.toJson()}")
        packet.setLøsning(behov, getLøsning())
        packet.nesteBehov(nesteBehov)
        val test = packet[Key.INITIATE_ID.str].asText()
        JsonMessage.newMessage()
        println("test = $test")
        logger.info("Publiserer løsning: ${packet.toJson()}")
        this.rapid.publish(packet.toJson())
    }

    private fun getLøsning(): Løsning {
        val fnr = "123"
        val orgnr = "123"

        return when (behov) {
            BehovType.HENT_TRENGER_IM -> {
                HentTrengerImLøsning(
                    value = TrengerInntekt(
                        orgnr = orgnr,
                        fnr = fnr,
                        sykmeldingsperioder = listOf(1.januar til 2.januar),
                        forespurtData = emptyList()
                    )
                )
            }
            BehovType.PREUTFYLL -> {
                PreutfyltLøsning(
                    PersonLink(fnr, orgnr)
                )
            }
            BehovType.VIRKSOMHET -> {
                VirksomhetLøsning("Din Bedrift A/S")
            }
            BehovType.FULLT_NAVN -> {
                NavnLøsning("Navn navnesen")
            }
            BehovType.INNTEKT -> {
                InntektLøsning(Inntekt(emptyList()))
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
                )
            }
            else -> {
                NavnLøsning(error("Ukjent behov, ingen dummy-løsning!"))
            }
        }
    }
}

private fun JsonMessage.setLøsning(nøkkel: BehovType, data: Any) {
    this[Key.LØSNING.str] = mapOf(
        nøkkel.name to data
    )
}
private fun JsonMessage.nesteBehov(behov: List<BehovType>) {
    if (behov.isNullOrEmpty()) {
        return
    }
    this[Key.NESTE_BEHOV.str] = behov
}
