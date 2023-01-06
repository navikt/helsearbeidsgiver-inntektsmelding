package no.nav.helsearbeidsgiver.felles

class TrengerInntekt(
    val fnr: String,
    val orgnr: String,
    val sykemeldingsperioder: List<Periode> = emptyList()
)
