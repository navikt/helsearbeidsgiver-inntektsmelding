package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import kotlinx.coroutines.runBlocking
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifkasjon.graphql.generated.enums.SaksStatus
import no.nav.helsearbeidsgiver.felles.metrics.Metrics
import java.util.UUID
import kotlin.time.Duration.Companion.days

// 13x30 dager
val sakLevetid = 390.days

object NotifikasjonTekst {
    const val MERKELAPP = "Inntektsmelding sykepenger"

    @Deprecated("Bruk NotifikasjonTekst.MERKELAPP. Utdatert siden 21.05.2024.")
    const val MERKELAPP_GAMMEL = "Inntektsmelding"
    const val OPPGAVE_TEKST = "Innsending av inntektsmelding"
    const val STATUS_TEKST_UNDER_BEHANDLING = "NAV trenger inntektsmelding"
    const val STATUS_TEKST_FERDIG = "Mottatt – Se kvittering eller korriger inntektsmelding"

    fun lenkeFerdigstilt(
        linkUrl: String,
        forespoerselId: UUID,
    ): String = "$linkUrl/im-dialog/kvittering/$forespoerselId"

    fun sakTittel(
        sykmeldtNavn: String,
        sykmeldtFoedselsdato: String,
    ): String = "Inntektsmelding for $sykmeldtNavn: f. $sykmeldtFoedselsdato"

    fun oppgaveInnhold(
        orgnr: String,
        orgNavn: String,
    ): String =
        listOf(
            "$orgNavn - orgnr $orgnr: En av dine ansatte har søkt om sykepenger",
            "og vi trenger inntektsmelding for å behandle søknaden.",
            "Logg inn på Min side – arbeidsgiver hos NAV.",
            "Hvis dere sender inntektsmelding via lønnssystem kan dere fortsatt gjøre dette,",
            "og trenger ikke sende inn via Min side – arbeidsgiver.",
        ).joinToString(separator = " ")
}

fun ArbeidsgiverNotifikasjonKlient.opprettSak(
    lenke: String,
    inntektsmeldingTypeId: UUID,
    orgnr: String,
    sykmeldtNavn: String,
    sykmeldtFoedselsdato: String,
    initiellStatus: SaksStatus = SaksStatus.UNDER_BEHANDLING,
): String {
    val statusTekst =
        when (initiellStatus) {
            SaksStatus.FERDIG -> NotifikasjonTekst.STATUS_TEKST_FERDIG
            else -> NotifikasjonTekst.STATUS_TEKST_UNDER_BEHANDLING
        }

    return Metrics.agNotifikasjonRequest.recordTime(::opprettNySak) {
        runBlocking {
            opprettNySak(
                virksomhetsnummer = orgnr,
                merkelapp = NotifikasjonTekst.MERKELAPP,
                grupperingsid = inntektsmeldingTypeId.toString(),
                lenke = lenke,
                tittel = NotifikasjonTekst.sakTittel(sykmeldtNavn, sykmeldtFoedselsdato),
                statusTekst = statusTekst,
                initiellStatus = initiellStatus,
                harddeleteOm = sakLevetid,
            )
        }
    }
}

fun ArbeidsgiverNotifikasjonKlient.ferdigstillSak(
    forespoerselId: UUID,
    nyLenke: String,
) {
    Metrics.agNotifikasjonRequest.recordTime(::nyStatusSak) {
        runCatching {
            nyStatusSakByGrupperingsid(
                grupperingsid = forespoerselId.toString(),
                merkelapp = NotifikasjonTekst.MERKELAPP,
                status = SaksStatus.FERDIG,
                statusTekst = NotifikasjonTekst.STATUS_TEKST_FERDIG,
                nyLenke = nyLenke,
            )
        }.onFailure {
            nyStatusSakByGrupperingsid(
                grupperingsid = forespoerselId.toString(),
                merkelapp = NotifikasjonTekst.MERKELAPP_GAMMEL,
                status = SaksStatus.FERDIG,
                statusTekst = NotifikasjonTekst.STATUS_TEKST_FERDIG,
                nyLenke = nyLenke,
            )
        }
    }
}
