@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Bonus
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Feilregistrert
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Ferie
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Ferietrekk
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.NyStilling
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.NyStillingsprosent
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Nyansatt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Permisjon
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Permittering
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Sykefravaer
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Tariffendring
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.VarigLoennsendring
import no.nav.helsearbeidsgiver.felles.utils.tilNorskFormat

private const val FORKLARING_ENDRING = "Forklaring for endring"

class PdfDokument(
    val inntektsmelding: Inntektsmelding,
) {
    private val pdf = PdfBuilder(bodySize = 20, topText = "Innsendt: ${inntektsmelding.mottatt.tilNorskFormat()}") // Setter skriftstørrelsen på labels og text
    private var y = 0
    private val kolonneEn = 0
    private val kolonneTo = 420
    private val naturalytelse1 = kolonneEn
    private val naturalytelse2 = kolonneEn + 400
    private val naturalytelse3 = kolonneEn + 700

    fun export(): ByteArray {
        addHeader()
        addAnsatt()
        addArbeidsgiver()
        addLine()
        addFravaersperiode()
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

    private fun moveCursorBy(y: Int) {
        this.y += y
    }

    private fun moveCursorTo(y: Int) {
        this.y = y
    }

    private fun addLine() {
        moveCursorBy(pdf.bodySize)
        pdf.addLine(0, y)
        moveCursorBy(pdf.bodySize)
    }

    private fun addSection(title: String) {
        pdf.addSection(title, kolonneEn, y)
        moveCursorBy(pdf.sectionSize * 2)
    }

    private fun addLabel(
        label: String,
        text: String? = null,
        x: Int = kolonneEn,
        newY: Int = y,
        linefeed: Boolean = true,
        splitLines: Boolean = false,
    ) {
        pdf.addText(label, x, newY, bold = false)
        if (splitLines && text != null) {
            text.delOppLangeNavn().forEach {
                pdf.addText(it, x, y + pdf.bodySize + (pdf.bodySize / 2), bold = true)
                moveCursorBy(pdf.bodySize * 2)
            }
        } else {
            if (text != null) {
                pdf.addText(text, x, newY + pdf.bodySize + (pdf.bodySize / 2), bold = true)
            }
            if (linefeed) {
                moveCursorBy(
                    if (text == null) {
                        pdf.bodySize * 2
                    } else {
                        pdf.bodySize * 4
                    },
                )
            }
        }
    }

    private fun addText(
        text: String,
        x1: Int = kolonneEn,
        y2: Int = y,
        linefeed: Boolean = true,
    ) {
        pdf.addText(text, x1, y2, bold = true)
        if (linefeed) {
            moveCursorBy(pdf.bodySize * 2)
        }
    }

    private fun addHeader() {
        pdf.addTitle(
            title =
                when (inntektsmelding.aarsakInnsending) {
                    AarsakInnsending.Ny -> "Inntektsmelding for sykepenger"
                    AarsakInnsending.Endring -> "Inntektsmelding for sykepenger - endring"
                },
            x = 0,
            y = y,
        )
        moveCursorBy(pdf.titleSize * 2)
    }

    private fun addAnsatt() {
        addSection("Den ansatte")
        val topY = y
        addLabel("Navn", inntektsmelding.sykmeldt.navn, linefeed = false, splitLines = true)
        val afterY = y
        moveCursorTo(topY)
        addLabel("Personnummer", inntektsmelding.sykmeldt.fnr.verdi, kolonneTo)
        moveCursorTo(afterY)
        moveCursorBy(pdf.bodySize * 2)
    }

    private fun addArbeidsgiver() {
        addSection("Arbeidsgiveren")
        val topY = y
        addLabel("Virksomhetsnavn", inntektsmelding.avsender.orgNavn, linefeed = false, splitLines = true)
        val afterY = y
        moveCursorTo(topY)
        addLabel("Organisasjonsnummer for underenhet", inntektsmelding.avsender.orgnr.verdi, kolonneTo)
        moveCursorTo(afterY)
        moveCursorBy(pdf.bodySize * 2)
        val newY = y
        addLabel(
            label = "Innsender",
            text = inntektsmelding.avsender.navn,
            linefeed = false,
            splitLines = true,
        )
        moveCursorTo(newY)
        addLabel("Telefonnummer", inntektsmelding.avsender.tlf.formaterTelefonnummer(), kolonneTo)
    }

    private fun addFravaersperiode() {
        addSection("Fraværsperiode")
        val seksjonStartY = y // Husk når denne seksjonen starter i y-aksen

        // --- Kolonnen til venstre -------------------------------------------------
        addLabel("Bestemmende fraværsdag (skjæringstidpunkt)", inntektsmelding.inntekt?.inntektsdato?.tilNorskFormat(), kolonneEn)
        addLabel("Arbeidsgiverperiode", x = kolonneEn)
        addPerioder(kolonneEn, inntektsmelding.agp?.perioder.orEmpty())

        // Husk maks høyden på venstre side
        val kolonneVenstreMaxY = y

        // --- Kolonnen til høyre ---------------------------------------------------
        moveCursorTo(seksjonStartY) // Gjenopprett y-aksen fra tidligere
        addLabel("Egenmelding", x = kolonneTo)
        addPerioder(kolonneTo, inntektsmelding.agp?.egenmeldinger.orEmpty())
        addLabel("Sykemeldingsperioder", x = kolonneTo)
        addPerioder(kolonneTo, inntektsmelding.sykmeldingsperioder)

        // Husk maks høyden på høyre side
        val kolonneHoeyreMaxY = y

        // --- Finn ut hvilken kolonne som ble høyest -------------------------------
        moveCursorTo(maxOf(kolonneVenstreMaxY, kolonneHoeyreMaxY))
        moveCursorBy(pdf.bodySize * 2)
    }

    private fun addPerioder(
        x: Int,
        perioder: List<Periode>,
    ) {
        perioder.forEach {
            addLabel("Fra", it.fom.tilNorskFormat(), x, linefeed = false)
            addLabel("Til", it.tom.tilNorskFormat(), x + 200)
        }
    }

    private fun addInntekt() {
        addSection("Beregnet månedslønn")
        addLabel(
            "Registrert inntekt (per ${inntektsmelding.inntekt?.inntektsdato?.tilNorskFormat()})",
            "${inntektsmelding.inntekt?.beloep?.tilNorskFormat()} kr/måned",
        )
        val endringAarsak = inntektsmelding.inntekt?.endringAarsak
        when (endringAarsak) {
            null -> return // trenger ikke sende inn årsak...
            is Bonus -> addBonus()
            is Feilregistrert -> addFeilregistrert()
            is Ferie -> addFerie(endringAarsak)
            is Ferietrekk -> addFerietrekk()
            is NyStilling -> addNyStilling(endringAarsak)
            is NyStillingsprosent -> addNyStillingsprosent(endringAarsak)
            is Nyansatt -> addNyAnsatt()
            is Permisjon -> addPermisjon(endringAarsak)
            is Permittering -> addPermittering(endringAarsak)
            is Sykefravaer -> addSykefravaer(endringAarsak)
            is Tariffendring -> addTariffendring(endringAarsak)
            is VarigLoennsendring -> addVarigLonnsendring(endringAarsak)
        }
    }

    private fun addInntektEndringPerioder(
        endringAarsak: String,
        perioder: List<Periode>,
    ) {
        addLabel(FORKLARING_ENDRING, endringAarsak, linefeed = false)
        addPerioder(kolonneTo, perioder)
    }

    private fun addPermisjon(permisjon: Permisjon) {
        addInntektEndringPerioder("Permisjon", permisjon.permisjoner)
    }

    private fun addFerie(ferie: Ferie) {
        addInntektEndringPerioder("Ferie", ferie.ferier)
    }

    private fun addPermittering(permittering: Permittering) {
        addInntektEndringPerioder("Permittering", permittering.permitteringer)
    }

    private fun addSykefravaer(sykefravaer: Sykefravaer) {
        addInntektEndringPerioder("Sykefravær", sykefravaer.sykefravaer)
    }

    private fun addNyAnsatt() {
        addLabel(FORKLARING_ENDRING, "Nyansatt")
    }

    private fun addFeilregistrert() {
        addLabel(FORKLARING_ENDRING, "Mangelfull eller uriktig rapportering til A-ordningen")
    }

    private fun addTariffendring(tariffendring: Tariffendring) {
        addLabel(FORKLARING_ENDRING, "Tariffendring")
        addLabel("Gjelder fra", tariffendring.gjelderFra.tilNorskFormat(), linefeed = false)
        addLabel("Ble kjent", tariffendring.bleKjent.tilNorskFormat(), kolonneTo)
    }

    private fun addVarigLonnsendring(varigLoennsendring: VarigLoennsendring) {
        addLabel(FORKLARING_ENDRING, "Varig lønnsendring")
        addLabel("Gjelder fra", varigLoennsendring.gjelderFra.tilNorskFormat())
    }

    private fun addNyStilling(nyStilling: NyStilling) {
        addLabel(FORKLARING_ENDRING, "Ny stilling", linefeed = false)
        addLabel("Gjelder fra", nyStilling.gjelderFra.tilNorskFormat(), kolonneTo)
    }

    private fun addNyStillingsprosent(nyStillingsprosent: NyStillingsprosent) {
        addLabel(FORKLARING_ENDRING, "Ny stillingsprosent", linefeed = false)
        addLabel("Gjelder fra", nyStillingsprosent.gjelderFra.tilNorskFormat(), kolonneTo)
    }

    private fun addBonus() {
        addLabel(FORKLARING_ENDRING, "Bonus")
        // addLabel("Estimert årlig bonus", årligBonus.tilNorskFormat())
        // addLabel("Dato siste bonus", datoBonus.tilNorskFormat())
    }

    private fun addFerietrekk() {
        addLabel(FORKLARING_ENDRING, "Ferietrekk/Utbetaling av feriepenger")
    }

    private fun addRefusjon() {
        val redusertLoennIAgp = inntektsmelding.agp?.redusertLoennIAgp
        val refusjon = inntektsmelding.refusjon

        addSection("Refusjon")

        addLabel("Betaler arbeidsgiver full lønn til arbeidstaker i arbeidsgiverperioden?", (redusertLoennIAgp == null).tilNorskFormat())
        if (redusertLoennIAgp != null) {
            // Redusert lønn i AGP - to ekstra spørsmål
            addLabel("Begrunnelse", redusertLoennIAgp.begrunnelse.tilTekst())
            addLabel("Utbetalt under arbeidsgiverperiode", redusertLoennIAgp.beloep.tilNorskFormat() + " kr")
        }

        addLabel("Betaler arbeidsgiver lønn under hele eller deler av sykefraværet?", (refusjon != null).tilNorskFormat())
        if (refusjon != null) {
            // Ja - tre ekstra spørsmål
            addLabel("Refusjonsbeløp pr måned", refusjon.beloepPerMaaned.tilNorskFormat() + " kr/måned")

            val sluttdato = refusjon.sluttdato
            addLabel("Opphører refusjonskravet i perioden", (sluttdato != null).tilNorskFormat())
            if (sluttdato != null) {
                addLabel("Siste dag dere krever refusjon for", sluttdato.tilNorskFormat())
            }

            addLabel("Endringer i refusjon i perioden", refusjon.endringer.isNotEmpty().tilNorskFormat())
            refusjon.endringer.forEach {
                addLabel("Beløp", it.beloep.tilNorskFormat(), kolonneEn, linefeed = false)
                addLabel("Dato", it.startdato.tilNorskFormat(), kolonneTo)
            }
        }
    }

    private fun addNaturalytelser() {
        addSection("Bortfall av naturalytelser")
        if (inntektsmelding.inntekt?.naturalytelser.isNullOrEmpty()) {
            addLabel("Nei")
        } else {
            addLabel("Naturalytelser", x = naturalytelse1, linefeed = false)
            addLabel("Dato naturalytelse bortfaller", x = naturalytelse2, linefeed = false)
            addLabel("Verdi naturalytelse - kr/måned", x = naturalytelse3)
            inntektsmelding.inntekt?.naturalytelser?.forEach {
                addText(it.naturalytelse.name, naturalytelse1, linefeed = false)
                addText(it.sluttdato.tilNorskFormat(), naturalytelse2, linefeed = false)
                addText(it.verdiBeloep.tilNorskFormat(), naturalytelse3)
            }
            moveCursorBy(pdf.bodySize * 2)
        }
    }

    private fun addTidspunkt() {
        pdf.addItalics("Innsendt: ${inntektsmelding.mottatt.tilNorskFormat()}", kolonneEn, y)
        moveCursorBy(pdf.bodySize)
    }
}
