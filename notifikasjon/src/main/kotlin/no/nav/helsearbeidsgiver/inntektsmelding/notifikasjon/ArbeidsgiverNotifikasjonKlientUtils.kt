package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import kotlinx.coroutines.runBlocking
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.Paaminnelse
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.SakEllerOppgaveDuplikatException
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.SakEllerOppgaveFinnesIkkeException
import no.nav.helsearbeidsgiver.arbeidsgivernotifkasjon.graphql.generated.enums.SaksStatus
import no.nav.helsearbeidsgiver.felles.domene.Person
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID
import kotlin.time.Duration.Companion.days

// 13x30 dager
val sakLevetid = 390.days

private val logger = "arbeidsgiver-notifikasjon-klient-utils".logger()
private val sikkerLogger = sikkerLogger()

object NotifikasjonTekst {
    const val MERKELAPP = "Inntektsmelding sykepenger"

    @Deprecated("Bruk NotifikasjonTekst.MERKELAPP. Utdatert siden 21.05.2024.")
    const val MERKELAPP_GAMMEL = "Inntektsmelding"
    const val OPPGAVE_TEKST = "Innsending av inntektsmelding"
    const val STATUS_TEKST_UNDER_BEHANDLING = "Nav trenger inntektsmelding"
    const val STATUS_TEKST_FERDIG = "Mottatt – Se kvittering eller korriger inntektsmelding"
    const val STATUS_TEKST_AVBRUTT = "Avbrutt av Nav"

    fun lenkeAktivForespoersel(
        linkUrl: String,
        forespoerselId: UUID,
    ): String = "$linkUrl/im-dialog/$forespoerselId"

    fun lenkeFerdigstiltForespoersel(
        linkUrl: String,
        forespoerselId: UUID,
    ): String = "$linkUrl/im-dialog/kvittering/$forespoerselId"

    fun lenkeUtgaattForespoersel(linkUrl: String): String = "$linkUrl/im-dialog/utgatt"

    fun lenkeFerdigstiltSelvbestemt(
        linkUrl: String,
        selvbestemtId: UUID,
    ): String = "$linkUrl/im-dialog/kvittering/agi/$selvbestemtId"

    fun sakTittel(sykmeldt: Person): String = "Inntektsmelding for ${sykmeldt.navn}: f. ${sykmeldt.fnr.lesFoedselsdato()}"

    fun oppgaveInnhold(
        orgnr: Orgnr,
        orgNavn: String,
    ): String =
        listOf(
            "$orgNavn - orgnr $orgnr: En av dine ansatte har søkt om sykepenger",
            "og vi trenger inntektsmelding for å behandle søknaden.",
            "Logg inn på Min side – arbeidsgiver hos Nav.",
            "Hvis dere sender inntektsmelding via lønnssystem kan dere fortsatt gjøre dette,",
            "og trenger ikke sende inn via Min side – arbeidsgiver.",
        ).joinToString(separator = " ")

    fun paaminnelseInnhold(
        orgnr: Orgnr,
        orgNavn: String,
    ): String =
        listOf(
            "Nav venter fortsatt på inntektsmelding for en av deres ansatte.",
            "Vi trenger inntektsmeldingen så snart som mulig,",
            "ellers kan vi ikke behandle søknaden om sykepenger.",
            "Logg inn på Min side – arbeidsgiver på Nav for å finne ut hvilken inntektsmelding det gjelder.",
            "Gjelder $orgNavn – orgnr $orgnr.",
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

    return try {
        runBlocking {
            opprettNySak(
                virksomhetsnummer = orgnr.verdi,
                grupperingsid = inntektsmeldingTypeId.toString(),
                merkelapp = NotifikasjonTekst.MERKELAPP,
                lenke = lenke,
                tittel = NotifikasjonTekst.sakTittel(sykmeldt),
                statusTekst = statusTekst,
                initiellStatus = initiellStatus,
                hardDeleteOm = sakLevetid,
            )
        }
    } catch (e: SakEllerOppgaveDuplikatException) {
        loggWarn("Fant duplikat under opprettelse av sak.", e)
        e.eksisterendeId
    }
}

fun ArbeidsgiverNotifikasjonKlient.ferdigstillSak(
    nyLenke: String,
    forespoerselId: UUID,
) {
    runBlocking {
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
        }.onFailure { error ->
            loggWarnIkkeFunnetEllerThrow("Fant ikke sak under ferdigstilling.", error)
        }
    }
}

