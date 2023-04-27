@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Periode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.ÅrsakInnsending
import no.nav.helsearbeidsgiver.pdf.PdfBuilder
import java.time.LocalDate

class PdfDokument(val dokument: InntektsmeldingDokument) {

    private val pdf = PdfBuilder()
    private var y = 0
    private val kolonneTo = 420

    fun moveCursorBy(y: Int) {
        this.y += y
    }

    fun addLine() {
        moveCursorBy(20)
        pdf.addLine(0, y)
        moveCursorBy(20)
    }

    fun addHeader() {
        pdf.addTitle(
            title = when (dokument.årsakInnsending) {
                ÅrsakInnsending.ENDRING -> "Inntektsmelding for sykepenger - endring"
                else -> "Inntektsmelding for sykepenger"
            },
            x = 0,
            y = y
        )
        moveCursorBy(60)
    }

    fun addAnsatte() {
        pdf.addSection("Den ansatte", 0, y)
        lagLabel(pdf, 0, y + 47, "Navn", dokument.fulltNavn)
        lagLabel(pdf, 420, y + 47, "Personnummer", dokument.identitetsnummer)
        moveCursorBy(120)
    }

    fun addArbeidsgiver() {
        pdf.addSection("Arbeidsgiveren", 0, y)
        lagLabel(pdf, 0, y + 47, "Virksomhetsnavn", dokument.virksomhetNavn)
        lagLabel(pdf, kolonneTo, y + 47, "Organisasjonsnummer for underenhet", dokument.orgnrUnderenhet)
        moveCursorBy(120)
    }

    fun addFraværsperiode() {
        val startY = y
        pdf.addSection("Fraværsperiode", 0, y)
        // LISTE
        pdf.addBold("Egenmelding", 0, y + 35)
        var egenmeldingIndex = 1
        var egenmeldingY = y
        dokument.egenmeldingsperioder.forEach {
            egenmeldingY = y + 10 + egenmeldingIndex * 60
            lagPeriode(pdf, 0, egenmeldingY, it.fom.toNorsk(), it.tom.toNorsk())
            egenmeldingIndex++
        }
        // LISTE
        var fraværsY = egenmeldingY + 80
        pdf.addBold("Fravær knyttet til sykmelding", 0, fraværsY)
        dokument.fraværsperioder.forEach {
            fraværsY += 60
            lagPeriode(pdf, 0, fraværsY - 20, it.fom.toNorsk(), it.tom.toNorsk())
        }
        val bestemmendeX = 430
        val bestemmendeY = 420
        lagLabel(pdf, bestemmendeX, y + 80, "Bestemmende fraværsdag", "Bestemmende fraværsdag angir datoen som sykelønn skal beregnes ut i fra.")
        lagLabel(pdf, bestemmendeX, bestemmendeY + 60, "Dato", dokument.bestemmendeFraværsdag.toNorsk())
        lagLabel(
            pdf,
            bestemmendeX,
            bestemmendeY + 120,
            "Arbeidsgiverperiode",
            "Arbeidsgivers har ansvar vanligvis for å betale lønn til den sykemeldte under arbeidsgiverperioden"
        )
        // LISTE
        var arbeidsgiverperiodeY = startY + 200
        dokument.arbeidsgiverperioder.forEach {
            arbeidsgiverperiodeY += 60
            lagPeriode(pdf, bestemmendeX, arbeidsgiverperiodeY, it.fom.toNorsk(), it.tom.toNorsk())
        }
        if (arbeidsgiverperiodeY > fraværsY) {
            moveCursorBy(arbeidsgiverperiodeY - startY + 40)
        } else {
            moveCursorBy(fraværsY - startY + 40)
        }
    }

    fun lagPerioder(): List<Periode> {
        val dato = LocalDate.now()
        return listOf(
            Periode(dato, dato.plusDays(20)),
            Periode(dato.plusDays(10), dato.plusDays(20)),
            Periode(dato.plusDays(20), dato.plusDays(30))
        )
    }

    fun addPerioder(x: Int, perioder: List<Periode>){
        perioder.forEach {
            lagLabel(pdf, x, y, "Fra", it.fom.toNorsk())
            lagLabel(pdf, x + 200, y, "Til", it.tom.toNorsk())
            moveCursorBy(50)
        }
    }

    fun addInntektEndringPerioder(endringsårsak: String, perioder: List<Periode>){
        lagLabel(pdf, 0, y , "Forklaring for endring", endringsårsak)
        perioder.forEach {
            lagLabel(pdf, 400, y, "Fra", it.fom.toNorsk())
            lagLabel(pdf, 600, y, "Til", it.tom.toNorsk())
            moveCursorBy(50)
        }
    }

    fun addPermisjon(){
        addInntektEndringPerioder("Permisjon", lagPerioder())
    }

