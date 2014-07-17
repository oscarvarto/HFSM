package com.optrak.experiment

import scalaz.std.option._
import scalaz.std.option.optionSyntax._

/**
 * Created by oscarvarto on 2014/07/04.
 */
object AlternativeFSM {
  sealed trait Transition
  case object Normal2Normal extends Transition // NormalState -> NormalState
  case object Normal2Super extends Transition // NormalState -> SuperState
  case object Super2Normal extends Transition // SuperState -> NormalState
  case object Super2Super extends Transition // SuperState -> SuperState

  trait AFSMSupport {
    type NormalState    
    type SuperState
    type S = Either[NormalState, SuperState]
    implicit def normal2FSMState(ns: NormalState): S = Left(ns)
    implicit def super2FSMState(ss: SuperState): S = Right(ss)

    type Message
    type Data

    case class StateData[D](state: S, data: D)
    type SD = StateData[Data]
    type SDF = SD => Option[SD]
    type Next = Message => SDF // Or Message => SD => Option[SD]

    def p2f(pf: PartialFunction[SD, SD]): SDF = pf.lift

    trait AFSM[T] { self: T =>
      def stateData: SD
      def next: Next
      def transitionType(s1: S, s2: S): Transition = (s1, s2) match {
        case (Left(_), Left(_)) => Normal2Normal
        case (Left(_), Right(_)) => Normal2Super
        case (Right(_), Left(_)) => Super2Normal
        case (Right(_), Right(_)) => Super2Super
      }
      def create(s: SD): T
      def process(msg: Message): (T, Option[Transition]) = {
        val newS: Option[SD] = next(msg)(stateData)
        newS.cata(
          s => create(s) -> transitionType(stateData.state, s.state).some,
          self -> none[Transition]
        )
      }
    }
  }
}
