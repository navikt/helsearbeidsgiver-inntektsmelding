@file:UseSerializers(OffsetDateTimeSerializer::class, UuidSerializer::class)

package no.nav.hag.simba.utils.felles.domene

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Arbeidsgiverperiode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Avsender
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntekt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Kanal
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Naturalytelse
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.RedusertLoennIAgp
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Refusjon
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Sykmeldt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.api.AvsenderSystem
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.ArbeidsforholdType
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaAvsender
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmeldingSelvbestemt
import no.nav.helsearbeidsgiver.utils.json.serializer.OffsetDateTimeSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.time.OffsetDateTime
import java.util.UUID

// Brukes for å trygt fase ut eksplisitte egenmeldinger
@Serializable
data class ArbeidsgiverperiodeUtenEksplisitteEgenmeldinger(
    val perioder: List<Periode>,
    @EncodeDefault
    val egenmeldinger: List<Periode> = emptyList(),
    val redusertLoennIAgp: RedusertLoennIAgp?,
) {
    fun tilArbeidsgiverperiode(): Arbeidsgiverperiode =
        Arbeidsgiverperiode(
            perioder = perioder,
            egenmeldinger = egenmeldinger,
            redusertLoennIAgp = redusertLoennIAgp,
        )

    fun erGyldigHvisIkkeForespurt(
        erAgpForespurt: Boolean,
        sykmeldingsperioder: List<Periode>,
    ): Boolean = tilArbeidsgiverperiode().erGyldigHvisIkkeForespurt(erAgpForespurt, sykmeldingsperioder)
}

@Serializable
data class SkjemaInntektsmeldingIntern(
    val forespoerselId: UUID,
    val avsenderTlf: String,
    val agp: ArbeidsgiverperiodeUtenEksplisitteEgenmeldinger?,
    val inntekt: Inntekt?,
    val naturalytelser: List<Naturalytelse>,
    val refusjon: Refusjon?,
) {
    fun tilSkjemaInntektsmelding(): SkjemaInntektsmelding =
        SkjemaInntektsmelding(
            forespoerselId = forespoerselId,
            avsenderTlf = avsenderTlf,
            agp = agp?.tilArbeidsgiverperiode(),
            inntekt = inntekt,
            naturalytelser = naturalytelser,
            refusjon = refusjon,
        )

    fun valider(): Set<String> = this.tilSkjemaInntektsmelding().valider()
}

@Serializable
data class SkjemaInntektsmeldingSelvbestemtIntern(
    val selvbestemtId: UUID?,
    val sykmeldtFnr: Fnr,
    val avsender: SkjemaAvsender,
    val sykmeldingsperioder: List<Periode>,
    val agp: ArbeidsgiverperiodeUtenEksplisitteEgenmeldinger?,
    val inntekt: Inntekt,
    val naturalytelser: List<Naturalytelse>,
    val refusjon: Refusjon?,
    val vedtaksperiodeId: UUID? = null, // nullable for å støtte fisker og utenArbeidsforhold
    val arbeidsforholdType: ArbeidsforholdType,
) {
    fun valider(): Set<String> =
        SkjemaInntektsmeldingSelvbestemt(
            selvbestemtId = selvbestemtId,
            sykmeldtFnr = sykmeldtFnr,
            avsender = avsender,
            sykmeldingsperioder = sykmeldingsperioder,
            agp = agp?.tilArbeidsgiverperiode(),
            inntekt = inntekt,
            naturalytelser = naturalytelser,
            refusjon = refusjon,
            vedtaksperiodeId = vedtaksperiodeId,
            arbeidsforholdType = arbeidsforholdType,
        ).valider()
}

@Serializable
data class InntektsmeldingIntern(
    val id: UUID,
    val type: Type,
    val sykmeldt: Sykmeldt,
    val avsender: Avsender,
    val sykmeldingsperioder: List<Periode>,
    val agp: ArbeidsgiverperiodeUtenEksplisitteEgenmeldinger?,
    val inntekt: Inntekt?,
    val naturalytelser: List<Naturalytelse>,
    val refusjon: Refusjon?,
    val aarsakInnsending: AarsakInnsending,
    val mottatt: OffsetDateTime,
    val vedtaksperiodeId: UUID? = null, // nullable for å støtte fisker og utenArbeidsforhold
) {
    @Serializable
    sealed class Type {
        abstract val id: UUID

        open val avsenderSystem: AvsenderSystem
            get() = AvsenderSystem.nav

        val kanal: Kanal
            get() =
                when (this) {
                    is ForespurtEkstern -> Kanal.HR_SYSTEM_API
                    is Forespurt, is Selvbestemt, is Fisker, is UtenArbeidsforhold, is Behandlingsdager -> Kanal.NAV_NO
                }

        @Serializable
        @SerialName("Forespurt")
        data class Forespurt(
            override val id: UUID,
            @EncodeDefault
            val erAgpForespurt: Boolean = true,
        ) : Type()

        @Serializable
        @SerialName("ForespurtEkstern")
        data class ForespurtEkstern(
            override val id: UUID,
            @EncodeDefault
            val erAgpForespurt: Boolean = true,
            private val _avsenderSystem: AvsenderSystem,
        ) : Type() {
            override val avsenderSystem: AvsenderSystem
                get() = _avsenderSystem
        }

        @Serializable
        @SerialName("Selvbestemt")
        data class Selvbestemt(
            override val id: UUID,
        ) : Type()

        @Serializable
        @SerialName("Fisker")
        data class Fisker(
            override val id: UUID,
        ) : Type()

        @Serializable
        @SerialName("UtenArbeidsforhold")
        data class UtenArbeidsforhold(
            override val id: UUID,
        ) : Type()

        @Serializable
        @SerialName("Behandlingsdager")
        data class Behandlingsdager(
            override val id: UUID,
        ) : Type()

        fun tilType(): Inntektsmelding.Type =
            when (this) {
                is Forespurt -> Inntektsmelding.Type.Forespurt(id, erAgpForespurt)
                is ForespurtEkstern -> Inntektsmelding.Type.ForespurtEkstern(id, erAgpForespurt, avsenderSystem)
                is Selvbestemt -> Inntektsmelding.Type.Selvbestemt(id)
                is Fisker -> Inntektsmelding.Type.Fisker(id)
                is UtenArbeidsforhold -> Inntektsmelding.Type.UtenArbeidsforhold(id)
                is Behandlingsdager -> Inntektsmelding.Type.Behandlingsdager(id)
            }
    }

    fun tilInntektsmelding(): Inntektsmelding =
        Inntektsmelding(
            id = id,
            type = type.tilType(),
            sykmeldt = sykmeldt,
            avsender = avsender,
            sykmeldingsperioder = sykmeldingsperioder,
            agp = agp?.tilArbeidsgiverperiode(),
            inntekt = inntekt,
            naturalytelser = naturalytelser,
            refusjon = refusjon,
            aarsakInnsending = aarsakInnsending,
            mottatt = mottatt,
            vedtaksperiodeId = vedtaksperiodeId,
        )
}

@Serializable
data class InnsendingIntern(
    val innsendingId: UUID,
    val skjema: SkjemaInntektsmeldingIntern,
    val aarsakInnsending: AarsakInnsending,
    val type: InntektsmeldingIntern.Type,
    val innsendtTid: OffsetDateTime,
    val kontaktinfo: String,
    @EncodeDefault
    val versjon: Int = 1,
)
