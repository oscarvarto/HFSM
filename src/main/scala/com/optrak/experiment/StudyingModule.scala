package com.optrak.experiment

import com.optrak.experiment.AlternativeFSM.AFSMSupport
import scalaz.std.option._

/**
 * Created by oscarvarto on 2014/07/08.
 */
object StudyingModule extends AFSMSupport {
  sealed trait NS
  sealed trait SS
  type NormalState = NS
  type SuperState = SS
  case object Final extends NormalState
  sealed trait Msg
  type Message = Msg

  case object Thinking extends NormalState
  case object Reading extends NormalState
  case object SettingTasks extends SuperState

  type Book = String
  trait Place
  case object Home extends Place
  case object School extends Place

  case class ChooseBook(book: Book) extends Message
  case object StartReading extends Message
  case object StopReading extends Message
  case object SetTasks extends Message
  case object StopStudying extends Message

  type Data = SMData
  case class SMData(currentBook: Option[Book], place: Place = School)

  // == Define rules to handle every message ==

  // For handling ChooseBook(book):
  // + you must have thought which one you want, s === Left(Thinking)
  // + you should not have a current book, data.currentBook === None
  // + it doesn't matter where you choose the book.
  val chooseBook: Book => SDF = book => p2f {
    case statedata @ StateData(Left(Thinking), data @ SMData(None, _)) =>
      statedata.copy(data = data.copy(currentBook = Some(book)))
  }

  // Handling StartReading:
  // + You must have a book to read.
  val startReading: SDF = p2f {
    case StateData(_, data @ SMData(Some(book), _)) => StateData(Reading, data)
  }

  // Handling StopReading:
  // + You must be reading a book so that this message changes something
  // + Next state is Thinking
  val stopReading: SDF = p2f {
    case StateData(Left(Reading), data @ SMData(Some(book), _)) => StateData(Left(Thinking), data.copy(currentBook = none))
  }

  // Handling SetTasks:
  // + This message makes sense only if you are at school
  // + Given that state SettingTasks is a SuperState, you must be in a different State to make a change
  val setTasks: SDF = p2f {
    case StateData(s, data @ SMData(_, School)) if s != Right(SettingTasks) => StateData(Right(SettingTasks), data)
  }

  // Handling StopStudying:
  val stopStudying: SDF = p2f {
    case StateData(_, data) => StateData(Final, data)
  }

  val f: Next = {
    case ChooseBook(book) => chooseBook(book)
    case StartReading => startReading
    case StopReading => stopReading
    case SetTasks => setTasks
    case StopStudying => stopStudying
  }

  case class StudyingFSM(stateData: SD = StateData(Thinking, SMData(None, School))) extends AFSM[StudyingFSM] {
    val next: Next = f
    def create(s: SD) = StudyingFSM(s)
  }
}