    fun addFerie(){
        addInntektEndringPerioder("Ferie", lagPerioder())
    }

    fun addPermittering(){
        addInntektEndringPerioder("Permittering", lagPerioder())
    }

    fun addTariffendring(){
        lagLabel(pdf, 0, y , "Forklaring for endring", "Tariffendring")
        lagLabel(pdf, 400, y, "Gjelder fra", LocalDate.now().toNorsk())
        lagLabel(pdf, 600, y, "Ble kjent", LocalDate.now().toNorsk())
    }

    fun addVarigLonnsendring(){
        lagLabel(pdf, 0, y , "Forklaring for endring", "Varig lønnsendring")
        lagLabel(pdf, 400, y, "Gjelder fra", LocalDate.now().toNorsk())
    }

    fun addNyStilling(){
        lagLabel(pdf, 0, y , "Forklaring for endring", "Ny stilling")
        lagLabel(pdf, 400, y, "Gjelder fra", LocalDate.now().toNorsk())
    }

    fun addNyStillingsprosent(){
        lagLabel(pdf, 0, y , "Forklaring for endring", "Ny stillingsprosent")
        lagLabel(pdf, 400, y, "Gjelder fra", LocalDate.now().toNorsk())
    }

    fun addBonus(){
        lagLabel(pdf, 0, y , "Forklaring for endring", "Bonus")
    }

    fun addInntekt() {
        pdf.addSection("Beregnet månedslønn", 0, y)
        lagLabel(pdf, 0, y+30, "Registrert inntekt (per ${dokument.tidspunkt.toLocalDate().toNorsk()})", dokument.beregnetInntekt.toNorsk() + " kr/måned")
        moveCursorBy(90)
        val endringType = 1
        when (endringType) {
            1 -> addPermisjon()
            8 -> addFerie()
            2 -> addPermittering()
            3 -> addTariffendring()
            4 -> addVarigLonnsendring()
            5 -> addNyStilling()
            6 -> addNyStillingsprosent()
            7 -> addBonus()
        }
    }

    fun addRefusjon() {
        pdf.addSection("Refusjon", 0, y)
        val full = dokument.fullLønnIArbeidsgiverPerioden
        val refusjon = dokument.refusjon
        lagLabel(pdf, 0, y + 50, "Betaler arbeidsgiver full lønn til arbeidstaker i arbeidsgiverperioden?", full.utbetalerFullLønn.toNorsk())
        lagLabel(pdf, 0, y + 100, "Begrunnelse", full.begrunnelse?.name ?: "-")
        lagLabel(pdf, 0, y + 150, "Utbetalt under arbeidsgiverperiode", (full.utbetalt?.toNorsk() ?: "-") + " kr")
        lagLabel(pdf, 0, y + 200, "Betaler arbeidsgiver lønn under hele eller deler av sykefraværet?", "Ja")
        lagLabel(pdf, 0, y + 250, "Refusjonsbeløp pr måned", (refusjon.refusjonPrMnd?.toNorsk() ?: "-") + " kr/måned") // Arbeidsgiver
        lagLabel(pdf, 0, y + 300, "Opphører refusjonskravet i perioden", refusjon.refusjonOpphører?.toNorsk() ?: "-")
        lagLabel(pdf, 0, y + 350, "Siste dag dere krever refusjon for", refusjon.refusjonOpphører?.toNorsk() ?: "-")
        moveCursorBy(400)
    }

    fun addNaturalytelser() {
        val antallNaturalytelser = dokument.naturalytelser?.size ?: 0
        pdf.addSection("Bortfall av naturalytelser", 0, y)
        if (antallNaturalytelser == 0) {
            pdf.addBody("Nei", 0, y + 30)
            moveCursorBy(60)
        } else {
            pdf.addBody("Ja", 0, y + 30)
            val kolonne1 = 0
            val kolonne2 = 150
            val kolonne3 = 385
            val tabellY = y + 60
            pdf.addBold("Naturalytelser", 0, tabellY)
            pdf.addBold("Dato naturalytelse bortfaller", kolonne2, tabellY)
            pdf.addBold("Verdi naturalytelse - kr/måned", kolonne3, tabellY)
            var i = 1
            dokument.naturalytelser?.forEach {
                pdf.addBody("Fri transport", kolonne1, i * 30 + tabellY)
                pdf.addBody(it.dato.toNorsk(), kolonne2, i * 30 + tabellY)
                pdf.addBody(it.beløp.toNorsk(), kolonne3, i * 30 + tabellY)
                i++
            }
            moveCursorBy((antallNaturalytelser + 3) * 30)
        }
    }

    fun addTidspunkt() {
        pdf.addItalics("Innsendt: ${dokument.tidspunkt.toNorsk()}", 0, y)
        moveCursorBy(20)
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
        return pdf.export()
    }

}
