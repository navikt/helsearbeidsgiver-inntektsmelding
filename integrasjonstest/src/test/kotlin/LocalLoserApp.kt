import io.mockk.coEvery
import io.mockk.mockkClass
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.app.LocalApp
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandAll
import no.nav.helsearbeidsgiver.felles.test.date.januar
import no.nav.helsearbeidsgiver.felles.til
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.PriProducer
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.TrengerForespoerselLøser
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.toHentTrengerImLøsning
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

val logger: Logger = LoggerFactory.getLogger("helsearbeidsgiver-im-integrasjon")

fun main() {
    val env = LocalApp().getLocalEnvironment("im-helsebro", 8083)
    val priProducer = mockkClass(PriProducer::class)
    val rapid = RapidApplication.create(env)
    LoserEthvertBehov(rapid)
    coEvery { priProducer.send(any()) }.returns(true)
    TrengerForespoerselLøser(rapid, priProducer)

    rapid.start()
}

class LoserEthvertBehov(rapidsConnection: RapidsConnection) : River.PacketListener {
    val rapid = rapidsConnection

    init {
        logger.info("nu kør vi")
        River(rapidsConnection).apply {
            validate { msg ->
                msg.demandAll(Key.BEHOV, listOf(BehovType.HENT_TRENGER_IM))
                msg.rejectKey(Key.LØSNING.str)
                msg.interestedIn(Key.FORESPOERSEL_ID.str, Key.INITIATE_ID.str, Key.BOOMERANG.str)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("Fikk pakke: ${packet.toJson()}")
        packet.setLøsning(BehovType.HENT_TRENGER_IM, svar.toHentTrengerImLøsning())

        logger.info("Publiserer løsning: $packet")
        this.rapid.publish(packet.toJson())
    }

    val boomerang_ikke_i_bruk = mapOf(
        Key.NESTE_BEHOV to listOf(BehovType.PREUTFYLL).toJson(BehovType.serializer())
    )

    val svar = ForespoerselSvar(
        UUID.fromString("77c9e7ed-efcc-45e5-9177-4db2d4e466c1"),
        ForespoerselSvar.Suksess("123", "123", listOf(1.januar til 2.januar), emptyList()),
        null,
        boomerang_ikke_i_bruk.toJson(MapSerializer(Key.serializer(), JsonElement.serializer()))
    )
}

private fun JsonMessage.setLøsning(nøkkel: BehovType, data: Any) {
    this[Key.LØSNING.str] = mapOf(
        nøkkel.name to data
    )
}
