package com.optrak.experiment

import com.optrak.experiment.AlternativeFSM.AFSMSupport
import com.optrak.experiment.Tasks.Task

/**
 * Created by oscarvarto on 2014/07/08.
 */
object SettingTasksModule extends AFSMSupport {
  sealed trait NS
  sealed trait SS
  type NormalState = NS
  type SuperState = SS
  case object Final extends NormalState
  sealed trait Msg
  type Message = Msg

  case object ReviewingTasks extends NormalState

  case object StopSettingTasks extends Message
  case class AppendTask(t: Task) extends Message
  case class TaskModified(newValue: Task, taskIndex: Int) extends Message

  val initTasks = Vector.tabulate(3) {i =>
    val n = i + 1
    Task(n, s"Task #$n")
  }

  type Data = TData
  case class TData(tasks: Vector[Task] = initTasks)

  // Handling StopSettingTasks
  val stopSettingTasks: SDF = p2f { case sd @ StateData(Left(ReviewingTasks), TData(t)) => sd.copy(state = Left(Final)) }

  // Handling AppendTask(t)
  val appendTask: Task => SDF = t => p2f {
    case sd @ StateData(_, TData(tasks)) => sd.copy(data = TData(tasks :+ t))
  }

  // Handling TaskModified(v, i)
  val taskModified: (Task, Int) => SDF = (newTask, taskIndex) => p2f {
    case sd @ StateData(_, TData(tasks)) => sd.copy(data = TData(tasks.updated(taskIndex, newTask)))
  }

  val f: Next = {
    case StopSettingTasks => stopSettingTasks
    case AppendTask(t) => appendTask(t)
    case TaskModified(newValue, taskIndex) => taskModified(newValue, taskIndex)
  }

  case class SettingTasksFSM(stateData: SD = StateData(ReviewingTasks, TData()))
      extends AFSM[SettingTasksFSM] {
    val next: Next = f
    def create(s: SettingTasksModule.SD): SettingTasksFSM = SettingTasksFSM(s)
  }
}
