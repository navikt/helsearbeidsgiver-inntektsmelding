package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.RedusertLoennIAgp
import java.text.DecimalFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

fun OffsetDateTime.tilNorskFormat(): String = this.format(DateTimeFormatter.ofPattern("dd.MM.yyyy ' kl. ' HH.mm.ss"))

fun Double.tilNorskFormat(): String {
    val format = DecimalFormat("#,###.##")
    return format.format(this)
}

fun Boolean.tilNorskFormat(): String =
    if (this) {
        "Ja"
    } else {
        "Nei"
    }

fun RedusertLoennIAgp.Begrunnelse.tilTekst(): String =
    when (this) {
        RedusertLoennIAgp.Begrunnelse.ArbeidOpphoert -> "Arbeidsforholdet er avsluttet"
        RedusertLoennIAgp.Begrunnelse.BeskjedGittForSent -> "Beskjed om fravær gitt for sent eller sykmeldingen er ikke sendt i tide"
        RedusertLoennIAgp.Begrunnelse.BetvilerArbeidsufoerhet -> "Vi betviler at ansatt er ute av stand til å jobbe"
        RedusertLoennIAgp.Begrunnelse.FerieEllerAvspasering ->
            "Mindre enn 16 dager siden arbeidet ble gjenopptatt på grunn av lovpålagt ferie eller avspasering"
        RedusertLoennIAgp.Begrunnelse.FiskerMedHyre -> "Ansatt er fisker med hyre på blad B"
        RedusertLoennIAgp.Begrunnelse.FravaerUtenGyldigGrunn -> "Ikke lovlig fravær"
        RedusertLoennIAgp.Begrunnelse.IkkeFravaer -> "Ansatt har ikke hatt fravær fra jobb"
        RedusertLoennIAgp.Begrunnelse.IkkeFullStillingsandel -> "Ansatt har ikke gjenopptatt full stilling etter forrige arbeidsgiverperiode"
        RedusertLoennIAgp.Begrunnelse.IkkeLoenn -> "Det er ikke avtale om videre arbeid"
        RedusertLoennIAgp.Begrunnelse.LovligFravaer -> "Lovlig fravær uten lønn"
        RedusertLoennIAgp.Begrunnelse.ManglerOpptjening -> "Det er ikke fire ukers opptjeningstid"
        RedusertLoennIAgp.Begrunnelse.Permittering -> "Ansatt er helt eller delvis permittert"
        RedusertLoennIAgp.Begrunnelse.Saerregler -> "Ansatt skal være donor eller skal til kontrollundersøkelse som varer i mer enn 24 timer"
        RedusertLoennIAgp.Begrunnelse.StreikEllerLockout -> "Streik eller lockout"
        RedusertLoennIAgp.Begrunnelse.TidligereVirksomhet -> "Arbeidsgiverperioden er helt eller delvis gjennomført hos tidligere virksomhet"
    }

private const val MAX_LINJELENGDE = 36

fun String.delOppLangeNavn(): List<String> =
    when {
        this.length < MAX_LINJELENGDE -> listOf(this)
        !this.contains(" ") -> this.chunked(MAX_LINJELENGDE)
        else ->
            this
                .split(" ")
                .fold(emptyList()) { result, word ->
                    val lastString = result.lastOrNull()
                    if (lastString != null && lastString.length + word.length < MAX_LINJELENGDE) {
                        result.dropLastIfNotEmpty() + "$lastString $word"
                    } else {
                        result.plus(word)
                    }
                }
    }

fun <T> List<T>.dropLastIfNotEmpty(): List<T> = if (isNotEmpty()) dropLast(1) else this

fun String.formaterTelefonnummer(): String {
    if (this.length > 8) {
        val start = this.length - 8
        return this.substring(0, start) + " " + this.substring(start)
    }
    return this
}
