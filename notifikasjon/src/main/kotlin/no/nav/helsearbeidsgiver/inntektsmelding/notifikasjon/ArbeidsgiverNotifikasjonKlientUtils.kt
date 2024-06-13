package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import kotlinx.coroutines.runBlocking
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifkasjon.graphql.generated.enums.SaksStatus
import no.nav.helsearbeidsgiver.felles.metrics.Metrics
import no.nav.helsearbeidsgiver.felles.metrics.recordTime
import java.util.UUID
import kotlin.time.Duration.Companion.days

private const val STATUS_TEKST_UNDER_BEHANDLING = "NAV trenger inntektsmelding"
private const val STATUS_TEKST_FERDIG = "Mottatt - Se kvittering eller korriger inntektsmelding"

// 13x30 dager
val sakLevetid = 390.days

fun ArbeidsgiverNotifikasjonKlient.opprettSak(
    lenke: String,
    inntektsmeldingTypeId: UUID,
    orgnr: String,
    sykmeldtNavn: String,
    sykmeldtFoedselsdato: String,
    initiellStatus: SaksStatus = SaksStatus.UNDER_BEHANDLING
): String {
    val statusTekst = when (initiellStatus) {
        SaksStatus.FERDIG -> STATUS_TEKST_FERDIG
        else -> STATUS_TEKST_UNDER_BEHANDLING
    }

    return Metrics.agNotifikasjonRequest.recordTime(::opprettNySak) {
        runBlocking {
            opprettNySak(
                virksomhetsnummer = orgnr,
                merkelapp = "Inntektsmelding sykepenger",
                grupperingsid = inntektsmeldingTypeId.toString(),
                lenke = lenke,
                tittel = "Inntektsmelding for $sykmeldtNavn: f. $sykmeldtFoedselsdato",
                statusTekst = statusTekst,
                initiellStatus = initiellStatus,
                harddeleteOm = sakLevetid
            )
        }
    }
}

fun ArbeidsgiverNotifikasjonKlient.ferdigstillSak(sakId: String, nyLenkeTilSak: String) {
    Metrics.agNotifikasjonRequest.recordTime(::nyStatusSak) {
        runBlocking {
            nyStatusSak(
                id = sakId,
                status = SaksStatus.FERDIG,
                statusTekst = STATUS_TEKST_FERDIG,
                nyLenkeTilSak = nyLenkeTilSak
            )
        }
    }
}
