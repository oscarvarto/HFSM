package com.optrak.experiment

import com.optrak.experiment.AlternativeFSM.AFSMSupport

/**
 * Created by oscarvarto on 2014/07/08.
 */
object BeingStudentModule extends AFSMSupport {
  sealed trait NS
  sealed trait SS
  type NormalState = NS
  type SuperState = SS
  case object Final extends NormalState
  sealed trait Msg
  type Message = Msg

  case object Eating extends NormalState
  case object WatchingTV extends NormalState
  case object Studying extends SuperState
  case object Idle extends NormalState
  case object Initial extends NormalState

  type Data = BSMData
  case class BSMData(name: String = "Oscar", age: Int = 31)

  case object Logoff extends Message
  case object StartEating extends Message
  case object StopEating extends Message
  case object StartWatchingTV extends Message
  case object StopWatchingTV extends Message
  case object StartStudying extends Message

  // Suppose that for this particular case the Final StateData depends only on the Message
  val f: Next =
    msg => p2f {
      case _ =>
        val s: S = msg match {
          case Logoff => Final
          case StartEating => Eating
          case StopEating => Idle
          case StartWatchingTV => WatchingTV
          case StopWatchingTV => Idle
          case StartStudying => Studying
        }
        StateData(s, BSMData())
    }

  case class BeingStudentFSM(stateData: SD = StateData(Idle, BSMData())) extends AFSM[BeingStudentFSM] {
    val next: Next = f
    def create(s: SD) = BeingStudentFSM(s)
  }
}
