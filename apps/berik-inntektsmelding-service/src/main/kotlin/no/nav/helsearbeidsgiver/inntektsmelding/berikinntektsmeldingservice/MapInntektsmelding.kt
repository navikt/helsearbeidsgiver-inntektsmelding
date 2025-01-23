package no.nav.helsearbeidsgiver.inntektsmelding.berikinntektsmeldingservice

import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Avsender
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Sykmeldt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import no.nav.helsearbeidsgiver.felles.utils.toOffsetDateTimeOslo
import java.time.LocalDateTime
import java.util.UUID

fun mapInntektsmelding(
    forespoersel: Forespoersel,
    skjema: SkjemaInntektsmelding,
    aarsakInnsending: AarsakInnsending,
    virksomhetNavn: String,
    sykmeldtNavn: String,
    avsenderNavn: String,
    mottatt: LocalDateTime,
): Inntektsmelding {
    val agp =
        if (forespoersel.forespurtData.arbeidsgiverperiode.paakrevd) {
            skjema.agp
        } else {
            null
        }

    val inntekt =
        if (forespoersel.forespurtData.inntekt.paakrevd) {
            if (forespoersel.forespurtData.arbeidsgiverperiode.paakrevd) {
                skjema.inntekt
            } else {
                skjema.inntekt?.copy(
                    inntektsdato = forespoersel.forslagInntektsdato(),
                )
            }
        } else {
            null
        }

    val refusjon =
        if (forespoersel.forespurtData.refusjon.paakrevd) {
            skjema.refusjon
        } else {
            null
        }

    return Inntektsmelding(
        id = UUID.randomUUID(),
        type =
            Inntektsmelding.Type.Forespurt(
                id = skjema.forespoerselId,
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
                navn = avsenderNavn,
                tlf = skjema.avsenderTlf,
            ),
        sykmeldingsperioder = forespoersel.sykmeldingsperioder,
        agp = agp,
        inntekt = inntekt,
        refusjon = refusjon,
        aarsakInnsending = aarsakInnsending,
        mottatt = mottatt.toOffsetDateTimeOslo(),
        vedtaksperiodeId = forespoersel.vedtaksperiodeId,
    )
}
