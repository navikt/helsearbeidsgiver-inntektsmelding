@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import no.nav.helsearbeidsgiver.pdf.PdfBuilder
import java.math.BigDecimal
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

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

class PdfDokument(val inntektsmeldingDokument: no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument) {

    private val b = PdfBuilder()
    private var y = 0
    private val kolonneTo = 420
    private val SPACING = 30

    fun moveCursorBy(y: Int) {
        this.y += y
    }

    fun addLine() {
        moveCursorBy(20)
        b.addLine(0, y)
        moveCursorBy(20)
    }

    fun addHeader() {
        b.addTitle("Inntektsmelding", 0, y)
        moveCursorBy(60)
    }

    fun addAnsatte() {
        b.addSection("Den ansatte", 0, y)
        lagLabel(b, 0, y + 47, "Navn", inntektsmeldingDokument.fulltNavn)
        lagLabel(b, 420, y + 47, "Personnummer", inntektsmeldingDokument.identitetsnummer)
        moveCursorBy(120)
    }

    fun addArbeidsgiver() {
        b.addSection("Arbeidsgiveren", 0, y)
        lagLabel(b, 0, y + 47, "Virksomhetsnavn", inntektsmeldingDokument.virksomhetNavn)
        lagLabel(b, kolonneTo, y + 47, "Organisasjonsnummer for underenhet", inntektsmeldingDokument.orgnrUnderenhet)
        moveCursorBy(120)
    }

    fun addFraværsperiode() {
        val startY = y
        b.addSection("Fraværsperiode", 0, y)
        // LISTE
        b.addBold("Egenmelding", 0, y + 35)
        var egenmeldingIndex = 1
        var egenmeldingY = y
        inntektsmeldingDokument.egenmeldingsperioder.forEach {
            egenmeldingY = y + 10 + egenmeldingIndex * 60
            lagPeriode(b, 0, egenmeldingY, it.fom.toNorsk(), it.tom.toNorsk())
            egenmeldingIndex++
        }
        // LISTE
        var fraværsY = egenmeldingY + 80
        b.addBold("Fravær knyttet til sykmelding", 0, fraværsY)
        inntektsmeldingDokument.fraværsperioder.forEach {
            fraværsY += 60
            lagPeriode(b, 0, fraværsY - 20, it.fom.toNorsk(), it.tom.toNorsk())
        }
        val bestemmendeX = 430
        val bestemmendeY = 420
        lagLabel(b, bestemmendeX, y + 80, "Bestemmende fraværsdag", "Bestemmende fraværsdag angir datoen som sykelønn skal beregnes ut i fra.")
        lagLabel(b, bestemmendeX, bestemmendeY + 60, "Dato", inntektsmeldingDokument.bestemmendeFraværsdag.toNorsk())
        lagLabel(
            b,
            bestemmendeX,
            bestemmendeY + 120,
            "Arbeidsgiverperiode",
            "Arbeidsgivers har ansvar vanligvis for å betale lønn til den sykemeldte under arbeidsgiverperioden"
        )
        // LISTE
        var arbeidsgiverperiodeY = startY + 200
        inntektsmeldingDokument.arbeidsgiverperioder.forEach {
            arbeidsgiverperiodeY += 60
            lagPeriode(b, bestemmendeX, arbeidsgiverperiodeY, it.fom.toNorsk(), it.tom.toNorsk())
        }
        if (arbeidsgiverperiodeY > fraværsY) {
            moveCursorBy(arbeidsgiverperiodeY - startY + 40)
        } else {
            moveCursorBy(fraværsY - startY + 40)
        }
    }

    fun addInntekt() {
        b.addSection("Bruttoinntekt siste 3 måneder", 0, y)
        b.addBold("Registrert inntekt (per ${inntektsmeldingDokument.tidspunkt.toLocalDate().toNorsk()})", 0, y + 30)
        b.addBody(inntektsmeldingDokument.beregnetInntekt.toNorsk() + " kr/måned", 0, y + 60)
        moveCursorBy(90)
    }

    fun addRefusjon() {
        b.addSection("Refusjon", 0, y)
        val full = inntektsmeldingDokument.fullLønnIArbeidsgiverPerioden
        val refusjon = inntektsmeldingDokument.refusjon
        lagLabel(b, 0, y + 50, "Betaler arbeidsgiver full lønn til arbeidstaker i arbeidsgiverperioden?", full.utbetalerFullLønn.toNorsk())
        lagLabel(b, 0, y + 100, "Begrunnelse", full.begrunnelse?.name ?: "-")
        lagLabel(b, 0, y + 150, "Utbetalt under arbeidsgiverperiode", (full.utbetalt?.toNorsk() ?: "-") + " kr")
        lagLabel(b, 0, y + 200, "Betaler arbeidsgiver lønn under hele eller deler av sykefraværet?", "Ja")
        lagLabel(b, 0, y + 250, "Refusjonsbeløp pr måned", (refusjon.refusjonPrMnd?.toNorsk() ?: "-") + " kr/måned") // Arbeidsgiver
        lagLabel(b, 0, y + 300, "Opphører refusjonskravet i perioden", refusjon.refusjonOpphører?.toNorsk() ?: "-")
        lagLabel(b, 0, y + 350, "Siste dag dere krever refusjon for", refusjon.refusjonOpphører?.toNorsk() ?: "-")
        moveCursorBy(400)
    }

    fun addNaturalytelser() {
        val antallNaturalytelser = inntektsmeldingDokument.naturalytelser?.size ?: 0
        b.addSection("Eventuelle naturalytelser", 0, y)
        if (antallNaturalytelser == 0) {
            b.addBody("Nei", 0, y + 30)
            moveCursorBy(60)
        } else {
            b.addBody("Ja", 0, y + 30)
            val kolonne1 = 0
            val kolonne2 = 150
            val kolonne3 = 385
            val tabellY = y + 60
            b.addBold("Naturalytelser", 0, tabellY)
            b.addBold("Dato naturalytelse bortfaller", kolonne2, tabellY)
            b.addBold("Verdi naturalytelse - kr/måned", kolonne3, tabellY)
            var i = 1
            inntektsmeldingDokument.naturalytelser?.forEach {
                b.addBody("Fri transport", kolonne1, i * 30 + tabellY)
                b.addBody(it.dato.toNorsk(), kolonne2, i * 30 + tabellY)
                b.addBody(it.beløp.toNorsk(), kolonne3, i * 30 + tabellY)
                i++
            }
            moveCursorBy((antallNaturalytelser + 3) * 30)
        }
    }

    fun addTidspunkt() {
        b.addItalics("Innsendt: ${inntektsmeldingDokument.tidspunkt.toNorsk()}", 0, y)
        moveCursorBy(20)
    }

    fun export(): ByteArray {
        addHeader()
        addAnsatte()
        addArbeidsgiver()
        addLine()
        addFraværsperiode()
        addLine()
        addInntekt()
        addLine()
        addRefusjon()
        addLine()
        addNaturalytelser()
        addLine()
        addTidspunkt()
        return b.export()
    }

    fun lagLabel(b: PdfBuilder, x: Int, y: Int, label: String, text: String) {
        b.addBold(label, x, y)
        b.addBody(text, x, y + 20)
    }

    fun lagPeriode(b: PdfBuilder, x: Int = 0, y: Int, fom: String, tom: String) {
        b.addBold("Fra", x, y)
        b.addBold("Til", x + 182, y)
        b.addBody(fom, x, y + 26)
        b.addBody(tom, x + 182, y + 26)
    }
}
