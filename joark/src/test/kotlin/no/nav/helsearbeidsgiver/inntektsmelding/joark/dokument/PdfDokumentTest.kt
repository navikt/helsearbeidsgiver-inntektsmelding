package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileOutputStream

internal class PdfDokumentTest {

    @Test
    fun `skal lage kvittering`() {
        val file = File.createTempFile("kvittering", "pdf")
        val writer = FileOutputStream(file)
        // writer.write(DokumentPDF().export())
    }
}
