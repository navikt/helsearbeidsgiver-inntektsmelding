package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.BegrunnelseIngenEllerRedusertUtbetalingKode
import java.math.BigDecimal
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

private val begrunnelseRefusjonTilTekst = mapOf(
    BegrunnelseIngenEllerRedusertUtbetalingKode.LOVLIG_FRAVAER to "Lovlig fravær uten lønn",
    BegrunnelseIngenEllerRedusertUtbetalingKode.FRAVAER_UTEN_GYLDIG_GRUNN to "Ikke lovlig fravær",
    BegrunnelseIngenEllerRedusertUtbetalingKode.ARBEID_OPPHOERT to "Arbeidsforholdet er avsluttet",
    BegrunnelseIngenEllerRedusertUtbetalingKode.BESKJED_GITT_FOR_SENT to "Beskjed om fravær gitt for sent eller sykmeldingen er ikke sendt i tide",
    BegrunnelseIngenEllerRedusertUtbetalingKode.MANGLER_OPPTJENING to "Det er ikke fire ukers opptjeningstid",
    BegrunnelseIngenEllerRedusertUtbetalingKode.IKKE_LOENN to "Det er ikke avtale om videre arbeid",
    BegrunnelseIngenEllerRedusertUtbetalingKode.BETVILER_ARBEIDSUFOERHET to "Vi betviler at ansatt er ute av stand til å jobbe",
    BegrunnelseIngenEllerRedusertUtbetalingKode.IKKE_FRAVAER to "Ansatt har ikke hatt fravær fra jobb",
    BegrunnelseIngenEllerRedusertUtbetalingKode.STREIK_ELLER_LOCKOUT to "Streik eller lockout",
    BegrunnelseIngenEllerRedusertUtbetalingKode.PERMITTERING to "Ansatt er helt eller delvis permittert",
    BegrunnelseIngenEllerRedusertUtbetalingKode.FISKER_MED_HYRE to "Ansatt er fisker med hyre på blad B",
    BegrunnelseIngenEllerRedusertUtbetalingKode.SAERREGLER to "Ansatt skal være donor eller skal til kontrollundersøkelse som varer i mer enn 24 timer",
    BegrunnelseIngenEllerRedusertUtbetalingKode.FERIE_ELLER_AVSPASERING to
        "Mindre enn 16 dager siden arbeidet ble gjenopptatt på grunn av lovpålagt ferie eller avspasering",
    BegrunnelseIngenEllerRedusertUtbetalingKode.IKKE_FULL_STILLINGSANDEL to
        "Ansatt har ikke gjenopptatt full stilling etter forrige arbeidsgiverperiode",
    BegrunnelseIngenEllerRedusertUtbetalingKode.TIDLIGERE_VIRKSOMHET to
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

fun BigDecimal.toNorsk(): String {
    val format = DecimalFormat("#,###.##")
    return format.format(this)
}

fun Boolean.toNorsk(): String {
    return if (this) { "Ja" } else { "Nei" }
}

fun BegrunnelseIngenEllerRedusertUtbetalingKode.tekst(): String {
    return begrunnelseRefusjonTilTekst.getOrDefault(this, this.value)
}

private const val MAX_LINJELENGDE = 40

fun String.delOppLangeNavn(): List<String> {
    if (this.length < MAX_LINJELENGDE) {
        return listOf(this)
    }
    if (!this.contains(" ")) {
        return this.chunked(MAX_LINJELENGDE)
    }
    return this.split(" ")
        .fold(listOf<String>()) { result, word ->
            val lastString = result.lastOrNull()
            if (lastString != null && lastString.length + word.length  < MAX_LINJELENGDE) {
                result.dropLastIfNotEmpty() + "$lastString $word"
            } else {
                result.plus(word)
            }
        }
}

fun <T> List<T>.dropLastIfNotEmpty(): List<T> {
    return if (isNotEmpty()) dropLast(1) else this
}

