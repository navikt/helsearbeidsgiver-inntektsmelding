package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import kotlinx.coroutines.runBlocking
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifkasjon.graphql.generated.enums.SaksStatus
import no.nav.helsearbeidsgiver.felles.metrics.Metrics
import no.nav.helsearbeidsgiver.felles.metrics.recordTime
import java.util.UUID
import kotlin.time.Duration.Companion.days

// 13x30 dager
val sakLevetid = 390.days

fun ArbeidsgiverNotifikasjonKlient.opprettSak(
    lenke: String,
    inntektsmeldingTypeId: UUID,
    orgnr: String,
    sykmeldtNavn: String,
    sykmeldtFoedselsdato: String,
    initiellStatus: SaksStatus = SaksStatus.UNDER_BEHANDLING
): String =
    Metrics.agNotifikasjonRequest.recordTime(::opprettNySak) {
        runBlocking {
            opprettNySak(
                virksomhetsnummer = orgnr,
                merkelapp = "Inntektsmelding",
                grupperingsid = inntektsmeldingTypeId.toString(),
                lenke = lenke,
                tittel = "Inntektsmelding for $sykmeldtNavn: f. $sykmeldtFoedselsdato",
                statusTekst = "NAV trenger inntektsmelding",
                initiellStatus = initiellStatus,
                harddeleteOm = sakLevetid
            )
        }
    }

fun ArbeidsgiverNotifikasjonKlient.ferdigstillSak(sakId: String, nyLenkeTilSak: String) {
    Metrics.agNotifikasjonRequest.recordTime(::nyStatusSak) {
        runBlocking {
            nyStatusSak(
                id = sakId,
                status = SaksStatus.FERDIG,
                statusTekst = "Mottatt - Se kvittering eller korriger inntektsmelding",
                nyLenkeTilSak = nyLenkeTilSak
            )
        }
    }
}
