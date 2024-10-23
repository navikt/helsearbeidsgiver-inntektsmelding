package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import kotlinx.coroutines.runBlocking
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifkasjon.graphql.generated.enums.SaksStatus
import no.nav.helsearbeidsgiver.felles.domene.Person
import no.nav.helsearbeidsgiver.felles.metrics.Metrics
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
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
    const val STATUS_TEKST_AVBRUTT = "Avbrutt av NAV"

    fun lenkeAktiv(
        linkUrl: String,
        forespoerselId: UUID,
    ): String = "$linkUrl/im-dialog/$forespoerselId"

    fun lenkeFerdigstiltForespoersel(
        linkUrl: String,
        forespoerselId: UUID,
    ): String = "$linkUrl/im-dialog/kvittering/$forespoerselId"

    fun lenkeFerdigstiltSelvbestemt(
        linkUrl: String,
        selvbestemtId: UUID,
    ): String = "$linkUrl/im-dialog/kvittering/agi/$selvbestemtId"

    fun sakTittel(sykmeldt: Person): String {
        val foedselsdato = sykmeldt.fnr.verdi.take(6)
        return "Inntektsmelding for ${sykmeldt.navn}: f. $foedselsdato"
    }

    fun oppgaveInnhold(
        orgnr: Orgnr,
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
    orgnr: Orgnr,
    sykmeldt: Person,
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
                virksomhetsnummer = orgnr.verdi,
                grupperingsid = inntektsmeldingTypeId.toString(),
                merkelapp = NotifikasjonTekst.MERKELAPP,
                lenke = lenke,
                tittel = NotifikasjonTekst.sakTittel(sykmeldt),
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
): Result<Unit> =
    Metrics.agNotifikasjonRequest.recordTime(::nyStatusSak) {
        runCatching {
            nyStatusSakByGrupperingsid(
                grupperingsid = forespoerselId.toString(),
                merkelapp = NotifikasjonTekst.MERKELAPP,
                status = SaksStatus.FERDIG,
                statusTekst = NotifikasjonTekst.STATUS_TEKST_FERDIG,
                nyLenke = nyLenke,
            )
        }.recoverCatching {
            nyStatusSakByGrupperingsid(
                grupperingsid = forespoerselId.toString(),
                merkelapp = NotifikasjonTekst.MERKELAPP_GAMMEL,
                status = SaksStatus.FERDIG,
                statusTekst = NotifikasjonTekst.STATUS_TEKST_FERDIG,
                nyLenke = nyLenke,
            )
        }
    }

fun ArbeidsgiverNotifikasjonKlient.avbrytSak(
    forespoerselId: UUID,
    nyLenke: String,
): Result<Unit> =
    Metrics.agNotifikasjonRequest.recordTime(::nyStatusSakByGrupperingsid) {
        runCatching {
            nyStatusSakByGrupperingsid(
                grupperingsid = forespoerselId.toString(),
                merkelapp = NotifikasjonTekst.MERKELAPP,
                status = SaksStatus.FERDIG,
                statusTekst = NotifikasjonTekst.STATUS_TEKST_AVBRUTT,
                nyLenke = nyLenke,
            )
        }.recoverCatching {
            nyStatusSakByGrupperingsid(
                grupperingsid = forespoerselId.toString(),
                merkelapp = NotifikasjonTekst.MERKELAPP_GAMMEL,
                status = SaksStatus.FERDIG,
                statusTekst = NotifikasjonTekst.STATUS_TEKST_AVBRUTT,
                nyLenke = nyLenke,
            )
        }
    }

fun ArbeidsgiverNotifikasjonKlient.opprettOppgave(
    lenke: String,
    forespoerselId: UUID,
    orgnr: Orgnr,
    orgNavn: String,
): String =
    runBlocking {
        opprettNyOppgave(
            virksomhetsnummer = orgnr.verdi,
            eksternId = forespoerselId.toString(),
            grupperingsid = forespoerselId.toString(),
            merkelapp = NotifikasjonTekst.MERKELAPP,
            lenke = lenke,
            tekst = NotifikasjonTekst.OPPGAVE_TEKST,
            varslingTittel = NotifikasjonTekst.STATUS_TEKST_UNDER_BEHANDLING,
            varslingInnhold = NotifikasjonTekst.oppgaveInnhold(orgnr, orgNavn),
            tidspunkt = null,
        )
    }
