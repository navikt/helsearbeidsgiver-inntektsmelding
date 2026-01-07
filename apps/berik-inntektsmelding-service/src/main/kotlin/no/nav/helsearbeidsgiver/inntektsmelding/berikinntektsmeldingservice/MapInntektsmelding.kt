package no.nav.helsearbeidsgiver.inntektsmelding.berikinntektsmeldingservice

import no.nav.hag.simba.kontrakt.domene.forespoersel.Forespoersel
import no.nav.hag.simba.utils.felles.Tekst
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Avsender
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Sykmeldt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.api.Innsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.utils.date.toOffsetDateTimeOslo
import java.time.LocalDateTime
import java.util.UUID

fun mapInntektsmelding(
    inntektsmeldingId: UUID,
    forespoersel: Forespoersel,
    skjema: SkjemaInntektsmelding,
    aarsakInnsending: AarsakInnsending,
    virksomhetNavn: String,
    sykmeldtNavn: String,
    avsenderNavn: String?,
    mottatt: LocalDateTime,
    innsending: Innsending? = null, // TODO: kanskje heller en separat mapping for api-innsending?
): Inntektsmelding {
    val inntekt =
        if (skjema.agp != null) {
            skjema.inntekt
        } else {
            skjema.inntekt?.copy(
                inntektsdato = forespoersel.forslagInntektsdato(),
            )
        }

    return Inntektsmelding(
        id = innsending?.innsendingId ?: inntektsmeldingId,
        type =
            innsending?.type
                ?: Inntektsmelding.Type.Forespurt(
                    id = skjema.forespoerselId,
                    erAgpForespurt = forespoersel.forespurtData.arbeidsgiverperiode.paakrevd,
                ),
        sykmeldt =
            Sykmeldt(
                fnr = forespoersel.fnr,
                navn = sykmeldtNavn,
            ),
        avsender =
            Avsender(
                orgnr = forespoersel.orgnr,
                orgNavn = virksomhetNavn,
                navn = avsenderNavn ?: Tekst.UKJENT_NAVN,
                tlf = skjema.avsenderTlf,
            ),
        sykmeldingsperioder = forespoersel.sykmeldingsperioder,
        agp = skjema.agp,
        inntekt = inntekt,
        naturalytelser = skjema.naturalytelser,
        refusjon = skjema.refusjon,
        aarsakInnsending = innsending?.aarsakInnsending ?: aarsakInnsending,
        mottatt = mottatt.toOffsetDateTimeOslo(),
        vedtaksperiodeId = forespoersel.vedtaksperiodeId,
    )
}
