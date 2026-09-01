package no.nav.hag.simba.kontrakt.resultat.soeknad

import kotlinx.serialization.builtins.PairSerializer
import kotlinx.serialization.builtins.TripleSerializer
import no.nav.hag.simba.kontrakt.domene.forespoersel.Forespoersel
import no.nav.hag.simba.kontrakt.domene.soeknad.Soeknad
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.list

val hentSoeknaderResultatSerializer =
    TripleSerializer(
        aSerializer =
            PairSerializer(
                keySerializer = UuidSerializer,
                valueSerializer = Forespoersel.serializer(),
            ).list(),
        bSerializer = Soeknad.Arbeidstaker.serializer().list(),
        cSerializer = Soeknad.Behandlingsdager.serializer().list(),
    )
