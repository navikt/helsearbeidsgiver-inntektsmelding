package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

internal class MapXmlDokumentKtTest {

    val dokument = MockInntektsmeldingDokument()

    @Test
    fun `skal mappe hele dokumentet`() {
        assertNotNull(mapXmlDokument(dokument))
    }

    @Test
    fun `skal mappe arbeidsgiverperioder`() {
        assertEquals("", mapArbeidsgiverperioder(emptyList()))
        assertEquals(
            "<arbeidsgiverperiode><fom>2022-12-24</fom><tom>2023-01-23</tom></arbeidsgiverperiode>\n" +
                "<arbeidsgiverperiode><fom>2022-12-24</fom><tom>2023-02-02</tom></arbeidsgiverperiode>\n" +
                "<arbeidsgiverperiode><fom>2022-12-24</fom><tom>2023-02-02</tom></arbeidsgiverperiode>",
            mapArbeidsgiverperioder(dokument.arbeidsgiverperioder)
        )
    }

    @Test
    fun `skal mappe naturalytelser`() {
        assertEquals("", mapNaturalytelser(emptyList()))
        assertEquals(
            "<opphoerAvNaturalytelse>" +
                "<naturalytelseType>Bil</naturalytelseType><fom>2022-12-29</fom><beloepPrMnd>350.0</beloepPrMnd>" +
                "</opphoerAvNaturalytelse>\n" +
                "<opphoerAvNaturalytelse>" +
                "<naturalytelseType>Bil</naturalytelseType><fom>2022-12-29</fom><beloepPrMnd>350.0</beloepPrMnd>" +
                "</opphoerAvNaturalytelse>",
            mapNaturalytelser(dokument.naturalytelser)
        )
    }

    @Test
    fun `skal mappe refusjonsendringer`() {
        assertEquals("", mapRefusjonsEndringer(emptyList()))
        assertEquals(
            "<endringIRefusjon><endringsdato>2022-12-20</endringsdato><refusjonsbeloepPrMnd>140.0</refusjonsbeloepPrMnd></endringIRefusjon>\n" +
                "<endringIRefusjon><endringsdato>2022-12-19</endringsdato><refusjonsbeloepPrMnd>150.0</refusjonsbeloepPrMnd></endringIRefusjon>\n" +
                "<endringIRefusjon><endringsdato>2022-12-18</endringsdato><refusjonsbeloepPrMnd>160.0</refusjonsbeloepPrMnd></endringIRefusjon>",
            mapRefusjonsEndringer(dokument.refusjon.refusjonEndringer)
        )
    }
}
