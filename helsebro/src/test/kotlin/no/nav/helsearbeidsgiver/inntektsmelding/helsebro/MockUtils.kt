package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.test.date.januar
import no.nav.helsearbeidsgiver.felles.til
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ArbeidsgiverPeriode
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.FastsattInntekt
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespurtData
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForslagInntekt
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.Inntekt
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.Refusjon
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.TrengerForespoersel
import java.util.UUID

fun mockForespurtDataListe(): List<ForespurtData> =
    listOf(
        ArbeidsgiverPeriode(
            forslag = listOf(
                1.januar til 10.januar,
                15.januar til 20.januar
            )
        ),
        Inntekt(
            forslag = ForslagInntekt(
                beregningsmåneder = listOf(
                    oktober(2017),
                    november(2017),
                    desember(2017)
                )
            )
        ),
        Refusjon
    )

fun mockForespurtDataMedFastsattInntektListe(): List<ForespurtData> =
    listOf(
        ArbeidsgiverPeriode(
            forslag = listOf(
                1.januar til 10.januar,
                15.januar til 20.januar
            )
        ),
        FastsattInntekt(
            fastsattInntekt = 31415.92
        ),
        Refusjon
    )

fun mockTrengerForespoersel(): TrengerForespoersel =
    TrengerForespoersel(
        vedtaksperiodeId = UUID.randomUUID(),
        boomerang = mockBoomerang()
    )

fun mockForespoerselSvar(): ForespoerselSvar =
    ForespoerselSvar(
        orgnr = "hungry-traitor-chaplain",
        fnr = "deputize-snowy-quirk",
        vedtaksperiodeId = UUID.randomUUID(),
        sykmeldingsperioder = listOf(1.januar til 16.januar),
        forespurtData = mockForespurtDataListe(),
        boomerang = mockBoomerang()
    )

private fun mockBoomerang(): Map<String, JsonElement> =
    mapOf(
        Key.INITIATE_ID.str to UUID.randomUUID().toJson()
    )
