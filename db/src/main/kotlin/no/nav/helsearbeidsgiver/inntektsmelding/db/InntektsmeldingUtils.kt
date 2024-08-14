package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helsearbeidsgiver.domene.inntektsmelding.Utils.convertToV1
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import java.util.UUID
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding as InntektsmeldingV1

fun Inntektsmelding.erDuplikatAv(other: Inntektsmelding): Boolean =
    this ==
        other.copy(
            vedtaksperiodeId = vedtaksperiodeId,
            tidspunkt = tidspunkt,
            årsakInnsending = årsakInnsending,
            innsenderNavn = innsenderNavn,
            telefonnummer = telefonnummer,
        )

fun Inntektsmelding.erDuplikatAv(other: SkjemaInntektsmelding): Boolean =
    convertToV1(
        inntektsmelding = this,
        // inntektsmeldingId og vedtaksperiodeId er ikke relevant for sammenlikningen.
        inntektsmeldingId = UUID.randomUUID(),
        type = InntektsmeldingV1.Type.Forespurt(id = other.forespoerselId, vedtaksperiodeId = UUID.randomUUID()),
    ).let {
        it.type.id == other.forespoerselId &&
            it.agp == other.agp &&
            it.inntekt == other.inntekt &&
            it.refusjon == other.refusjon
    }

fun SkjemaInntektsmelding.erDuplikatAv(other: SkjemaInntektsmelding): Boolean =
    this ==
        other.copy(
            avsenderTlf = avsenderTlf,
        )
