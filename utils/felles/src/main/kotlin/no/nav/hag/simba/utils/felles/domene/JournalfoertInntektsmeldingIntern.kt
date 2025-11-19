package no.nav.hag.simba.utils.felles.domene

import kotlinx.serialization.Serializable

@Serializable
data class JournalfoertInntektsmeldingIntern(
    val journalpostId: String,
    val inntektsmelding: InntektsmeldingIntern,
)
