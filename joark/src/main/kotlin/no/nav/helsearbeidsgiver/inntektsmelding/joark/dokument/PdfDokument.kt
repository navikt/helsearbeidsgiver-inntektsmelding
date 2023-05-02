@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Bonus
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Ferie
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.NyStilling
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.NyStillingsprosent
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Periode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Permisjon
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Permittering
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Tariffendring
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.VarigLonnsendring
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.ÅrsakInnsending
import no.nav.helsearbeidsgiver.pdf.PdfBuilder

class PdfDokument(val dokument: InntektsmeldingDokument) {

    private val pdf = PdfBuilder(bodySize = 20) // Setter skriftstørrelsen på labels og text
    private var y = 0
    private val KOLONNE_EN = 0
    private val KOLONNE_TO = 420
    private val NATURALYTELSE_1 = KOLONNE_EN
    private val NATURALYTELSE_2 = KOLONNE_EN + 400
    private val NATURALYTELSE_3 = KOLONNE_EN + 700
    private val BOLD_LABELS = false // Bestemmer om navnet på label eller verdien skal stå i bold

    fun export(): ByteArray {
        addHeader()
        addAnsatt()
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

    fun moveCursorBy(y: Int) {
        this.y += y
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

    fun addLabel(label: String, text: String? = null, x: Int = KOLONNE_EN, newY: Int = y, linefeed: Boolean = true) {
        pdf.addText(label, x, newY, BOLD_LABELS)
        if (text != null) {
            pdf.addText(text, x, newY + pdf.bodySize + (pdf.bodySize / 2), !BOLD_LABELS)
        }
        if (linefeed) {
            moveCursorBy(if (text == null) { pdf.bodySize * 2 } else { pdf.bodySize * 4 })
        }
    }

    fun addText(text: String, x1: Int = KOLONNE_EN, y2: Int = y, linefeed: Boolean = true) {
        pdf.addText(text, x1, y2, !BOLD_LABELS)
        if (linefeed) {
            moveCursorBy(pdf.bodySize * 2)
        }
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

    fun addAnsatt() {
        addSection("Den ansatte")
        addLabel("Navn", dokument.fulltNavn, linefeed = false)
        addLabel("Personnummer", dokument.identitetsnummer, KOLONNE_TO)
    }

    fun addArbeidsgiver() {
        addSection("Arbeidsgiveren")
        addLabel("Virksomhetsnavn", dokument.virksomhetNavn, linefeed = false)
        addLabel("Organisasjonsnummer for underenhet", dokument.orgnrUnderenhet, KOLONNE_TO)
    }

    fun addFraværsperiode() {
        addSection("Fraværsperiode")
        val startY = y
        addLabel("Egenmelding")
        addPerioder(KOLONNE_EN, dokument.egenmeldingsperioder)
        addLabel("Fravær knyttet til sykmelding")
        addPerioder(KOLONNE_EN, dokument.fraværsperioder)
        val kolonne1max = y // Husk maks høyden på venstre side
        y = startY
        addLabel("Bestemmende fraværsdag", "Bestemmende fraværsdag angir datoen som sykelønn skal beregnes ut i fra.", KOLONNE_TO)
        addLabel("Dato", dokument.bestemmendeFraværsdag.toNorsk(), KOLONNE_TO)
        addLabel(
            "Arbeidsgiverperiode",
            "Arbeidsgivers har ansvar vanligvis for å betale lønn",
            KOLONNE_TO
        )
        moveCursorBy(-pdf.bodySize)
        addText("til den sykemeldte under arbeidsgiverperioden", KOLONNE_TO)
        moveCursorBy(pdf.bodySize)
        addPerioder(KOLONNE_TO, dokument.arbeidsgiverperioder)
        val kolonne2max = y // Husk maks høyden på høyre side
        y = if (kolonne1max > kolonne2max) { kolonne1max } else { kolonne2max } // Plasser cursor etter høyeste side
        moveCursorBy(pdf.bodySize * 2)
    }

    fun addPerioder(x: Int, perioder: List<Periode>) {
        perioder.forEach {
            addLabel("Fra", it.fom.toNorsk(), x, linefeed = false)
            addLabel("Til", it.tom.toNorsk(), x + 200)
        }
    }

    fun addInntekt() {
        addSection("Beregnet månedslønn")
        addLabel("Registrert inntekt (per ${dokument.tidspunkt.toLocalDate().toNorsk()})", dokument.beregnetInntekt.toNorsk() + " kr/måned")
        val endringsårsak = dokument.inntekt?.endringÅrsak
        when (endringsårsak) {
            is Permisjon -> addPermisjon(endringsårsak)
            is Ferie -> addFerie(endringsårsak)
            is Permittering -> addPermittering(endringsårsak)
            is Tariffendring -> addTariffendring(endringsårsak)
            is VarigLonnsendring -> addVarigLonnsendring(endringsårsak)
            is NyStilling -> addNyStilling(endringsårsak)
            is NyStillingsprosent -> addNyStillingsprosent(endringsårsak)
            is Bonus -> addBonus(endringsårsak)
            else -> {}
        }
    }

    fun addInntektEndringPerioder(endringsårsak: String, perioder: List<Periode>) {
        addLabel("Forklaring for endring", endringsårsak, linefeed = false)
        addPerioder(KOLONNE_TO, perioder)
    }

    fun addPermisjon(permisjon: Permisjon) {
        addInntektEndringPerioder("Permisjon", permisjon.liste)
    }

    fun addFerie(ferie: Ferie) {
        addInntektEndringPerioder("Ferie", ferie.liste)
    }

    fun addPermittering(permittering: Permittering) {
        addInntektEndringPerioder("Permittering", permittering.liste)
    }

    fun addTariffendring(tariffendring: Tariffendring) {
        addLabel("Forklaring for endring", "Tariffendring")
        addLabel("Gjelder fra", tariffendring.gjelderFra.toNorsk())
        addLabel("Ble kjent", tariffendring.bleKjent.toNorsk())
    }

    fun addVarigLonnsendring(varigLonnsendring: VarigLonnsendring) {
        addLabel("Forklaring for endring", "Varig lønnsendring")
        addLabel("Gjelder fra", varigLonnsendring.gjelderFra.toNorsk(), KOLONNE_TO)
    }

    fun addNyStilling(nyStilling: NyStilling) {
        addLabel("Forklaring for endring", "Ny stilling")
        addLabel("Gjelder fra", nyStilling.gjelderFra.toNorsk(), KOLONNE_TO)
    }

    fun addNyStillingsprosent(nyStillingsprosent: NyStillingsprosent) {
        addLabel("Forklaring for endring", "Ny stillingsprosent")
        addLabel("Gjelder fra", nyStillingsprosent.gjelderFra.toNorsk(), KOLONNE_TO)
    }

    fun addBonus(bonus: Bonus) {
        addLabel("Forklaring for endring", "Bonus")
    }

    fun addRefusjon() {
        addSection("Refusjon")

        val full = dokument.fullLønnIArbeidsgiverPerioden
        addLabel("Betaler arbeidsgiver full lønn til arbeidstaker i arbeidsgiverperioden?", full.utbetalerFullLønn.toNorsk())
        if (full.utbetalerFullLønn) {
            addLabel("Begrunnelse", full.begrunnelse?.name ?: "-")
            addLabel("Utbetalt under arbeidsgiverperiode", (full.utbetalt?.toNorsk() ?: "-") + " kr")
        }

        val refusjon = dokument.refusjon
        addLabel("Betaler arbeidsgiver lønn under hele eller deler av sykefraværet?", refusjon.utbetalerHeleEllerDeler.toNorsk())

        if (dokument.refusjon.utbetalerHeleEllerDeler) {
            addLabel("Refusjonsbeløp pr måned", (refusjon.refusjonPrMnd?.toNorsk() ?: "-") + " kr/måned") // Arbeidsgiver
            addLabel("Opphører refusjonskravet i perioden", refusjon.refusjonOpphører?.toNorsk() ?: "-")
            addLabel("Siste dag dere krever refusjon for", refusjon.refusjonOpphører?.toNorsk() ?: "-")
        }

        val endringer = dokument.refusjon.refusjonEndringer ?: emptyList()
        addLabel("Endringer i refusjon i perioden", (!endringer.isEmpty()).toNorsk())
        if (endringer.isEmpty() == false) {
            endringer.forEach {
                addLabel("Beløp", it.beløp?.toNorsk() ?: "-", KOLONNE_EN, linefeed = false)
                addLabel("Dato", it.dato?.toNorsk() ?: "-", KOLONNE_TO)
            }
        }
    }

    fun addNaturalytelser() {
        addSection("Bortfall av naturalytelser")
        val antallNaturalytelser = dokument.naturalytelser?.size ?: 0
        if (antallNaturalytelser == 0) {
            addLabel("Nei")
        } else {
            addLabel("Naturalytelser", x = NATURALYTELSE_1, linefeed = false)
            addLabel("Dato naturalytelse bortfaller", x = NATURALYTELSE_2, linefeed = false)
            addLabel("Verdi naturalytelse - kr/måned", x = NATURALYTELSE_3)
            dokument.naturalytelser?.forEach {
                addText(it.naturalytelse.value, NATURALYTELSE_1, linefeed = false)
                addText(it.dato.toNorsk(), NATURALYTELSE_2, linefeed = false)
                addText(it.beløp.toNorsk(), NATURALYTELSE_3)
            }
            moveCursorBy(pdf.bodySize * 2)
        }
    }

    fun addTidspunkt() {
        pdf.addItalics("Innsendt: ${dokument.tidspunkt.toNorsk()}", KOLONNE_EN, y)
        moveCursorBy(pdf.bodySize)
    }
}
