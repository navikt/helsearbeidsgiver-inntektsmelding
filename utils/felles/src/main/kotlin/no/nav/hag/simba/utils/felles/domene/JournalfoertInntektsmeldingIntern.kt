package no.nav.hag.simba.utils.felles.domene

import kotlinx.serialization.Serializable

@Serializable
@Deprecated("Skal erstattes med im-domene 0.5.0")
data class JournalfoertInntektsmeldingIntern(
    val journalpostId: String,
    val inntektsmelding: InntektsmeldingIntern,
)
