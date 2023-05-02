package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Refusjon
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.RefusjonEndring
import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

internal class PdfDokumentTest {

    val dag = LocalDate.of(2022, 12, 24)

    val im = MockInntektsmeldingDokument()

    val refusjon = Refusjon(
        true,
        25000.0.toBigDecimal(),
        dag.plusDays(3),
        listOf(
            RefusjonEndring(140.0.toBigDecimal(), dag.minusDays(4)),
            RefusjonEndring(150.0.toBigDecimal(), dag.minusDays(5)),
            RefusjonEndring(160.0.toBigDecimal(), dag.minusDays(6))
        )
    )

    @Test
    fun `uten refusjon`() {
        writePDF("uten_refusjon", im.copy(refusjon = Refusjon(false)))
    }

    @Test
    fun `med refusjon uten endringer`() {
        writePDF("med_refusjon_uten_endringer", im.copy(refusjon = refusjon.copy(refusjonEndringer = null)))
    }

    @Test
    fun `med refusjon med endringer`() {
        writePDF("med_refusjon_med_endringer", im.copy(refusjon = refusjon))
    }

    fun writePDF(title: String, im: InntektsmeldingDokument) {
        val file = File(System.getProperty("user.home"), "/Desktop/$title.pdf")
        // val file = File.createTempFile("$title", "pdf")
        val writer = FileOutputStream(file)
        writer.write(PdfDokument(im).export())
    }
}
