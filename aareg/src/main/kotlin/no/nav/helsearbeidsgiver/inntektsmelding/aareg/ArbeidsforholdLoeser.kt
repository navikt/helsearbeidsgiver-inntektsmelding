@file:Suppress("NonAsciiCharacters", "ClassName")

package no.nav.helsearbeidsgiver.inntektsmelding.aareg

import io.prometheus.client.Summary
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.aareg.AaregClient
import no.nav.helsearbeidsgiver.felles.Arbeidsforhold
import no.nav.helsearbeidsgiver.felles.ArbeidsforholdListe
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Loeser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.requireKeys
import no.nav.helsearbeidsgiver.felles.utils.simpleName
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import kotlin.system.measureTimeMillis
import no.nav.helsearbeidsgiver.aareg.Arbeidsforhold as KlientArbeidsforhold

class ArbeidsforholdLoeser(
    rapidsConnection: RapidsConnection,
    private val aaregClient: AaregClient
) : Loeser(rapidsConnection) {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    private val behovType = BehovType.ARBEIDSFORHOLD
    private val requestLatency = Summary.build()
        .name("simba_aareg_hent_arbeidsforhold_latency_seconds")
        .help("aareg hent arbeidsforhold latency in seconds")
        .register()

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

    override fun onBehov(behov: Behov) {
        measureTimeMillis {
            val transaksjonId = behov.uuid()
            val identitetsnummer = behov[Key.IDENTITETSNUMMER].asText()

            logger.info("Løser behov $behovType med transaksjon-ID $transaksjonId")

            val arbeidsforhold = hentArbeidsforhold(identitetsnummer, transaksjonId)?.let { ArbeidsforholdListe(it) }

            if (arbeidsforhold != null) {
                publishData(
                    behov.createData(
                        mapOf(
                            Key.ARBEIDSFORHOLD to arbeidsforhold
                        )
                    )
                )
            } else {
                publishFail(behov.createFail("Klarte ikke hente arbeidsforhold"))
            }
        }.also {
            logger.info("${simpleName()} took $it")
        }
    }

    private fun hentArbeidsforhold(fnr: String, callId: String): List<Arbeidsforhold>? =
        runCatching {
            runBlocking {
                val arbeidsforhold: List<no.nav.helsearbeidsgiver.aareg.Arbeidsforhold>
                val requestTimer = requestLatency.startTimer()
                measureTimeMillis {
                    arbeidsforhold = aaregClient.hentArbeidsforhold(fnr, callId)
                }.also {
                    logger.info("arbeidsforhold endepunkt tok $it")
                    requestTimer.observeDuration()
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
