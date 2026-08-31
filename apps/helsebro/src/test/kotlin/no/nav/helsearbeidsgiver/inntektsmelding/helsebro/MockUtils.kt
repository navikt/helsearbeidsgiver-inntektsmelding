package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.kontrakt.domene.bro.forespoersel.test.mockForespoerselFraBro
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselListeSvar
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

fun mockForespoerselSvarMedSuksess(): ForespoerselSvar {
    val forespoerselId = UUID.randomUUID()
    return ForespoerselSvar(
        forespoerselId = forespoerselId,
        resultat = mockForespoerselFraBro().copy(forespoerselId = forespoerselId),
        feil = null,
        boomerang = mockBoomerang(),
    )
}

fun mockForespoerselListeSvarMedSuksess(): ForespoerselListeSvar =
    ForespoerselListeSvar(
        resultat = listOf(mockForespoerselFraBro()),
        boomerang = mockBoomerang(),
    )

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

private fun mockBoomerang(): JsonElement =
    mapOf(
        Key.EVENT_NAME to EventName.SERVICE_HENT_INNTEKT.toJson(),
        Key.KONTEKST_ID to UUID.randomUUID().toJson(),
    ).toJson()
