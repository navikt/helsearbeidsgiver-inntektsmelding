package no.nav.helsearbeidsgiver.felles.inntektsmelding.db

import kotlinx.serialization.Serializable

@Serializable
class JournalførtInntektsmelding(
    val inntektsmeldingDokument: InntektsmeldingDokument,
    val journalpostId: String
)
