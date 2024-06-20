@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.pdl

import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.metrics.Metrics
import no.nav.helsearbeidsgiver.felles.metrics.recordTime
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Loeser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.interestedIn
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.publishData
import no.nav.helsearbeidsgiver.felles.rapidsrivers.requireKeys
import no.nav.helsearbeidsgiver.felles.utils.simpleName
import no.nav.helsearbeidsgiver.pdl.PdlClient
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.pipe.orDefault
import java.util.UUID
import kotlin.system.measureTimeMillis

class FulltNavnLoeser(
    rapidsConnection: RapidsConnection,
    private val pdlClient: PdlClient
) : Loeser(rapidsConnection) {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    private val BEHOV = BehovType.FULLT_NAVN

    override fun accept(): River.PacketValidation =
        River.PacketValidation {
            it.demandValues(
                Key.BEHOV to BEHOV.name
            )
            it.requireKeys(Key.IDENTITETSNUMMER)
            it.interestedIn(Key.ARBEIDSGIVER_ID)
        }

    override fun onBehov(behov: Behov) {
        val json = behov.jsonMessage.toJson().parseJson().toMap()

        val transaksjonId = Key.UUID.lesOrNull(UuidSerializer, json)
        val arbeidstakerId = Key.IDENTITETSNUMMER.lesOrNull(String.serializer(), json).orEmpty()
        val arbeidsgiverId = Key.ARBEIDSGIVER_ID.lesOrNull(String.serializer(), json).orEmpty()

        logger.info("Henter navn for transaksjonId $transaksjonId.")

        val identer = listOf(arbeidstakerId, arbeidsgiverId).filterNot(String::isEmpty)
        measureTimeMillis {
            try {
                val personer = hentPersoner(identer)

                logger.info("Mottok ${personer.size} navn fra pdl, ba om ${identer.size}")

                val arbeidstakerInfo = personer.firstOrNull { it.ident == arbeidstakerId }.orDefault(PersonDato("", null, arbeidstakerId))
                val arbeidsgiverInfo = personer.firstOrNull { it.ident == arbeidsgiverId }.orDefault(PersonDato("", null, arbeidsgiverId))

                val messagePairs =
                    json
                        .minus(listOf(Key.BEHOV, Key.EVENT_NAME))
                        .toList()
                        .toTypedArray()

                rapidsConnection.publishData(
                    eventName = behov.event,
                    transaksjonId = transaksjonId,
                    forespoerselId = behov.forespoerselId?.let(UUID::fromString),
                    Key.SELVBESTEMT_ID to json[Key.SELVBESTEMT_ID],
                    Key.ARBEIDSTAKER_INFORMASJON to arbeidstakerInfo.toJson(PersonDato.serializer()),
                    Key.ARBEIDSGIVER_INFORMASJON to arbeidsgiverInfo.toJson(PersonDato.serializer()),
                    Key.DATA to "".toJson(),
                    *messagePairs
                )
            } catch (ex: Exception) {
                logger.error("Klarte ikke hente navn for transaksjonId $transaksjonId.")
                sikkerLogger.error("Det oppstod en feil ved henting av identitetsnummer: $arbeidstakerId: ${ex.message} for transaksjonId $transaksjonId.", ex)
                publishFail(behov.createFail("Klarte ikke hente navn"))
            }
        }.also {
            logger.info("${simpleName()} took $it")
        }
    }

    private fun hentPersoner(identitetsnummere: List<String>): List<PersonDato> =
        Metrics.pdlRequest.recordTime(pdlClient::personBolk) {
            pdlClient.personBolk(identitetsnummere)
        }
            .orEmpty()
            .map {
                PersonDato(
                    navn = it.navn.fulltNavn(),
                    fødselsdato = it.foedselsdato,
                    ident = it.ident.orEmpty()
                )
            }
}
