package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.HardDeleteSakException
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Loeser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.pipe.ifFalse
import no.nav.helsearbeidsgiver.utils.pipe.ifTrue

class SlettSakLoeser(
    rapidsConnection: RapidsConnection,
    private val arbeidsgiverNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient
) : Loeser(rapidsConnection) {

    private val logger = logger()

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.demandValue(Key.BEHOV.str, BehovType.SLETT_SAK.name)
            it.interestedIn(Key.SAK_ID.str)
        }
    }
    private fun slettSak(
        sakId: String
    ): Boolean {
        return try {
            runBlocking {
                arbeidsgiverNotifikasjonKlient.hardDeleteSak(sakId)
            }
            true
        } catch (e: HardDeleteSakException) {
            sikkerLogger.error("Feil ved sletting av sak $sakId!", e)
            false
        }
    }

    override fun onBehov(behov: Behov) {
        val sakId = behov[Key.SAK_ID].asText()
        slettSak(sakId)
            .ifTrue { logger.info("Slettet sak $sakId") }
            .ifFalse { logger.error("Feilet ved sletting av sak $sakId") }
    }
}