fun ArbeidsgiverNotifikasjonKlient.avbrytSak(
    nyLenke: String,
    forespoerselId: UUID,
) {
    runBlocking {
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
        }.onFailure { error ->
            loggWarnIkkeFunnetEllerThrow("Fant ikke sak under avbryting.", error)
        }
    }
}

fun ArbeidsgiverNotifikasjonKlient.opprettOppgave(
    lenke: String,
    forespoerselId: UUID,
    orgnr: Orgnr,
    orgNavn: String,
    skalHaPaaminnelse: Boolean,
    paaminnelseAktivert: Boolean,
    tidMellomOppgaveopprettelseOgPaaminnelse: String,
): String =
    try {
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
                paaminnelse =
                    if (skalHaPaaminnelse && paaminnelseAktivert) {
                        Paaminnelse(
                            tittel = "Påminnelse: ${NotifikasjonTekst.STATUS_TEKST_UNDER_BEHANDLING}",
                            innhold = NotifikasjonTekst.paaminnelseInnhold(orgnr, orgNavn),
                            tidMellomOppgaveopprettelseOgPaaminnelse = tidMellomOppgaveopprettelseOgPaaminnelse,
                        ).also { logger.info("Satte påminnelse for forespørsel $forespoerselId") }
                    } else {
                        null
                    },
            )
        }
    } catch (e: SakEllerOppgaveDuplikatException) {
        loggWarn("Fant duplikat under opprettelse av oppgave.", e)
        e.eksisterendeId
    }

fun ArbeidsgiverNotifikasjonKlient.ferdigstillOppgave(
    lenke: String,
    forespoerselId: UUID,
) {
    runBlocking {
        runCatching {
            oppgaveUtfoertByEksternIdV2(
                eksternId = forespoerselId.toString(),
                merkelapp = NotifikasjonTekst.MERKELAPP,
                nyLenke = lenke,
            )
        }.recoverCatching {
            oppgaveUtfoertByEksternIdV2(
                eksternId = forespoerselId.toString(),
                merkelapp = NotifikasjonTekst.MERKELAPP_GAMMEL,
                nyLenke = lenke,
            )
        }.onFailure { error ->
            loggWarnIkkeFunnetEllerThrow("Fant ikke oppgave under ferdigstilling.", error)
        }
    }
}

fun ArbeidsgiverNotifikasjonKlient.settOppgaveUtgaatt(
    lenke: String,
    forespoerselId: UUID,
) {
    runBlocking {
        runCatching {
            oppgaveUtgaattByEksternId(
                eksternId = forespoerselId.toString(),
                merkelapp = NotifikasjonTekst.MERKELAPP,
                nyLenke = lenke,
            )
        }.recoverCatching {
            oppgaveUtgaattByEksternId(
                eksternId = forespoerselId.toString(),
                merkelapp = NotifikasjonTekst.MERKELAPP_GAMMEL,
                nyLenke = lenke,
            )
        }.onFailure { error ->
            loggWarnIkkeFunnetEllerThrow("Fant ikke oppgave under endring til utgått.", error)
        }
    }
}

// Støtter d-nummer
private fun Fnr.lesFoedselsdato(): String {
    val foersteSiffer = verdi.first().digitToInt()
    return if (foersteSiffer < 4) {
        verdi.take(6)
    } else {
        (foersteSiffer - 4).toString() + verdi.substring(1, 6)
    }
}

private fun loggWarnIkkeFunnetEllerThrow(
    melding: String,
    error: Throwable,
) {
    if (error is SakEllerOppgaveFinnesIkkeException) {
        loggWarn(melding, error)
    } else {
        throw error
    }
}

private fun loggWarn(
    melding: String,
    error: Throwable,
) {
    logger.warn(melding)
    sikkerLogger.warn(melding, error)
}
