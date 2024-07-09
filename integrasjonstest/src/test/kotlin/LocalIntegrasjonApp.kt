import io.mockk.mockk
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.felles.Ansettelsesperiode
import no.nav.helsearbeidsgiver.felles.Arbeidsforhold
import no.nav.helsearbeidsgiver.felles.Arbeidsgiver
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Forespoersel
import no.nav.helsearbeidsgiver.felles.ForespoerselType
import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PeriodeNullable
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.app.LocalApp
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.PriProducer
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.toPretty
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespurtData
import no.nav.helsearbeidsgiver.felles.utils.simpleName
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.TrengerForespoerselLoeser
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.test.date.januar
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

val logger = "helsearbeidsgiver-im-integrasjon-local".logger()

fun main() {
    val env = LocalApp().setupEnvironment("im-helsebro", 8083)

    val rapid = RapidApplication.create(env)
    // DummyLoeser lar deg teste flyten, så du slipper å mocke hvert enkelt endepunkt
    DummyLoeser(rapid, BehovType.HENT_TRENGER_IM)
    // enten::
//    DummyLoeser(rapid, BehovType.PREUTFYLL, listOf(BehovType.FULLT_NAVN))
    // eller: start opp faktisk løser med LocalPreutfyltApp og lag dummy av resten:
    // HentPreutfyltLoeser(rapid)
//    DummyLoeser(rapid, BehovType.INNTEKT)
//    DummyLoeser(rapid, BehovType.ARBEIDSFORHOLD)
//    DummyLoeser(rapid, BehovType.VIRKSOMHET)
    DummyLoeser(rapid, BehovType.FULLT_NAVN)
    // Hvis ønskelig kan man kjøre opp "ekte" løsere med eller uten mocking parallellt, sammen med DummyLøser:
    val priProducer = mockk<PriProducer>(relaxed = true)
    TrengerForespoerselLoeser(rapid, priProducer)

    rapid.start()
}

class DummyLoeser(
    private val rapid: RapidsConnection,
    private val behov: BehovType,
) : River.PacketListener {
    init {
        logger.info("Starter ${simpleName()} for Behov $behov")
        River(rapid)
            .apply {
                validate { msg ->
                    msg.demandValues(Key.BEHOV to behov.name)
                    msg.interestedIn(Key.FORESPOERSEL_ID.str)
                }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
    ) {
        logger.info("Fikk pakke:\n${packet.toPretty()}")

        rapid
            .publish(
                *getData().toList().toTypedArray(),
            ).also {
                logger.info("Publiserte data:\n${it.toPretty()}")
            }
    }

    private fun getData(): Map<Key, JsonElement> {
        val fnr = "123"
        val orgnr = "123"

        return when (behov) {
            BehovType.HENT_TRENGER_IM ->
                mapOf(
                    Key.FORESPOERSEL_SVAR to
                        Forespoersel(
                            type = ForespoerselType.KOMPLETT,
                            orgnr = orgnr,
                            fnr = fnr,
                            vedtaksperiodeId = UUID.randomUUID(),
                            sykmeldingsperioder = listOf(2.januar til 3.januar),
                            egenmeldingsperioder = listOf(1.januar til 1.januar),
                            bestemmendeFravaersdager = mapOf(orgnr to 1.januar),
                            forespurtData = mockForespurtData(),
                            erBesvart = false,
                        ).toJson(Forespoersel.serializer()),
                )
            BehovType.VIRKSOMHET ->
                mapOf(
                    Key.VIRKSOMHET to "Din Bedrift A/S".toJson(),
                )
            BehovType.FULLT_NAVN ->
                mapOf(
                    Key.ARBEIDSTAKER_INFORMASJON to
                        PersonDato(
                            "Navn navnesen",
                            LocalDate.now(),
                            "123456",
                        ).toJson(
                            PersonDato.serializer(),
                        ),
                    Key.ARBEIDSGIVER_INFORMASJON to
                        PersonDato(
                            "Arbeidsgiver",
                            LocalDate.now(),
                            "654321",
                        ).toJson(
                            PersonDato.serializer(),
                        ),
                )
            BehovType.INNTEKT ->
                mapOf(
                    Key.INNTEKT to Inntekt(emptyList()).toJson(Inntekt.serializer()),
                )
            BehovType.ARBEIDSFORHOLD ->
                mapOf(
                    Key.ARBEIDSFORHOLD to
                        listOf(
                            Arbeidsforhold(
                                Arbeidsgiver("A/S", orgnr),
                                Ansettelsesperiode(PeriodeNullable(1.januar, 31.januar)),
                                LocalDateTime.now(),
                            ),
                        ).toJson(Arbeidsforhold.serializer()),
                )
            else -> error("Ukjent behov, ingen dummy-løsning!")
        }
    }
}
