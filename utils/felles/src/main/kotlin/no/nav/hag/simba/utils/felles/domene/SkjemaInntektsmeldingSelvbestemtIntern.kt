@file:UseSerializers(UuidSerializer::class)

package no.nav.hag.simba.utils.felles.domene

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Arbeidsgiverperiode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntekt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Naturalytelse
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Refusjon
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.ArbeidsforholdType
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaAvsender
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmeldingSelvbestemt
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@Deprecated("Skal erstattes med im-domene 0.5.0")
data class SkjemaInntektsmeldingSelvbestemtIntern(
    val selvbestemtId: UUID?,
    val sykmeldtFnr: Fnr,
    val avsender: SkjemaAvsender,
    val sykmeldingsperioder: List<Periode>,
    val agp: Arbeidsgiverperiode?,
    val inntekt: Inntekt,
    val refusjon: Refusjon?,
    @EncodeDefault
    val naturalytelser: List<Naturalytelse> = inntekt.naturalytelser,
    val vedtaksperiodeId: UUID? = null, // nullable for å støtte fisker og utenArbeidsforhold
    val arbeidsforholdType: ArbeidsforholdType,
) {
    fun valider(): Set<String> {
        val naturalytelserFeilmelding =
            if (naturalytelser.all { it.verdiBeloep > 0 && it.verdiBeloep < 1_000_000 }) {
                null
            } else {
                "Beløp må være større enn 0"
            }

        return SkjemaInntektsmeldingSelvbestemt(
            selvbestemtId,
            sykmeldtFnr,
            avsender,
            sykmeldingsperioder,
            agp,
            inntekt,
            refusjon,
            vedtaksperiodeId,
            arbeidsforholdType,
        ).valider()
            .plus(naturalytelserFeilmelding)
            .mapNotNull { it }
            .toSet()
    }
}
