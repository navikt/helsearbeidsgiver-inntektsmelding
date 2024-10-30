package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.ForespoerselFraBro
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespurtData
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespurtDataMedFastsattInntekt
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselListeSvar
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

fun mockForespoerselSvarMedSuksess(): ForespoerselSvar {
    val forespoerselId = UUID.randomUUID()
    return ForespoerselSvar(
        forespoerselId = forespoerselId,
        resultat = mockForespoerselSvarSuksess(forespoerselId),
        feil = null,
        boomerang = mockBoomerang(),
    )
}

fun mockForespoerselListeSvarMedSuksess(): ForespoerselListeSvar {
    val orgnr = Orgnr.genererGyldig()
    return ForespoerselListeSvar(
        resultat =
            listOf(
                ForespoerselFraBro(
                    orgnr = orgnr,
                    fnr = Fnr.genererGyldig(),
                    vedtaksperiodeId = UUID.randomUUID(),
                    forespoerselId = UUID.randomUUID(),
                    sykmeldingsperioder = listOf(2.januar til 16.januar),
                    egenmeldingsperioder = listOf(1.januar til 1.januar),
                    bestemmendeFravaersdager = mapOf(orgnr to 1.januar),
                    forespurtData = mockForespurtData(),
                    erBesvart = false,
                ),
            ),
        boomerang = mockBoomerang(),
    )
}

fun mockForespoerselSvarMedSuksessMedFastsattInntekt(): ForespoerselSvar {
    val forespoerselId = UUID.randomUUID()
    return ForespoerselSvar(
        forespoerselId = forespoerselId,
        resultat = mockForespoerselSvarSuksessMedFastsattInntekt(forespoerselId),
        feil = null,
        boomerang = mockBoomerang(),
    )
}

fun mockForespoerselSvarMedFeil(): ForespoerselSvar =
    ForespoerselSvar(
        forespoerselId = UUID.randomUUID(),
        resultat = null,
        feil = ForespoerselSvar.Feil.FORESPOERSEL_IKKE_FUNNET,
        boomerang = mockBoomerang(),
    )

fun mockForespoerselListeSvarMedFeil(): ForespoerselListeSvar =
    ForespoerselListeSvar(
        resultat = emptyList(),
        boomerang = mockBoomerang(),
        feil = ForespoerselListeSvar.Feil.FORESPOERSEL_FOR_VEDTAKSPERIODE_ID_LISTE_FEILET,
    )

fun mockForespoerselSvarSuksess(forespoerselId: UUID): ForespoerselFraBro {
    val orgnr = Orgnr.genererGyldig()
    return ForespoerselFraBro(
        orgnr = orgnr,
        fnr = Fnr.genererGyldig(),
        forespoerselId = forespoerselId,
        vedtaksperiodeId = UUID.randomUUID(),
        sykmeldingsperioder = listOf(2.januar til 16.januar),
        egenmeldingsperioder = listOf(1.januar til 1.januar),
        bestemmendeFravaersdager = mapOf(orgnr to 1.januar),
        forespurtData = mockForespurtData(),
        erBesvart = false,
    )
}

fun mockForespoerselSvarSuksessMedFastsattInntekt(forespoerselId: UUID): ForespoerselFraBro {
    val orgnr = Orgnr.genererGyldig()
    return ForespoerselFraBro(
        orgnr = orgnr,
        fnr = Fnr.genererGyldig(),
        forespoerselId = forespoerselId,
        vedtaksperiodeId = UUID.randomUUID(),
        sykmeldingsperioder =
            listOf(
                2.januar til 10.januar,
                15.januar til 31.januar,
            ),
        egenmeldingsperioder = listOf(1.januar til 1.januar),
        bestemmendeFravaersdager = mapOf(orgnr to 1.januar),
        forespurtData = mockForespurtDataMedFastsattInntekt(),
        erBesvart = false,
    )
}

private fun mockBoomerang(): JsonElement =
    mapOf(
        Key.EVENT_NAME.str to EventName.INNTEKT_REQUESTED.toJson(),
        Key.UUID.str to UUID.randomUUID().toJson(),
    ).toJson()
