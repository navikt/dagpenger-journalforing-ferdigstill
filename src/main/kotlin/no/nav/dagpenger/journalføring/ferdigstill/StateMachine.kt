package no.nav.dagpenger.journalføring.ferdigstill

import no.nav.dagpenger.events.Packet
import java.lang.Exception

sealed class BehandlingStatus {
    object Mottatt : BehandlingStatus()
    object HarBestiltOppgave : BehandlingStatus()
    object FerdigStilt : BehandlingStatus()
}

sealed class Action {
    abstract val packet: Packet
    data class BestillOppgave(override val packet: Packet) : Action()
    data class Ferdigstill(override val packet: Packet) : Action()
    data class Manuell(override val packet: Packet) : Action()
}

sealed class Event {
    object Success : Event()
    object Fail : Event()
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
                    is Event.Success -> Transition.to(BehandlingStatus.HarBestiltOppgave).actions(Action.BestillOppgave(packet))
                    is Event.Fail -> Transition.to(BehandlingStatus.HarBestiltOppgave).actions(Action.Manuell(packet))
                }

            is BehandlingStatus.HarBestiltOppgave ->
                return when (event) {
                    is Event.Success -> Transition.to(BehandlingStatus.FerdigStilt).actions(Action.Ferdigstill(packet))
                    is Event.Fail -> throw Exception("")
                }

            is BehandlingStatus.FerdigStilt -> Transition.to(BehandlingStatus.FerdigStilt)
        }
    }
}