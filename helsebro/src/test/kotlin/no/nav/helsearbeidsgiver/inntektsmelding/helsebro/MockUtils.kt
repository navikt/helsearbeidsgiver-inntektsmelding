package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.ForespoerselType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespurtData
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespurtDataMedFastsattInntekt
import no.nav.helsearbeidsgiver.felles.til
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.januar
import java.util.UUID

fun mockForespoerselSvarMedSuksess(): ForespoerselSvar =
    ForespoerselSvar(
        forespoerselId = UUID.randomUUID(),
        resultat = mockForespoerselSvarSuksess(),
        feil = null,
        boomerang = mockBoomerang()
    )

fun mockForespoerselSvarMedSuksessMedFastsattInntekt(): ForespoerselSvar =
    ForespoerselSvar(
        forespoerselId = UUID.randomUUID(),
        resultat = mockForespoerselSvarSuksessMedFastsattInntekt(),
        feil = null,
        boomerang = mockBoomerang()
    )

fun mockForespoerselSvarMedFeil(): ForespoerselSvar =
    ForespoerselSvar(
        forespoerselId = UUID.randomUUID(),
        resultat = null,
        feil = ForespoerselSvar.Feil.FORESPOERSEL_IKKE_FUNNET,
        boomerang = mockBoomerang()
    )

fun mockForespoerselSvarSuksess(): ForespoerselSvar.Suksess =
    ForespoerselSvar.Suksess(
        type = ForespoerselType.KOMPLETT,
        orgnr = "hungry-traitor-chaplain",
        fnr = "deputize-snowy-quirk",
        skjaeringstidspunkt = 11.januar(2018),
        sykmeldingsperioder = listOf(2.januar til 16.januar),
        egenmeldingsperioder = listOf(1.januar til 1.januar),
        forespurtData = mockForespurtData(),
        erBesvart = false,
        vedtaksperiodeId = randomUuid()
    )

fun mockForespoerselSvarSuksessMedFastsattInntekt(): ForespoerselSvar.Suksess =
    ForespoerselSvar.Suksess(
        type = ForespoerselType.KOMPLETT,
        orgnr = "full-traitor-chaplain",
        fnr = "captain-snowy-quirk",
        skjaeringstidspunkt = null,
        sykmeldingsperioder = listOf(
            2.januar til 10.januar,
            15.januar til 31.januar
        ),
        egenmeldingsperioder = listOf(1.januar til 1.januar),
        forespurtData = mockForespurtDataMedFastsattInntekt(),
        erBesvart = false,
        vedtaksperiodeId = randomUuid()
    )

private fun mockBoomerang(): JsonElement =
    mapOf(
        Key.EVENT_NAME.str to EventName.INNTEKT_REQUESTED.toJson(),
        Key.UUID.str to UUID.randomUUID().toJson()
    )
        .toJson()
