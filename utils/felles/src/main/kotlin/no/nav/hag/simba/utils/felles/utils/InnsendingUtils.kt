package no.nav.hag.simba.utils.felles.utils

import no.nav.hag.simba.utils.felles.domene.ApiInnsendingIntern
import no.nav.hag.simba.utils.felles.domene.InnsendingIntern
import no.nav.hag.simba.utils.felles.domene.SkjemaInntektsmeldingIntern
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntekt

object InnsendingUtils {
    fun oversett(apiInnsending: ApiInnsendingIntern): InnsendingIntern =
        InnsendingIntern(
            innsendingId = apiInnsending.innsendingId,
            skjema =
                SkjemaInntektsmeldingIntern(
                    forespoerselId = apiInnsending.skjema.forespoerselId,
                    avsenderTlf = apiInnsending.skjema.avsenderTlf,
                    agp = apiInnsending.skjema.agp,
                    inntekt =
                        apiInnsending.skjema.inntekt?.let {
                            Inntekt(
                                it.beloep,
                                it.inntektsdato,
                                apiInnsending.skjema.naturalytelser,
                                it.endringAarsaker,
                            )
                        },
                    refusjon = apiInnsending.skjema.refusjon,
                    naturalytelser = apiInnsending.skjema.naturalytelser,
                ),
            aarsakInnsending = apiInnsending.aarsakInnsending,
            type = apiInnsending.type,
            innsendtTid = apiInnsending.innsendtTid,
            versjon = apiInnsending.versjon,
        )
}
