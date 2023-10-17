package no.nav.helsearbeidsgiver.felles.rapidsrivers

import jdk.jfr.Experimental

@Experimental
class InputFelter {
    lateinit var IN: List<String>
    lateinit var OUT: List<String>

    fun IN(IN: List<String>): InputFelter {
        this.IN = IN
        return this
    }

    fun OUT(OUT: List<String>): InputFelter {
        this.OUT = OUT
        return this
    }
}
