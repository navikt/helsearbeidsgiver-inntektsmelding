@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import no.nav.helsearbeidsgiver.pdf.PdfBuilder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun LocalDate.toNorsk(): String {
    return this.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
}

fun LocalDateTime.toNorsk(): String {
    return this.toString()
}

fun Double.toNorsk(): String {
    return this.toString()
}

fun Boolean.toNorsk(): String {
    return if (this) { "Ja" } else { "Nei" }
}

class PdfDokument(val inntektsmeldingDokument: InntektsmeldingDokument) {

    private val b = PdfBuilder()
    private var y = 0
    private val kolonneTo = 420
    private val SPACING = 30

    fun moveCursorBy(y: Int) {
        this.y += y
    }

    fun addHeader() {
        b.addTitle("Kvittering - innsendt inntektsmelding", 0, y)
        moveCursorBy(60)
    }

    fun addAnsatte() {
        val ansatteY = y
        b.addSection("Den ansatte", 0, ansatteY)
        lagLabel(b, 0, ansatteY + 47, "Navn", inntektsmeldingDokument.fulltNavn)
        lagLabel(b, 420, ansatteY + 47, "Personnummer", inntektsmeldingDokument.identitetsnummer)
        y += 120
    }

    fun addArbeidsgiver() {
        val arbeidsgiverY = y
        b.addSection("Arbeidsgiveren", 0, arbeidsgiverY)
        lagLabel(b, 0, arbeidsgiverY + 47, "Virksomhetsnavn", inntektsmeldingDokument.virksomhetNavn)
        lagLabel(b, kolonneTo, arbeidsgiverY + 47, "Organisasjonsnummer for underenhet", inntektsmeldingDokument.orgnrUnderenhet)
    }

    fun export(): ByteArray {
        addHeader()
        addAnsatte()
        addArbeidsgiver()

        val fraværsperiodeY = 360
        b.addLine(0, fraværsperiodeY - 30)

        // ------------------- Fraværsperiode
        b.addSection("Fraværsperiode", 0, fraværsperiodeY)
        b.addBold("Egenmelding", 0, fraværsperiodeY + 35)
        var egenmeldingIndex = 1
        inntektsmeldingDokument.egenmeldingsperioder.forEach {
            lagPeriode(b, 0, fraværsperiodeY + 80 + egenmeldingIndex * 30, it.fom.toNorsk(), it.tom.toNorsk())
            egenmeldingIndex++
        }

        b.addBold("Fravær knyttet til sykmelding", 0, fraværsperiodeY + 145)
        val antalFravær = 3
        val fraværY = fraværsperiodeY + 180
        var i = 1
        inntektsmeldingDokument.fraværsperioder.forEach {
            lagPeriode(b, 0, fraværY + (i - 1) * 60, it.fom.toNorsk(), it.tom.toNorsk())
            i++
        }
        val bestemmendeX = 430
        val bestemmendeY = 420
        lagLabel(b, bestemmendeX, bestemmendeY, "Bestemmende fraværsdag", "Bestemmende fraværsdag angir datoen som sykelønn skal beregnes ut i fra.")
        lagLabel(b, bestemmendeX, bestemmendeY + 60, "Dato", inntektsmeldingDokument.bestemmendeFraværsdag.toNorsk())
        lagLabel(
            b,
            bestemmendeX,
            bestemmendeY + 120,
            "Arbeidsgiverperiode",
            "Arbeidsgivers har ansvar vanligvis for å betale lønn til den sykemeldte under arbeidsgiverperioden"
        )
        inntektsmeldingDokument.arbeidsgiverperioder.forEach {
            lagPeriode(b, bestemmendeX, bestemmendeY + 180, it.fom.toNorsk(), it.tom.toNorsk())
        }
        // ------------------- Bruttoinntekt
        val bruttoInntektY = fraværY + (antalFravær * 60) + 30
        b.addLine(0, bruttoInntektY)
        b.addSection("Bruttoinntekt siste 3 måneder", 0, bruttoInntektY + 30)
        b.addBold("Registrert inntekt (per ${inntektsmeldingDokument.tidspunkt.toLocalDate().toNorsk()})", 0, bruttoInntektY + 60)
        b.addBody(inntektsmeldingDokument.beregnetInntekt.toNorsk() + " kr/måned", 0, bruttoInntektY + 90)
        // ------------------- Refusjon
        val refusjonY = bruttoInntektY + 180
        b.addLine(0, refusjonY - 30)
        b.addSection("Refusjon", 0, refusjonY)
        val full = inntektsmeldingDokument.fullLønnIArbeidsgiverPerioden
        val refusjon = inntektsmeldingDokument.refusjon
        lagLabel(b, 0, refusjonY + 50, "Betaler arbeidsgiver full lønn til arbeidstaker i arbeidsgiverperioden?", full.utbetalerFullLønn.toNorsk())
        lagLabel(b, 0, refusjonY + 100, "Begrunnelse", full.begrunnelse?.name ?: "-")
        lagLabel(b, 0, refusjonY + 150, "Utbetalt under arbeidsgiverperiode", (full.utbetalt?.toNorsk() ?: "-") + " kr")
        lagLabel(b, 0, refusjonY + 200, "Betaler arbeidsgiver lønn under hele eller deler av sykefraværet?", "Ja")
        lagLabel(b, 0, refusjonY + 250, "Refusjonsbeløp pr måned", (refusjon.refusjonPrMnd?.toNorsk() ?: "-") + " kr/måned") // Arbeidsgiver
        lagLabel(b, 0, refusjonY + 300, "Opphører refusjonskravet i perioden", refusjon.refusjonOpphører?.toNorsk() ?: "-")
        lagLabel(b, 0, refusjonY + 350, "Siste dag dere krever refusjon for", refusjon.refusjonOpphører?.toNorsk() ?: "-")
        val naturalytelseY = refusjonY + 450
        val antallNaturalytelser = inntektsmeldingDokument.naturalytelser?.size ?: 0
        b.addLine(0, naturalytelseY - 30)
        b.addSection("Eventuelle naturalytelser", 0, naturalytelseY)
        if (antallNaturalytelser == 0) {
            b.addBody("Nei", 0, naturalytelseY + 30)
        } else {
            b.addBody("Ja", 0, naturalytelseY + 30)
            val kolonne1 = 0
            val kolonne2 = 150
            val kolonne3 = 385
            val tabellY = naturalytelseY + 60
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
        }
        val kvitteringY = naturalytelseY + ((antallNaturalytelser + 5) * 30) // 200
        b.addLine(0, kvitteringY)
        b.addItalics("Kvittering - innsendt inntektsmelding - ${inntektsmeldingDokument.tidspunkt.toNorsk()}", 0, kvitteringY + 30)
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
