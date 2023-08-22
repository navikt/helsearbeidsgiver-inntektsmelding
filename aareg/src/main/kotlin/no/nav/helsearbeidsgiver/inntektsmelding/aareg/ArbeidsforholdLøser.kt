@file:Suppress("NonAsciiCharacters", "ClassName")

package no.nav.helsearbeidsgiver.inntektsmelding.aareg

import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.aareg.AaregClient
import no.nav.helsearbeidsgiver.felles.Arbeidsforhold
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Data
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.createFail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.requireKeys
import no.nav.helsearbeidsgiver.utils.log.logger
import kotlin.system.measureTimeMillis
import no.nav.helsearbeidsgiver.aareg.Arbeidsforhold as KlientArbeidsforhold

class ArbeidsforholdLøser(
    rapidsConnection: RapidsConnection,
    private val aaregClient: AaregClient
) : Løser(rapidsConnection) {
    private val logger = logger()

    private val behovType = BehovType.ARBEIDSFORHOLD

    override fun accept(): River.PacketValidation =
        River.PacketValidation {
            it.demandValues(
                Key.BEHOV to behovType.name
            )
            it.requireKeys(
                Key.IDENTITETSNUMMER,
                Key.UUID
            )
        }

    override fun onBehov(packet: JsonMessage) {
    }

    override fun onBehov(behov: Behov) {
        measureTimeMillis {
            val transaksjonId = behov.uuid()
            val identitetsnummer = behov[Key.IDENTITETSNUMMER].asText()

            logger.info("Løser behov $behovType med transaksjon-ID $transaksjonId")

            val arbeidsforhold = hentArbeidsforhold(identitetsnummer, transaksjonId)

            if (arbeidsforhold != null) {
                publishData(
                    behov.createData(
                        mapOf(
                            DataFelt.ARBEIDSFORHOLD to (
                                Data(arbeidsforhold)
                                )
                        )
                    )
                )
            } else {
                publishFail(behov.createFail("Klarte ikke hente arbeidsforhold"))
            }
        }.also {
            logger.info("Arbeidsforhold løser took $it")
        }
    }

    private fun hentArbeidsforhold(fnr: String, callId: String): List<Arbeidsforhold>? =
        runCatching {
            runBlocking {
                val arbeidsforhold: List<no.nav.helsearbeidsgiver.aareg.Arbeidsforhold>
                measureTimeMillis {
                    arbeidsforhold = aaregClient.hentArbeidsforhold(fnr, callId)
                }.also {
                    logger.info("arbeidsforhold endepunkt tok $it")
                }
                arbeidsforhold
            }
        }
            .onFailure {
                sikkerLogger.error("Det oppstod en feil ved henting av arbeidsforhold for $fnr", it)
            }
            .getOrNull()
            ?.map(KlientArbeidsforhold::tilArbeidsforhold)
            ?.also {
                sikkerLogger.info("Fant arbeidsforhold $it for $fnr")
            }
}
