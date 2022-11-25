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
        egenmeldingsperioder = listOf(
            Periode(dag, dag.plusDays(2)),
            Periode(dag.plusDays(3), dag.plusDays(4))
        ),
        beregnetInntektEndringÅrsak = ÅrsakBeregnetInntektEndringKodeliste.FeilInntekt,
        beregnetInntekt = 25000.0,
        fullLønnIArbeidsgiverPerioden = FullLønnIArbeidsgiverPerioden(true, begrunnelse = BegrunnelseIngenEllerRedusertUtbetalingKode.BeskjedGittForSent),
        refusjon = Refusjon(25000.0, dag.plusDays(3)),
        naturalytelser = listOf(
            Naturalytelse(NaturalytelseKode.Bil, dag.plusDays(5), 350.0),
            Naturalytelse(NaturalytelseKode.Bil, dag.plusDays(5), 350.0)
        ),
        fraværsperioder = listOf(
            Periode(dag, dag.plusDays(55)),
            Periode(dag, dag.plusDays(22)),
            Periode(dag, dag.plusDays(32))
        ),
        arbeidsgiverperioder = listOf(
            Periode(dag, dag.plusDays(30)),
            Periode(dag, dag.plusDays(40)),
            Periode(dag, dag.plusDays(40))
        ),
        bestemmendeFraværsdag = dag.plusDays(90),
        tidspunkt = LocalDateTime.now(),
        årsakInnsending = ÅrsakInnsending.Ny,
        identitetsnummerInnsender = "123123123123123"
    )

    @Test
    fun `skal lage kvittering`() {
        // val file = File(System.getProperty("user.home"), "/Desktop/inntektsmelding.pdf")
        val file = File.createTempFile("kvittering", "pdf")
        val writer = FileOutputStream(file)
        writer.write(PdfDokument(im).export())
    }
}
