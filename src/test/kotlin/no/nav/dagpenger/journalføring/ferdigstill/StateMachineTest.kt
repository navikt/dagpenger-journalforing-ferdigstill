package no.nav.dagpenger.journalføring.ferdigstill

import io.kotlintest.matchers.collections.shouldBeEmpty
import io.kotlintest.shouldBe
import no.nav.dagpenger.events.Packet
import org.junit.Test

internal class StateMachineTest(){

    @Test
    fun ` Skal kunne håndtere mottatt ved initiell behandling`(){

        val t  = StateMachine.transition(BehandlingStatus.Mottatt, Event.Suksess,  Packet())
        t.toState shouldBe BehandlingStatus.BehandlingStartet
        t.transitionActions.map { it.javaClass.simpleName }  shouldBe listOf("BestillOppgave")


    }

    @Test
    fun ` Skal kunne håndtere mottatt ved feilet behandling`(){

        val t  = StateMachine.transition(BehandlingStatus.Mottatt, Event.Feilet("TEST"),  Packet())
        t.toState shouldBe BehandlingStatus.ManueltBehandlet
        t.transitionActions.map { it.javaClass.simpleName }  shouldBe listOf("BehandleManuelt")


    }

    @Test
    fun ` Skal kunne ferdigstille ved initiell behandling ` (){

        val t  = StateMachine.transition(BehandlingStatus.BehandlingStartet, Event.Suksess, Packet())
        t.toState shouldBe BehandlingStatus.AutomatiskBehandlet
        t.transitionActions.map { it.javaClass.simpleName }  shouldBe listOf("Ferdigstill")

    }

    @Test
    fun ` Skal kunne ferdigstille ved feilet ferdigstilling ` (){

        val t  = StateMachine.transition(BehandlingStatus.BehandlingStartet, Event.Feilet("TEST"), Packet())
        t.toState shouldBe BehandlingStatus.AutomatiskBehandlet
        t.transitionActions.map { it.javaClass.simpleName }  shouldBe listOf("Ferdigstill")

    }


    @Test
    fun ` Automatisk behandlet til ferdig ` (){

        val t  = StateMachine.transition(BehandlingStatus.AutomatiskBehandlet, Event.Suksess, Packet())
        t.toState shouldBe BehandlingStatus.Ferdig
        t.transitionActions.shouldBeEmpty()

    }

    @Test
    fun ` Manuelt behandlet til ferdig ` (){

        val t  = StateMachine.transition(BehandlingStatus.ManueltBehandlet, Event.Suksess, Packet())
        t.toState shouldBe BehandlingStatus.Ferdig
        t.transitionActions.shouldBeEmpty()

    }



}