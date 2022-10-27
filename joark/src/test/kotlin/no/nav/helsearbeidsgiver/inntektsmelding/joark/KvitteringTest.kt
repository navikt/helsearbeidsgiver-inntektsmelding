package no.nav.helsearbeidsgiver.inntektsmelding.joark

import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileOutputStream

internal class KvitteringTest {

    @Test
    fun `skal lage kvittering`() {
        val file = File.createTempFile("kvittering", "pdf")
        val writer = FileOutputStream(file)
        writer.write(Kvittering().export())
    }
}
