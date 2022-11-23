package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime

internal class PdfDokumentTest {

    val dag = LocalDate.of(2022, 12, 24)

    val im = InntektsmeldingDokument(
        orgnrUnderenhet = "123456789",
        identitetsnummer = "12345678901",
        fulltNavn = "Ola Normann",
        virksomhetNavn = "Norge AS",
        behandlingsdager = listOf(dag),
        egenmeldingsperioder = listOf(Periode(dag, dag.plusDays(2))),
        bruttoInntekt = Bruttoinntekt(true, 25000.0, "Ingen årsak", true),
        fullLønnIArbeidsgiverPerioden = FullLønnIArbeidsgiverPerioden(true, begrunnelse = BegrunnelseIngenEllerRedusertUtbetalingKode.BeskjedGittForSent),
        heleEllerdeler = HeleEllerdeler(true, 25000.0, dag.plusDays(3)),
        naturalytelser = listOf(Naturalytelse("asd", dag.plusDays(5), 350.0)),
        bekreftOpplysninger = true,
        fravaersperioder = emptyList(),
        arbeidsgiverperioder = emptyList(),
        bestemmendeFraværsdag = dag.plusDays(6),
        tidspunkt = LocalDateTime.now()
    )

    @Test
    fun `skal lage kvittering`() {
        val file = File.createTempFile("kvittering", "pdf")
        val writer = FileOutputStream(file)
        writer.write(PdfDokument().export(im))
    }
}
