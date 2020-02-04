package no.nav.dagpenger.journalføring.ferdigstill

import no.nav.dagpenger.events.Packet

sealed class BehandlingStatus {
    object Mottatt : BehandlingStatus()
    object BehandlingStartet : BehandlingStatus()
    object ManueltBehandlet : BehandlingStatus()
    object AutomatiskBehandlet : BehandlingStatus()
    object Ferdig : BehandlingStatus()
}

sealed class Action {
    abstract val packet: Packet

    data class BestillOppgave(override val packet: Packet) : Action()
    data class Ferdigstill(override val packet: Packet) : Action()
    data class BehandleManuelt(override val packet: Packet) : Action()
}

sealed class Event {
    object Suksess : Event()
    data class Feilet(val grunn: String) : Event()
}

data class Transition(val toState: BehandlingStatus, val transitionActions: List<Action>) {
    fun actions(vararg additionalActions: Action) = this.copy(transitionActions = transitionActions + additionalActions)

    companion object {
        fun to(state: BehandlingStatus): Transition = Transition(toState = state, transitionActions = emptyList())
    }
}

object StateMachine {

    fun transition(behandlingStatus: BehandlingStatus, event: Event, packet: Packet): Transition {

        return when (behandlingStatus) {
            is BehandlingStatus.Mottatt ->

                return when (event) {
                    is Event.Suksess -> Transition.to(BehandlingStatus.BehandlingStartet).actions(
                        Action.BestillOppgave(
                            packet
                        )
                    )
                    is Event.Feilet -> Transition.to(BehandlingStatus.ManueltBehandlet).actions(
                        Action.BehandleManuelt(
                            packet
                        )
                    )
                }

            is BehandlingStatus.BehandlingStartet ->
                return when (event) {
                    is Event.Suksess -> Transition.to(BehandlingStatus.AutomatiskBehandlet).actions(
                        Action.Ferdigstill(
                            packet
                        )
                    )
                    is Event.Feilet -> Transition.to(BehandlingStatus.AutomatiskBehandlet).actions(
                        Action.Ferdigstill(
                            packet
                        )
                    )
                }
            is BehandlingStatus.ManueltBehandlet -> Transition.to(BehandlingStatus.Ferdig)
            is BehandlingStatus.AutomatiskBehandlet -> Transition.to(BehandlingStatus.Ferdig)
            is BehandlingStatus.Ferdig -> Transition.to(BehandlingStatus.Ferdig)
        }
    }
}