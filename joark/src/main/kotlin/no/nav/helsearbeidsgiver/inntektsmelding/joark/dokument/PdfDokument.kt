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
    private val KOLONNE_EN = 0
    private val KOLONNE_TO = 420
    private val KOLONNE_TRE = 600
    private val NATURALYTELSE_1 = KOLONNE_EN
    private val NATURALYTELSE_2 = KOLONNE_EN + 150
    private val NATURALYTELSE_3 = KOLONNE_EN + 385

    fun moveCursorBy(y: Int) {
        this.y += y
    }

    fun moveCursorByBody(y: Float) {
        this.y += (y * pdf.bodySize).toInt()
    }

    fun addLine() {
        moveCursorBy(pdf.bodySize)
        pdf.addLine(0, y)
        moveCursorBy(pdf.bodySize)
    }

    fun addSection(title: String) {
        pdf.addSection(title, KOLONNE_EN, y)
        moveCursorBy(pdf.sectionSize * 2)
    }

    fun addLabel(title: String, value: String){
        lagLabel(pdf, KOLONNE_TO, y, title, value)
        moveCursorBy(pdf.bodySize * 4)
    }

    fun addLabels(title: String, value: String, title2: String, value2: String){
        lagLabel(pdf, KOLONNE_EN, y, title, value)
        lagLabel(pdf, KOLONNE_TO, y, title2, value2)
        moveCursorBy(pdf.bodySize * 4)
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
        moveCursorBy(pdf.titleSize * 2)
    }

    fun addAnsatte() {
        addSection("Den ansatte")
        addLabels("Navn", dokument.fulltNavn, "Personnummer", dokument.identitetsnummer)
    }

    fun addArbeidsgiver() {
        addSection("Arbeidsgiveren")
        addLabels("Virksomhetsnavn", dokument.virksomhetNavn, "Organisasjonsnummer for underenhet", dokument.orgnrUnderenhet)
    }

    fun addFraværsperiode() { // TODO
        addSection("Fraværsperiode")

        val startY = y

        pdf.addBold("Egenmelding", 0, y)
        moveCursorByBody(2f)

        addPerioder(KOLONNE_EN, dokument.egenmeldingsperioder)

        moveCursorByBody(2f)
        pdf.addBold("Fravær knyttet til sykmelding", KOLONNE_EN, y)
        moveCursorByBody(2f)

        addPerioder(KOLONNE_EN, dokument.fraværsperioder)

        val kolonne1max = y

        y = startY

        lagLabel(pdf, KOLONNE_TO, y, "Bestemmende fraværsdag", "Bestemmende fraværsdag angir datoen som sykelønn skal beregnes ut i fra.")
        moveCursorByBody(4f)
        lagLabel(pdf, KOLONNE_TO, y, "Dato", dokument.bestemmendeFraværsdag.toNorsk())
        moveCursorByBody(4f)
        lagLabel(
            pdf,
            KOLONNE_TO,
            y,
            "Arbeidsgiverperiode",
            "Arbeidsgivers har ansvar vanligvis for å betale lønn til den sykemeldte under arbeidsgiverperioden"
        )

        moveCursorByBody(4f)

        addPerioder(KOLONNE_TO, dokument.arbeidsgiverperioder)

        val kolonne2max = y

        if (kolonne1max > kolonne2max) {
            y = kolonne1max
        } else {
            y = kolonne2max
        }

        moveCursorByBody(2f)
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
            moveCursorBy(pdf.bodySize * 4)
        }
    }

    fun addInntektEndringPerioder(endringsårsak: String, perioder: List<Periode>){
        lagLabel(pdf, KOLONNE_EN, y , "Forklaring for endring", endringsårsak)
        addPerioder(KOLONNE_TO, perioder)
    }

    fun addPermisjon(){
        val perioder = lagPerioder() // TODO
        addInntektEndringPerioder("Permisjon", perioder)
    }

    fun addFerie(){
        val perioder = lagPerioder() // TODO
        addInntektEndringPerioder("Ferie", perioder)
    }

    fun addPermittering(){
        val perioder = lagPerioder() // TODO
        addInntektEndringPerioder("Permittering", perioder)
    }

    fun addTariffendring(){
        val gjelderFra = LocalDate.now() // TODO
        val bleKjent = LocalDate.now() // TODO
        lagLabel(pdf, KOLONNE_EN, y , "Forklaring for endring", "Tariffendring")
        lagLabel(pdf, KOLONNE_TO, y, "Gjelder fra", gjelderFra.toNorsk())
        lagLabel(pdf, KOLONNE_TRE, y, "Ble kjent", bleKjent.toNorsk())
    }

    fun addVarigLonnsendring(){
        val gjelderFra = LocalDate.now() // TODO
        lagLabel(pdf, KOLONNE_EN, y , "Forklaring for endring", "Varig lønnsendring")
        lagLabel(pdf, KOLONNE_TO, y, "Gjelder fra", gjelderFra.toNorsk())
    }

    fun addNyStilling(){
        val gjelderFra = LocalDate.now() // TODO
        lagLabel(pdf, KOLONNE_EN, y , "Forklaring for endring", "Ny stilling")
        lagLabel(pdf, KOLONNE_TO, y, "Gjelder fra", gjelderFra.toNorsk())
    }

    fun addNyStillingsprosent(){
        val gjelderFra = LocalDate.now() // TODO
        lagLabel(pdf, KOLONNE_EN, y , "Forklaring for endring", "Ny stillingsprosent")
        lagLabel(pdf, KOLONNE_TO, y, "Gjelder fra", gjelderFra.toNorsk())
    }

    fun addBonus(){
        lagLabel(pdf, KOLONNE_EN, y , "Forklaring for endring", "Bonus")
    }

    fun addInntekt() {
        addSection("Beregnet månedslønn")
        lagLabel(pdf, KOLONNE_EN, y, "Registrert inntekt (per ${dokument.tidspunkt.toLocalDate().toNorsk()})", dokument.beregnetInntekt.toNorsk() + " kr/måned")
        moveCursorBy(pdf.bodySize*4)
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
        addSection("Refusjon")
        val full = dokument.fullLønnIArbeidsgiverPerioden
        val refusjon = dokument.refusjon
        lagLabel(pdf, KOLONNE_EN, y, "Betaler arbeidsgiver full lønn til arbeidstaker i arbeidsgiverperioden?", full.utbetalerFullLønn.toNorsk())
        moveCursorBy(pdf.bodySize*4)
        lagLabel(pdf, KOLONNE_EN, y, "Begrunnelse", full.begrunnelse?.name ?: "-")
        moveCursorBy(pdf.bodySize*4)
        lagLabel(pdf, KOLONNE_EN, y, "Utbetalt under arbeidsgiverperiode", (full.utbetalt?.toNorsk() ?: "-") + " kr")
        moveCursorBy(pdf.bodySize*4)
        lagLabel(pdf, KOLONNE_EN, y, "Betaler arbeidsgiver lønn under hele eller deler av sykefraværet?", "Ja")
        moveCursorBy(pdf.bodySize*4)
        lagLabel(pdf, KOLONNE_EN, y, "Refusjonsbeløp pr måned", (refusjon.refusjonPrMnd?.toNorsk() ?: "-") + " kr/måned") // Arbeidsgiver
        moveCursorBy(pdf.bodySize*4)
        lagLabel(pdf, KOLONNE_EN, y, "Opphører refusjonskravet i perioden", refusjon.refusjonOpphører?.toNorsk() ?: "-")
        moveCursorBy(pdf.bodySize*4)
        lagLabel(pdf, KOLONNE_EN, y, "Siste dag dere krever refusjon for", refusjon.refusjonOpphører?.toNorsk() ?: "-")
        moveCursorBy(pdf.bodySize*4)
    }

    fun addNaturalytelser() {
        addSection("Bortfall av naturalytelser")
        val antallNaturalytelser = dokument.naturalytelser?.size ?: 0
        if (antallNaturalytelser == 0) {
            pdf.addBody("Nei", KOLONNE_EN, y)
        } else {
            pdf.addBody("Ja", KOLONNE_EN, y)
            moveCursorBy(pdf.bodySize*3)

            pdf.addBold("Naturalytelser", NATURALYTELSE_1, y)
            pdf.addBold("Dato naturalytelse bortfaller", NATURALYTELSE_2, y)
            pdf.addBold("Verdi naturalytelse - kr/måned", NATURALYTELSE_3, y)

            dokument.naturalytelser?.forEach {
                moveCursorBy(pdf.bodySize*2)
                pdf.addBody("Fri transport", NATURALYTELSE_1, y)
                pdf.addBody(it.dato.toNorsk(), NATURALYTELSE_2, y)
                pdf.addBody(it.beløp.toNorsk(), NATURALYTELSE_3, y)
            }
            moveCursorBy(pdf.bodySize*2)
        }
    }

    fun addTidspunkt() {
        pdf.addItalics("Innsendt: ${dokument.tidspunkt.toNorsk()}", KOLONNE_EN, y)
        moveCursorBy(pdf.bodySize)
    }

    fun lagLabel(b: PdfBuilder, x: Int, y: Int, label: String, text: String) {
        b.addBold(label, x, y)
        b.addBody(text, x, y + pdf.bodySize + (pdf.bodySize/2))
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
