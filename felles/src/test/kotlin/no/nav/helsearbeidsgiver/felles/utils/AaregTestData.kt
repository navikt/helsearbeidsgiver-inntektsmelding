package no.nav.helsearbeidsgiver.felles.utils

import no.nav.helsearbeidsgiver.felles.Ansettelsesperiode
import no.nav.helsearbeidsgiver.felles.Arbeidsforhold
import no.nav.helsearbeidsgiver.felles.Arbeidsgiver
import no.nav.helsearbeidsgiver.felles.PeriodeNullable
import java.time.LocalDate
import java.time.LocalDateTime

object AaregTestData {
    val arbeidsgiver = Arbeidsgiver("ORG", "123456789")

    val evigArbeidsForholdListe =
        listOf(
            Arbeidsforhold(
                arbeidsgiver = arbeidsgiver,
                ansettelsesperiode = Ansettelsesperiode(PeriodeNullable(LocalDate.MIN, LocalDate.MAX)),
                registrert = LocalDateTime.MIN,
            ),
        )

    val avsluttetArbeidsforholdListe =
        listOf(
            Arbeidsforhold(
                arbeidsgiver = arbeidsgiver,
                ansettelsesperiode =
                    Ansettelsesperiode(
                        PeriodeNullable(
                            LocalDate.MIN,
                            LocalDate.of(2021, 2, 5),
                        ),
                    ),
                registrert = LocalDateTime.now(),
            ),
        )

    val pågåendeArbeidsforholdListe =
        listOf(
            Arbeidsforhold(
                arbeidsgiver = arbeidsgiver,
                ansettelsesperiode =
                    Ansettelsesperiode(
                        PeriodeNullable(
                            LocalDate.of(2021, 2, 5),
                            null,
                        ),
                    ),
                registrert = LocalDateTime.now(),
            ),
        )
    val arbeidsforholdMedSluttDato =
        listOf(
            Arbeidsforhold(
                arbeidsgiver = arbeidsgiver,
                ansettelsesperiode =
                    Ansettelsesperiode(
                        PeriodeNullable(
                            LocalDate.of(2004, 6, 1),
                            LocalDate.of(2004, 6, 30),
                        ),
                    ),
                registrert = LocalDateTime.now(),
            ),
            Arbeidsforhold(
                arbeidsgiver = arbeidsgiver,
                ansettelsesperiode =
                    Ansettelsesperiode(
                        PeriodeNullable(
                            LocalDate.of(2004, 9, 1),
                            LocalDate.of(2004, 9, 30),
                        ),
                    ),
                registrert = LocalDateTime.now(),
            ),
            Arbeidsforhold(
                arbeidsgiver = arbeidsgiver,
                ansettelsesperiode =
                    Ansettelsesperiode(
                        PeriodeNullable(
                            LocalDate.of(2005, 1, 1),
                            LocalDate.of(2005, 2, 28),
                        ),
                    ),
                registrert = LocalDateTime.now(),
            ),
            Arbeidsforhold(
                arbeidsgiver = arbeidsgiver,
                ansettelsesperiode =
                    Ansettelsesperiode(
                        PeriodeNullable(
                            LocalDate.of(2005, 9, 6),
                            LocalDate.of(2007, 12, 31),
                        ),
                    ),
                registrert = LocalDateTime.now(),
            ),
            Arbeidsforhold(
                arbeidsgiver = arbeidsgiver,
                ansettelsesperiode =
                    Ansettelsesperiode(
                        PeriodeNullable(
                            LocalDate.of(2008, 6, 16),
                            LocalDate.of(2008, 8, 3),
                        ),
                    ),
                registrert = LocalDateTime.now(),
            ),
            Arbeidsforhold(
                arbeidsgiver = arbeidsgiver,
                ansettelsesperiode =
                    Ansettelsesperiode(
                        PeriodeNullable(
                            LocalDate.of(2009, 3, 5),
                            LocalDate.of(2010, 8, 30),
                        ),
                    ),
                registrert = LocalDateTime.now(),
            ),
            Arbeidsforhold(
                arbeidsgiver = arbeidsgiver,
                ansettelsesperiode =
                    Ansettelsesperiode(
                        PeriodeNullable(
                            LocalDate.of(2010, 11, 26),
                            LocalDate.of(2011, 9, 4),
                        ),
                    ),
                registrert = LocalDateTime.now(),
            ),
            Arbeidsforhold(
                arbeidsgiver = arbeidsgiver,
                ansettelsesperiode =
                    Ansettelsesperiode(
                        PeriodeNullable(
                            LocalDate.of(2011, 9, 5),
                            LocalDate.of(2013, 3, 30),
                        ),
                    ),
                registrert = LocalDateTime.now(),
            ),
            Arbeidsforhold(
                arbeidsgiver = arbeidsgiver,
                ansettelsesperiode =
                    Ansettelsesperiode(
                        PeriodeNullable(
                            LocalDate.of(2013, 3, 31),
                            LocalDate.of(2014, 1, 1),
                        ),
                    ),
                registrert = LocalDateTime.now(),
            ),
            Arbeidsforhold(
                arbeidsgiver = arbeidsgiver,
                ansettelsesperiode =
                    Ansettelsesperiode(
                        PeriodeNullable(
                            LocalDate.of(2013, 3, 31),
                            LocalDate.of(2013, 3, 31),
                        ),
                    ),
                registrert = LocalDateTime.now(),
            ),
            Arbeidsforhold(
                arbeidsgiver = arbeidsgiver,
                ansettelsesperiode =
                    Ansettelsesperiode(
                        PeriodeNullable(
                            LocalDate.of(2014, 3, 28),
                            LocalDate.of(2014, 5, 31),
                        ),
                    ),
                registrert = LocalDateTime.now(),
            ),
            Arbeidsforhold(
                arbeidsgiver = arbeidsgiver,
                ansettelsesperiode =
                    Ansettelsesperiode(
                        PeriodeNullable(
                            LocalDate.of(2014, 6, 1),
                            LocalDate.of(2022, 4, 30),
                        ),
                    ),
                registrert = LocalDateTime.now(),
            ),
            Arbeidsforhold(
                arbeidsgiver = arbeidsgiver,
                ansettelsesperiode =
                    Ansettelsesperiode(
                        PeriodeNullable(
                            LocalDate.of(2014, 6, 1),
                            LocalDate.of(2014, 12, 31),
                        ),
                    ),
                registrert = LocalDateTime.now(),
            ),
            Arbeidsforhold(
                arbeidsgiver = arbeidsgiver,
                ansettelsesperiode =
                    Ansettelsesperiode(
                        PeriodeNullable(
                            LocalDate.of(2022, 5, 1),
                            null,
                        ),
                    ),
                registrert = LocalDateTime.now(),
            ),
        )
}
