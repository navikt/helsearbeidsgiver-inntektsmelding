package no.nav.helsearbeidsgiver.felles

import com.fasterxml.jackson.annotation.JsonAlias

enum class BehovType {
    FULLT_NAVN,
    VIRKSOMHET,
    INNTEKT,
    ARBEIDSFORHOLD,
    EGENMELDING,
    SYK,
    JOURNALFOER,
    ARBEIDSGIVERE
}

@Target(AnnotationTarget.PROPERTY)
@JsonAlias("FULLT_NAVN")
annotation class JsonAliasFulltNavn

@Target(AnnotationTarget.PROPERTY)
@JsonAlias("VIRKSOMHET")
annotation class JsonAliasVirksomhet

@Target(AnnotationTarget.PROPERTY)
@JsonAlias("INNTEKT")
annotation class JsonAliasInntekt

@Target(AnnotationTarget.PROPERTY)
@JsonAlias("ARBEIDSFORHOLD")
annotation class JsonAliasArbeidsforhold

@Target(AnnotationTarget.PROPERTY)
@JsonAlias("EGENMELDING")
annotation class JsonAliasEgenmelding

@Target(AnnotationTarget.PROPERTY)
@JsonAlias("SYK")
annotation class JsonAliasSyk

@Target(AnnotationTarget.PROPERTY)
@JsonAlias("JOURNALFOER")
annotation class JsonAliasJournalfoer

@Target(AnnotationTarget.PROPERTY)
@JsonAlias("ARBEIDSGIVERE")
annotation class JsonAliasArbeidsgivere
