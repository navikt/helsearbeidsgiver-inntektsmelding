package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Loeser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.interestedIn
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.publishData
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID
import kotlin.system.measureTimeMillis

private const val EMPTY_PAYLOAD = "{}"

class HentPersistertLoeser(rapidsConnection: RapidsConnection, private val repository: InntektsmeldingRepository) : Loeser(rapidsConnection) {
    private val behov = BehovType.HENT_PERSISTERT_IM
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun accept(): River.PacketValidation =
        River.PacketValidation {
            it.demandValues(
                Key.BEHOV to behov.name,
            )
            it.interestedIn(
                Key.EVENT_NAME,
                Key.FORESPOERSEL_ID,
            )
        }

    override fun onBehov(behov: Behov) {
        measureTimeMillis {
            logger.info("Skal hente persistert inntektsmelding med forespørselId ${behov.forespoerselId}")
            try {
                val (dokument, eksternInntektsmelding) =
                    repository
                        .hentNyesteEksternEllerInternInntektsmelding(behov.forespoerselId!!)
                        .tilPayloadPair()

                if (dokument == EMPTY_PAYLOAD) {
                    logger.info("Fant IKKE persistert inntektsmelding for forespørselId ${behov.forespoerselId}")
                } else {
                    sikkerLogger.info("Fant persistert inntektsmelding: $dokument for forespørselId ${behov.forespoerselId}")
                }
                if (eksternInntektsmelding == EMPTY_PAYLOAD) {
                    logger.info("Fant IKKE persistert eksternInntektsmelding for forespørselId ${behov.forespoerselId}")
                } else {
                    sikkerLogger.info("Fant persistert eksternInntektsmelding: $eksternInntektsmelding for forespørselId ${behov.forespoerselId}")
                }

                val json = behov.jsonMessage.toJson().parseJson().toMap()

                val transaksjonId = Key.UUID.les(UuidSerializer, json)

                rapidsConnection.publishData(
                    eventName = behov.event,
                    transaksjonId = transaksjonId,
                    forespoerselId = behov.forespoerselId?.let(UUID::fromString),
                    Key.INNTEKTSMELDING_DOKUMENT to dokument.toJson(),
                    Key.EKSTERN_INNTEKTSMELDING to eksternInntektsmelding.toJson(),
                )
            } catch (ex: Exception) {
                logger.info("Det oppstod en feil ved uthenting av persistert inntektsmelding for forespørselId ${behov.forespoerselId}")
                sikkerLogger.error(
                    "Det oppstod en feil ved uthenting av persistert inntektsmelding for forespørselId ${behov.forespoerselId}",
                    ex,
                )
                behov.createFail("Klarte ikke hente persistert inntektsmelding").also {
                    publishFail(it)
                }
            }
        }.also {
            logger.info("Hent inntektmelding fra DB took $it")
        }
    }
}

fun Pair<Inntektsmelding?, EksternInntektsmelding?>?.tilPayloadPair(): Pair<String, String> =
    Pair(
        this?.first?.toJsonStr(Inntektsmelding.serializer()) ?: EMPTY_PAYLOAD,
        this?.second?.toJsonStr(EksternInntektsmelding.serializer()) ?: EMPTY_PAYLOAD,
    )
