package com.optrak.experiment

/**
 * Created by oscarvarto on 2014/07/17.
 */

import com.optrak.experiment.AlternativeFSM._
import com.optrak.experiment.{BeingStudentModule => BSM, SettingTasksModule => TM, StudyingModule => SM}
import com.optrak.experiment.BeingStudentModule._, com.optrak.experiment.StudyingModule._, com.optrak.experiment.SettingTasksModule._

/**
 * Created by oscarvarto on 2014/07/11.
 */
case class HFSM2(fsms: List[AnyRef]) {
  val fsmsIsEmptyError = "HFSM.process(msg) called, but fsms is empty"
  import scalaz.std.option.optionSyntax._

  def processor[A : Manifest]: A = fsms.headOption.err(fsmsIsEmptyError) match {
    case fsm: A => fsm
    case wrongFSM =>
      val exceptionMsg = s"fsms.head=$wrongFSM cannot process the message sent to it"
      throw new IllegalArgumentException(exceptionMsg)
  }

  def process(msg: BSM.Msg): HFSM2 = {
    import BSM._
    val (nextFSM, transOpt) = processor[BeingStudentFSM].process(msg)
    (nextFSM.stateData.state, transOpt) match {
      case (Left(BSM.Final), Some(Normal2Normal)) => HFSM2(List.empty)
      case (Left(BSM.Final), Some(Super2Normal)) => HFSM2(List.empty)
      case (Left(ns), _) => HFSM2( List(nextFSM) )
      case (Right(Studying), _) => HFSM2( List(StudyingFSM(), nextFSM) )
    }
  }

  def process(msg: SM.Msg): HFSM2 = {
    import SM._
    val (nextFSM, transOpt) = processor[StudyingFSM].process(msg)
    (nextFSM.stateData.state, transOpt) match {
      case (Left(SM.Final), Some(Normal2Normal)) => HFSM2(fsms.tail)
      case (Left(SM.Final), Some(Super2Normal)) => HFSM2(fsms.tail)
      case (Left(ns), _) => HFSM2(nextFSM :: fsms.tail)
      case (Right(SettingTasks), _) => HFSM2(SettingTasksFSM() :: nextFSM :: fsms.tail)
    }
  }


  def process(msg: TM.Msg): HFSM2 = {
    import TM._
    val (nextFSM, transOpt) = processor[SettingTasksFSM].process(msg)
    (nextFSM.stateData.state, transOpt) match {
      case (Left(TM.Final), Some(Normal2Normal)) => HFSM2(fsms.tail)
      case (Left(TM.Final), Some(Super2Normal)) => HFSM2(fsms.tail)
      case (_, _) => HFSM2(nextFSM :: fsms.tail)
    }
  }
}