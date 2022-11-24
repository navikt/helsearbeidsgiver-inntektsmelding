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
        egenmeldingsperioder = listOf(Periode(dag, dag.plusDays(2)), Periode(dag.plusDays(3), dag.plusDays(4))),
        bruttoInntekt = Bruttoinntekt(true, 25000.0, "Ingen årsak", true),
        fullLønnIArbeidsgiverPerioden = FullLønnIArbeidsgiverPerioden(true, begrunnelse = BegrunnelseIngenEllerRedusertUtbetalingKode.BeskjedGittForSent),
        heleEllerdeler = HeleEllerdeler(true, 25000.0, dag.plusDays(3)),
        naturalytelser = listOf(Naturalytelse("asd", dag.plusDays(5), 350.0)),
        bekreftOpplysninger = true,
        fravaersperioder = listOf(Periode(dag, dag.plusDays(55))),
        arbeidsgiverperioder = listOf(Periode(dag, dag.plusDays(80))),
        bestemmendeFraværsdag = dag.plusDays(90),
        tidspunkt = LocalDateTime.now()
    )

    @Test
    fun `skal lage kvittering`() {
        val file = File(System.getProperty("user.home"), "/Desktop/inntektsmelding.pdf")
        val writer = FileOutputStream(file)
        writer.write(PdfDokument(im).export())
    }
}
