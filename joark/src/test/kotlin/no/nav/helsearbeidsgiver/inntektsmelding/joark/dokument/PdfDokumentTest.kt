package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.BegrunnelseIngenEllerRedusertUtbetalingKode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.FullLonnIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Naturalytelse
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.NaturalytelseKode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Periode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Refusjon
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.ÅrsakInnsending
import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.ZonedDateTime

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
        beregnetInntekt = 25000.0.toBigDecimal(),
        fullLønnIArbeidsgiverPerioden = FullLonnIArbeidsgiverPerioden(
            false,
            begrunnelse = BegrunnelseIngenEllerRedusertUtbetalingKode.BESKJED_GITT_FOR_SENT
        ),
        refusjon = Refusjon(true, 25000.0.toBigDecimal(), dag.plusDays(3)),
        naturalytelser = listOf(
            Naturalytelse(
                NaturalytelseKode.BIL,
                dag.plusDays(5),
                350.0.toBigDecimal()
            ),
            Naturalytelse(
                NaturalytelseKode.BEDRIFTSBARNEHAGEPLASS,
                dag.plusDays(7),
                44.0.toBigDecimal()
            ),
            Naturalytelse(
                NaturalytelseKode.YRKEBIL_TJENESTLIGBEHOV_LISTEPRIS,
                dag.plusDays(3),
                12.0.toBigDecimal()
            )
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
        tidspunkt = ZonedDateTime.now().toOffsetDateTime(),
        årsakInnsending = ÅrsakInnsending.NY,
        identitetsnummerInnsender = "123123123123123"
    )

    @Test
    fun `skal lage kvittering`() {
        val file = File(System.getProperty("user.home"), "/Desktop/inntektsmeldingDokument.pdf")
        // val file = File.createTempFile("inntektsmeldingDokument", "pdf")
        val writer = FileOutputStream(file)
        writer.write(PdfDokument(im).export())
    }
}
