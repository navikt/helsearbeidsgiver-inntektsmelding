package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import kotlinx.coroutines.runBlocking
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifkasjon.graphql.generated.enums.SaksStatus
import no.nav.helsearbeidsgiver.felles.metrics.Metrics
import no.nav.helsearbeidsgiver.felles.metrics.recordTime
import java.util.UUID
import kotlin.time.Duration.Companion.days

// TODO vurder grense
val sakLevetid = 150.days

fun ArbeidsgiverNotifikasjonKlient.opprettSak(
    linkUrl: String,
    inntektsmeldingId: UUID,
    orgnr: String,
    sykmeldtNavn: String,
    sykmeldtFoedselsdato: String,
    initiellStatus: SaksStatus = SaksStatus.UNDER_BEHANDLING
): String =
    Metrics.agNotifikasjonRequest.recordTime(::opprettNySak.name) {
        runBlocking {
            opprettNySak(
                virksomhetsnummer = orgnr,
                merkelapp = "Inntektsmelding",
                grupperingsid = inntektsmeldingId.toString(),
                lenke = "$linkUrl/im-dialog/$inntektsmeldingId",
                tittel = "Inntektsmelding for $sykmeldtNavn: f. $sykmeldtFoedselsdato",
                statusTekst = "NAV trenger inntektsmelding",
                initiellStatus = initiellStatus,
                harddeleteOm = sakLevetid
            )
        }
    }

fun ArbeidsgiverNotifikasjonKlient.ferdigstillSak(sakId: String) {
    Metrics.agNotifikasjonRequest.recordTime(::nyStatusSak.name) {
        runBlocking {
            nyStatusSak(
                id = sakId,
                status = SaksStatus.FERDIG,
                statusTekst = "Mottatt - Se kvittering eller korriger inntektsmelding"
            )
        }
    }
}
