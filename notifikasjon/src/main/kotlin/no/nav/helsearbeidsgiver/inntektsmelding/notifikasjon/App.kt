package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateless
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.EndrePaaminnelseRiver
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.FerdigstillForespoerselSakOgOppgaveRiver
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.FjernPaaminnelseRiver
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.OpprettForespoerselSakOgOppgaveRiver
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.OpprettSelvbestemtSakRiver
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.UtgaattForespoerselRiver
import no.nav.helsearbeidsgiver.tokenprovider.oauth2ClientCredentialsTokenGetter
import no.nav.helsearbeidsgiver.utils.log.logger

private val logger = "im-notifikasjon".logger()

fun main() {
    RapidApplication
        .create(System.getenv())
        .createNotifikasjonService()
        .createNotifikasjonRivers(Env.linkUrl, Env.paaminnelseToggle, buildClient())
        .start()
}

fun RapidsConnection.createNotifikasjonService(): RapidsConnection =
    also {
        logger.info("Starter ${HentDataTilSakOgOppgaveService::class.simpleName}...")
        ServiceRiverStateless(
            HentDataTilSakOgOppgaveService(this),
        ).connect(this)
        logger.info("Starter ${HentDataTilPaaminnelseService::class.simpleName}...")
        ServiceRiverStateless(
            HentDataTilPaaminnelseService(this),
        ).connect(this)
    }

fun RapidsConnection.createNotifikasjonRivers(
    linkUrl: String,
    paaminnelseToggle: PaaminnelseToggle,
    agNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
): RapidsConnection =
    also {
        logger.info("Starter ${OpprettForespoerselSakOgOppgaveRiver::class.simpleName}...")
        OpprettForespoerselSakOgOppgaveRiver(
            lenkeBaseUrl = linkUrl,
            paaminnelseToggle = paaminnelseToggle,
            agNotifikasjonKlient = agNotifikasjonKlient,
        ).connect(this)

        logger.info("Starter ${OpprettSelvbestemtSakRiver::class.simpleName}...")
        OpprettSelvbestemtSakRiver(linkUrl, agNotifikasjonKlient).connect(this)

        logger.info("Starter ${FerdigstillForespoerselSakOgOppgaveRiver::class.simpleName}...")
        FerdigstillForespoerselSakOgOppgaveRiver(linkUrl, agNotifikasjonKlient).connect(this)

        logger.info("Starter ${UtgaattForespoerselRiver::class.simpleName}...")
        UtgaattForespoerselRiver(linkUrl, agNotifikasjonKlient).connect(this)

        logger.info("Starter ${FjernPaaminnelseRiver::class.simpleName}...")
        FjernPaaminnelseRiver(
            agNotifikasjonKlient = agNotifikasjonKlient,
            paaminnelseToggle = paaminnelseToggle,
        ).connect(this)
        logger.info("Starter ${EndrePaaminnelseRiver::class.simpleName}...")
        EndrePaaminnelseRiver(
            agNotifikasjonKlient = agNotifikasjonKlient,
        ).connect(this)
    }

private fun buildClient(): ArbeidsgiverNotifikasjonKlient {
    val tokenGetter = oauth2ClientCredentialsTokenGetter(Env.oauth2Environment)
    return ArbeidsgiverNotifikasjonKlient(Env.notifikasjonUrl, tokenGetter)
}
