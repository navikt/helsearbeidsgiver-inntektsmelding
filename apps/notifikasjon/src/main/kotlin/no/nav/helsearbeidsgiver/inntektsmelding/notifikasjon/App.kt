package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import no.nav.hag.simba.utils.auth.AuthClient
import no.nav.hag.simba.utils.auth.IdentityProvider
import no.nav.hag.simba.utils.rr.Publisher
import no.nav.hag.simba.utils.rr.river.ObjectRiver
import no.nav.hag.simba.utils.rr.service.ServiceRiverStateless
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.Altinn3Ressurs
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.AltinnMottaker
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.FerdigstillSakOgOppgaveRiver
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.FjernPaaminnelseRiver
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.OpprettForespoerselSakOgOppgaveRiver
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.OpprettSelvbestemtSakRiver
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river.UtgaattForespoerselRiver

fun main() {
    ObjectRiver.connectToRapid {
        listOf(
            createNotifikasjonServices(it),
            createNotifikasjonRivers(Env.linkUrl, Env.tidMellomOppgaveOpprettelseOgPaaminnelse, agNotifikasjonKlient()),
        ).flatten()
    }
}

fun createNotifikasjonServices(publisher: Publisher): List<ServiceRiverStateless> =
    listOf(
        ServiceRiverStateless(
            HentDataTilSakOgOppgaveService(publisher),
        ),
    )

fun createNotifikasjonRivers(
    linkUrl: String,
    tidMellomOppgaveOpprettelseOgPaaminnelse: String,
    agNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
): List<ObjectRiver.Simba<*>> =
    listOf(
        OpprettForespoerselSakOgOppgaveRiver(
            lenkeBaseUrl = linkUrl,
            tidMellomOppgaveOpprettelseOgPaaminnelse = tidMellomOppgaveOpprettelseOgPaaminnelse,
            agNotifikasjonKlient = agNotifikasjonKlient,
        ),
        OpprettSelvbestemtSakRiver(linkUrl, agNotifikasjonKlient),
        FerdigstillSakOgOppgaveRiver(linkUrl, agNotifikasjonKlient),
        UtgaattForespoerselRiver(linkUrl, agNotifikasjonKlient),
        FjernPaaminnelseRiver(agNotifikasjonKlient),
    )

private fun agNotifikasjonKlient(): ArbeidsgiverNotifikasjonKlient {
    val tokenGetter = AuthClient().tokenGetter(IdentityProvider.AZURE_AD, Env.agNotifikasjonScope)
    val altinnMottaker = if (Env.erProd) AltinnMottaker.Altinn2("4936", "1") else AltinnMottaker.Altinn3(Altinn3Ressurs.INNTEKTSMELDING)
    return ArbeidsgiverNotifikasjonKlient(Env.agNotifikasjonUrl, altinnMottaker, tokenGetter)
}
