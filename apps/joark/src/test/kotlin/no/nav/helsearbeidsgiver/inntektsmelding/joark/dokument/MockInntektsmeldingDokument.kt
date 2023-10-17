package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import no.nav.helsearbeidsgiver.domene.inntektsmelding.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmelding

fun mockInntektsmeldingDokumentMedTommeLister(): Inntektsmelding =
    mockInntektsmelding().let {
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
