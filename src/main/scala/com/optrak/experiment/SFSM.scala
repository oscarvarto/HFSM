package com.optrak.experiment

import com.optrak.experiment.AlternativeFSM._
import com.optrak.experiment.{BeingStudentModule => BSM, SettingTasksModule => TM, StudyingModule => SM}
import BSM._, TM._, SM._
import shapeless.ops.coproduct.Selector

/**
 * Created by oscarvarto on 2014/07/08.
 */
object SFSM extends App {

  import shapeless._

  type BST = BeingStudentFSM :+: StudyingFSM :+: SettingTasksFSM :+: CNil

  implicit def beingStudentFSM2BST(fsm: BeingStudentFSM): BST = Coproduct[BST](fsm)
  implicit def studyingFSM2PST(fsm: StudyingFSM): BST = Coproduct[BST](fsm)
  implicit def settingTasksFSM2PST(fsm: SettingTasksFSM): BST = Coproduct[BST](fsm)

  type M = BeingStudentModule.Message :+: StudyingModule.Message :+: SettingTasksModule.Message :+: CNil
  implicit def bsmMsg2M(msg: BeingStudentModule.Message): M = Coproduct[M](msg)
  implicit def smMsg2M(msg: StudyingModule.Message): M = Coproduct[M](msg)
  implicit def tmMsg2M(msg: SettingTasksModule.Message): M = Coproduct[M](msg)

  case class HFSM(fsms: List[BST]) {
    private def process(msg: M) = {
      val processor = fsms.headOption

      object f extends Poly1 {
        implicit def caseB = at[BeingStudentModule.Message] { message =>
          for {
            fsm <- processor
            beingStudentFSM <- fsm.select[BeingStudentFSM]
          } yield {
            val (nextFSM, transOpt) = beingStudentFSM.process(message)
            val hfsm = (nextFSM.stateData.state, transOpt) match {
              case (Left(BSM.Final), Some(Normal2Normal)) => HFSM(List.empty)
              case (Left(BSM.Final), Some(Super2Normal)) => HFSM(List.empty)
              case (Left(s), _) => HFSM(List(nextFSM))
              case (Right(_), _) => HFSM(List(StudyingFSM(), nextFSM))
            }
            (message, hfsm)
          }
        }

        implicit def caseS = at[StudyingModule.Message] { message =>
          for {
            fsm <- processor
            studyingFSM <- fsm.select[StudyingFSM]
          } yield {
            val (nextFSM, transOpt) = studyingFSM.process(message)
            val hfsm = (nextFSM.stateData.state, transOpt) match {
              case (Left(SM.Final), Some(Normal2Normal)) => HFSM(fsms.tail)
              case (Left(SM.Final), Some(Super2Normal)) => HFSM(fsms.tail)
              case (Left(s), _) => HFSM(nextFSM :: fsms.tail)
              case (Right(_), _) => HFSM(List(StudyingFSM(), nextFSM))
            }
            (message, hfsm)
          }
        }

        implicit def caseT = at[SettingTasksModule.Message] { message =>
          for {
            fsm <- processor
            settingTasksFSM <- fsm.select[SettingTasksFSM]
          } yield {
            val (nextFSM, transOpt) = settingTasksFSM.process(message)
            val hfsm = (nextFSM.stateData.state, transOpt) match {
              case (Left(TM.Final), Some(Normal2Normal)) => HFSM(fsms.tail)
              case (Left(TM.Final), Some(Super2Normal)) => HFSM(fsms.tail)
              case (_, _) => HFSM(nextFSM :: fsms.tail)
            }
            (message, hfsm)
          }
        }
      }

      msg map f
    }

    def process(msg: BSM.Msg): Option[HFSM] = {
      process(msg : M).select[Option[(BSM.Msg, HFSM)]].flatten map { _._2}
    }

    def process(msg: SM.Msg): Option[HFSM] = {
      process(msg: M).select[Option[(SM.Msg, HFSM)]].flatten map { _._2}
    }

    def process(msg: TM.Msg): Option[HFSM] = {
      process(msg: M).select[Option[(TM.Msg, HFSM)]].flatten map { _._2}
    }
  }

  val fsms: List[BST] =
    List(
      StudyingFSM(),
      BeingStudentFSM( BSM.StateData(Studying, BSMData()) )
    )
  val hfsm = new HFSM(fsms)

  val nhfsm: Option[HFSM] = hfsm.process(ChooseBook("Bible"))
  nhfsm foreach println
}
