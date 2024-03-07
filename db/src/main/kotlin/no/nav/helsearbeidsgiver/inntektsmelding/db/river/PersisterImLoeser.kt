package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Innsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.TrengerInntekt
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Loeser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.interestedIn
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.publishData
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.inntektsmelding.db.erDuplikatAv
import no.nav.helsearbeidsgiver.inntektsmelding.db.mapInntektsmelding
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class PersisterImLoeser(rapidsConnection: RapidsConnection, private val repository: InntektsmeldingRepository) : Loeser(rapidsConnection) {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun accept(): River.PacketValidation =
        River.PacketValidation {
            it.demandValues(
                Key.BEHOV to BehovType.PERSISTER_IM.name
            )
            it.interestedIn(
                Key.INNTEKTSMELDING,
                Key.VIRKSOMHET,
                Key.ARBEIDSTAKER_INFORMASJON,
                Key.ARBEIDSGIVER_INFORMASJON,
                Key.FORESPOERSEL_ID,
                Key.FORESPOERSEL_SVAR
            )
        }

    override fun onBehov(behov: Behov) {
        val forespoerselId = behov.forespoerselId!!.let(UUID::fromString)

        logger.info("Løser behov ${BehovType.PERSISTER_IM} med id $forespoerselId")

        try {
            val json = behov.jsonMessage.toJson().parseJson().toMap()

            val transaksjonId = Key.UUID.lesOrNull(UuidSerializer, json)
            val innsending = Key.INNTEKTSMELDING.les(Innsending.serializer(), json)
            val virksomhetNavn = Key.VIRKSOMHET.les(String.serializer(), json)
            val arbeidstaker = Key.ARBEIDSTAKER_INFORMASJON.les(PersonDato.serializer(), json)
            val innsender = Key.ARBEIDSGIVER_INFORMASJON.les(PersonDato.serializer(), json)
            val forespoersel = Key.FORESPOERSEL_SVAR.les(TrengerInntekt.serializer(), json)

            val inntektsmelding = mapInntektsmelding(
                request = innsending,
                fulltnavnArbeidstaker = arbeidstaker.navn,
                virksomhetNavn = virksomhetNavn,
                innsenderNavn = innsender.navn,
                vedtaksperiodeId = forespoersel.vedtaksperiodeId
            )

            val sisteIm = repository.hentNyeste(forespoerselId)
            val erDuplikat = sisteIm?.erDuplikatAv(inntektsmelding) ?: false

            if (erDuplikat) {
                sikkerLogger.warn("Fant duplikat av inntektsmelding for forespoerselId: $forespoerselId")
            } else {
                repository.lagreInntektsmelding(forespoerselId.toString(), inntektsmelding)
                sikkerLogger.info("Lagret Inntektsmelding for forespoerselId: $forespoerselId")
            }

            rapidsConnection.publishData(
                eventName = behov.event,
                transaksjonId = transaksjonId,
                forespoerselId = forespoerselId,
                Key.INNTEKTSMELDING_DOKUMENT to inntektsmelding.toJson(Inntektsmelding.serializer()),
                Key.ER_DUPLIKAT_IM to erDuplikat.toJson(Boolean.serializer())
            )
        } catch (ex: Exception) {
            logger.error("Klarte ikke persistere: $forespoerselId")
            sikkerLogger.error("Klarte ikke persistere: $forespoerselId", ex)
            behov.createFail("Klarte ikke persistere: $forespoerselId").also { publishFail(it) }
        }
    }
}
