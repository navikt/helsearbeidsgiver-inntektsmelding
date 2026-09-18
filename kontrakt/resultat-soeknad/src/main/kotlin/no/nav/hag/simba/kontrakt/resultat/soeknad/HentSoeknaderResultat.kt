@file:UseSerializers(UuidSerializer::class)

package no.nav.hag.simba.kontrakt.resultat.soeknad

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.builtins.TripleSerializer
import no.nav.hag.simba.kontrakt.domene.forespoersel.Forespoersel
import no.nav.hag.simba.kontrakt.domene.soeknad.Soeknad
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import java.util.UUID

@Serializable
data class ForespoerselMedId(
    val forespoerselId: UUID,
    val forespoersel: Forespoersel,
)

@Serializable
data class SoeknadMedForlengerId(
    val soeknad: Soeknad.Arbeidstaker,
    val forlengerVedtaksperiodeId: UUID?,
)

val hentSoeknaderResultatSerializer =
    TripleSerializer(
        aSerializer = ForespoerselMedId.serializer().list(),
        bSerializer = SoeknadMedForlengerId.serializer().list(),
        cSerializer = Soeknad.Behandlingsdager.serializer().list(),
    )
