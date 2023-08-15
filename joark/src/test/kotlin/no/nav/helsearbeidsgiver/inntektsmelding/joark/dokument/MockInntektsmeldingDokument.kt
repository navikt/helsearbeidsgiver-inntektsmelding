package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingDokument

fun mockInntektsmeldingDokumentMedTommeLister(): InntektsmeldingDokument =
    mockInntektsmeldingDokument().let {
        it.copy(
            behandlingsdager = emptyList(),
            egenmeldingsperioder = emptyList(),
            refusjon = it.refusjon.copy(
                refusjonEndringer = emptyList()
            ),
            naturalytelser = emptyList(),
            fraværsperioder = emptyList(),
            arbeidsgiverperioder = emptyList()
        )
    }
