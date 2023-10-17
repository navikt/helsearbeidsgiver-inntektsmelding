package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import no.nav.helsearbeidsgiver.domene.inntektsmelding.BegrunnelseIngenEllerRedusertUtbetalingKode
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

private val begrunnelseRefusjonTilTekst = mapOf(
    BegrunnelseIngenEllerRedusertUtbetalingKode.LovligFravaer to "Lovlig fravær uten lønn",
    BegrunnelseIngenEllerRedusertUtbetalingKode.FravaerUtenGyldigGrunn to "Ikke lovlig fravær",
    BegrunnelseIngenEllerRedusertUtbetalingKode.ArbeidOpphoert to "Arbeidsforholdet er avsluttet",
    BegrunnelseIngenEllerRedusertUtbetalingKode.BeskjedGittForSent to "Beskjed om fravær gitt for sent eller sykmeldingen er ikke sendt i tide",
    BegrunnelseIngenEllerRedusertUtbetalingKode.ManglerOpptjening to "Det er ikke fire ukers opptjeningstid",
    BegrunnelseIngenEllerRedusertUtbetalingKode.IkkeLoenn to "Det er ikke avtale om videre arbeid",
    BegrunnelseIngenEllerRedusertUtbetalingKode.BetvilerArbeidsufoerhet to "Vi betviler at ansatt er ute av stand til å jobbe",
    BegrunnelseIngenEllerRedusertUtbetalingKode.IkkeFravaer to "Ansatt har ikke hatt fravær fra jobb",
    BegrunnelseIngenEllerRedusertUtbetalingKode.StreikEllerLockout to "Streik eller lockout",
    BegrunnelseIngenEllerRedusertUtbetalingKode.Permittering to "Ansatt er helt eller delvis permittert",
    BegrunnelseIngenEllerRedusertUtbetalingKode.FiskerMedHyre to "Ansatt er fisker med hyre på blad B",
    BegrunnelseIngenEllerRedusertUtbetalingKode.Saerregler to "Ansatt skal være donor eller skal til kontrollundersøkelse som varer i mer enn 24 timer",
    BegrunnelseIngenEllerRedusertUtbetalingKode.FerieEllerAvspasering to
        "Mindre enn 16 dager siden arbeidet ble gjenopptatt på grunn av lovpålagt ferie eller avspasering",
    BegrunnelseIngenEllerRedusertUtbetalingKode.IkkeFullStillingsandel to
        "Ansatt har ikke gjenopptatt full stilling etter forrige arbeidsgiverperiode",
    BegrunnelseIngenEllerRedusertUtbetalingKode.TidligereVirksomhet to
        "Arbeidsgiverperioden er helt eller delvis gjennomført hos tidligere virksomhet"
)

fun LocalDate.toNorsk(): String {
    return this.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
}

fun LocalDateTime.toNorsk(): String {
    return this.format(DateTimeFormatter.ofPattern("dd.MM.yyyy ' kl. ' HH.mm.ss"))
}

fun OffsetDateTime.toNorsk(): String {
    return this.format(DateTimeFormatter.ofPattern("dd.MM.yyyy ' kl. ' HH.mm.ss"))
}

fun Double.toNorsk(): String {
    val format = DecimalFormat("#,###.##")
    return format.format(this)
}

fun Boolean.toNorsk(): String {
    return if (this) { "Ja" } else { "Nei" }
}

fun BegrunnelseIngenEllerRedusertUtbetalingKode.tekst(): String {
    return begrunnelseRefusjonTilTekst.getOrDefault(this, this.name)
}

private const val MAX_LINJELENGDE = 40

fun String.delOppLangeNavn(): List<String> {
    return when {
        this.length < MAX_LINJELENGDE -> listOf(this)
        !this.contains(" ") -> this.chunked(MAX_LINJELENGDE)
        else ->
            this.split(" ")
                .fold(listOf<String>()) { result, word ->
                    val lastString = result.lastOrNull()
                    if (lastString != null && lastString.length + word.length < MAX_LINJELENGDE) {
                        result.dropLastIfNotEmpty() + "$lastString $word"
                    } else {
                        result.plus(word)
                    }
                }
    }
}

fun <T> List<T>.dropLastIfNotEmpty(): List<T> {
    return if (isNotEmpty()) dropLast(1) else this
}

fun String.formaterTelefonnummer(): String {
    if (this.length > 8) {
        val start = this.length - 8
        return this.substring(0, start) + " " + this.substring(start)
    }
    return this
}
