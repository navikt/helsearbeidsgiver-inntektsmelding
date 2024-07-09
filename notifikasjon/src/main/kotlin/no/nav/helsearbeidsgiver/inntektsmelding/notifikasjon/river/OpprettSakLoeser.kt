package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river

import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Loeser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.publishData
import no.nav.helsearbeidsgiver.felles.utils.simpleName
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.opprettSak
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.pipe.orDefault
import java.time.format.DateTimeFormatter
import java.util.UUID

private val birthDateFormatter = DateTimeFormatter.ofPattern("ddMMyy")

class OpprettSakLoeser(
    rapidsConnection: RapidsConnection,
    private val arbeidsgiverNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
    private val linkUrl: String,
) : Loeser(rapidsConnection) {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun accept(): River.PacketValidation =
        River.PacketValidation {
            it.demandValue(Key.BEHOV.str, BehovType.OPPRETT_SAK.name)
            it.requireKey(Key.ORGNRUNDERENHET.str)
            it.interestedIn(Key.ARBEIDSTAKER_INFORMASJON.str)
        }

    private fun hentNavn(behov: Behov): PersonDato {
        if (behov[Key.ARBEIDSTAKER_INFORMASJON].isMissingNode) return PersonDato("Ukjent", null, "")
        return behov[Key.ARBEIDSTAKER_INFORMASJON].toString().fromJson(PersonDato.serializer())
    }

    override fun onBehov(behov: Behov) {
        val utloesendeMelding = behov.jsonMessage.toJson().parseJson()
        val transaksjonId =
            utloesendeMelding
                .toMap()[Key.UUID]
                ?.fromJson(UuidSerializer)
                .orDefault {
                    UUID.randomUUID().also {
                        sikkerLogger.error(
                            "Mangler transaksjonId i ${simpleName()}. Erstatter med ny, tilfeldig UUID '$it'." +
                                "\n${utloesendeMelding.toPretty()}",
                        )
                    }
                }

        val forespoerselId = behov.forespoerselId!!.let(UUID::fromString)

        logger.info("Skal opprette sak for forespørselId: $forespoerselId")
        val orgnr = behov[Key.ORGNRUNDERENHET].asText()
        val personDato = hentNavn(behov)
        val formattertFoedselsdato = personDato.fødselsdato?.format(birthDateFormatter) ?: "Ukjent"

        val sakId =
            runCatching {
                arbeidsgiverNotifikasjonKlient.opprettSak(
                    lenke = "$linkUrl/im-dialog/$forespoerselId",
                    inntektsmeldingTypeId = forespoerselId,
                    orgnr = orgnr,
                    sykmeldtNavn = personDato.navn,
                    sykmeldtFoedselsdato = formattertFoedselsdato,
                )
            }.onFailure {
                logger.error("Klarte ikke opprette sak.", it)
                sikkerLogger.error("Klarte ikke opprette sak.", it)
            }.getOrNull()

        if (sakId.isNullOrBlank()) {
            val fail =
                Fail(
                    feilmelding = "Opprett sak feilet",
                    event = behov.event,
                    transaksjonId = transaksjonId,
                    forespoerselId = forespoerselId,
                    utloesendeMelding = utloesendeMelding,
                )
            publishFail(fail)
        } else {
            logger.info("OpprettSakLøser fikk opprettet sak for forespørselId: ${behov.forespoerselId}")

            rapidsConnection.publishData(
                eventName = behov.event,
                transaksjonId = transaksjonId,
                forespoerselId = forespoerselId,
                Key.SAK_ID to sakId.toJson(),
            )

            sikkerLogger.info("OpprettSakLøser publiserte med sakId=$sakId og forespoerselId=${behov.forespoerselId}")
        }
    }
}
