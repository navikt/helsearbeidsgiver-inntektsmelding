package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.BegrunnelseIngenEllerRedusertUtbetalingKode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.FullLonnIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Refusjon
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.RefusjonEndring
import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

internal class PdfDokumentTest {

    private val dag = LocalDate.of(2022, 12, 24)
    private val im = MockInntektsmeldingDokument()

    @Test
    fun `betaler full lønn i arbeidsgiverperioden`() {
        writePDF(
            "ikke_refusjon_og_full_lønn_i_arbeidsgiverperioden",
            im.copy(
                fullLønnIArbeidsgiverPerioden = FullLonnIArbeidsgiverPerioden(true),
                refusjon = Refusjon(false)
            )
        )
    }

    @Test
    fun `betaler ikke full lønn i arbeidsgiverperioden`() {
        writePDF(
            "ikke_refusjon_og_ikke_full_lønn_i_arbeidsgiverperioden",
            im.copy(
                fullLønnIArbeidsgiverPerioden = FullLonnIArbeidsgiverPerioden(
                    false,
                    BegrunnelseIngenEllerRedusertUtbetalingKode.PERMITTERING,
                    5000.0.toBigDecimal()
                ),
                refusjon = Refusjon(false)
            )
        )
    }

    @Test
    fun `krever refusjon etter arbeidsgiverperioden`() {
        writePDF(
            "med_refusjon_og_full_lønn_arbeidsgiverperioden_opphører",
            im.copy(
                fullLønnIArbeidsgiverPerioden = FullLonnIArbeidsgiverPerioden(true),
                refusjon = Refusjon(
                    true,
                    25000.0.toBigDecimal(),
                    dag.plusDays(3)
                )
            )
        )
    }

    @Test
    fun `med refusjon og uten endringer`() {
        writePDF(
            "med_refusjon_og_full_lønn_arbeidsgiverperioden_oppnører_ikke",
            im.copy(
                fullLønnIArbeidsgiverPerioden = FullLonnIArbeidsgiverPerioden(true),
                refusjon = Refusjon(
                    true,
                    25000.0.toBigDecimal(),
                    null
                )
            )
        )
    }

    @Test
    fun `med refusjon med endringer`() {
        writePDF(
            "med_refusjon_og_endringer_i_beloep",
            im.copy(
                fullLønnIArbeidsgiverPerioden = FullLonnIArbeidsgiverPerioden(true),
                refusjon = Refusjon(
                    true,
                    25000.0.toBigDecimal(),
                    null,
                    refusjonEndringer = listOf(
                        RefusjonEndring(140.0.toBigDecimal(), dag.minusDays(4)),
                        RefusjonEndring(150.0.toBigDecimal(), dag.minusDays(5)),
                        RefusjonEndring(160.0.toBigDecimal(), dag.minusDays(6))
                    )
                )
            )
        )
    }

    fun writePDF(title: String, im: InntektsmeldingDokument) {
        val file = File(System.getProperty("user.home"), "/Desktop/$title.pdf")
        // val file = File.createTempFile("$title", "pdf")
        val writer = FileOutputStream(file)
        writer.write(PdfDokument(im).export())
    }
}
