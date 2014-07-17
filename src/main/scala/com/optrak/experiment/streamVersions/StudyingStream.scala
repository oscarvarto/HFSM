package com.optrak.experiment.streamVersions

import scalaz.{Middle3, Right3, Left3, Either3}
import scalaz.std.option._

/**
 * Created by oscarvarto on 2014/07/16.
 */
object StudyingStream extends App {
  //sealed trait StudyingMsg

  sealed trait NormalState
  case object Thinking extends NormalState
  case object Reading extends NormalState

  sealed trait SuperState
  case object SettingTasks extends SuperState

  case object Final

  type S = Either3[NormalState, SuperState, Final.type]

  import Either3._
  implicit def normal2S(ns: NormalState): S = left3(ns)
  implicit def super2S(ss: SuperState): S = middle3(ss)
  implicit def final2S(f: Final.type): S = right3(f)


  type Book = String
  trait Place
  case object Home extends Place
  case object School extends Place

  trait Msg
  case class ChooseBook(book: Book) extends Msg
  case object StartReading extends Msg
  case object StopReading extends Msg
  case object SetTasks extends Msg
  case object StopStudying extends Msg

  case class SMData(currentBook: Option[Book] = none, place: Place = School)

  case class StudyingMachine(state: S = Thinking, data: SMData = SMData())

  // For handling ChooseBook(book):
  // + you must have thought which one you want, s === Left(Thinking)
  // + you should not have a current book, data.currentBook === None
  // + it doesn't matter where you choose the book.
  type SDF = StudyingMachine => StudyingMachine
  val chooseBook: Book => SDF = book => {
    case m @ StudyingMachine(Left3(Thinking), d @ SMData(None, _)) =>
      m.copy(data = d.copy(currentBook = Some(book)))
    case other => other
  }

  // Handling StartReading:
  // + You must have a book to read.
  val startReading: SDF = {
    case StudyingMachine(_, data @ SMData(Some(book), _)) => StudyingMachine(Reading, data)
    case other => other
  }

  // Handling StopReading:
  // + You must be reading a book so that this message changes something
  // + Next state is Thinking
  val stopReading: SDF = {
    case StudyingMachine(Left3(Reading), data @ SMData(Some(book), _)) => StudyingMachine(Left3(Thinking), data.copy(currentBook = none))
    case other => other
  }

  // Handling SetTasks:
  // + This message makes sense only if you are at school
  // + Given that state SettingTasks is a SuperState, you must be in a different State to make a change
  val setTasks: SDF = {
    case StudyingMachine(s, data @ SMData(_, School)) if s != Middle3(SettingTasks) => StudyingMachine(Middle3(SettingTasks), data)
    case other => other
  }

  // Handling StopStudying:
  val stopStudying: SDF = {
    case StudyingMachine(_, data) => StudyingMachine(Final, data)
    case other => other
  }

  type Next = Msg => StudyingMachine => StudyingMachine
  val f: Next = {
    case ChooseBook(book) => chooseBook(book)
    case StartReading => startReading
    case StopReading => stopReading
    case SetTasks => setTasks
    case StopStudying => stopStudying
  }
  val next = Function.uncurried(f)

  import scalaz.stream._
  import Process._
  import scalaz.concurrent.Task

  val p1: Process[Task, Msg] = Process(ChooseBook("FP in Scala"), StartReading, StopReading)

  def MyZipWithState[A,B](z: B)(next: (A, B) => B): Process.Process1[A,(A,B)] = {
    def go(b: B): Process.Process1[A,(A,B)] =
      await1[A].flatMap { a =>
        val nextB = next(a, b)
        emit((a, nextB)) fby go(nextB)
      }
    go(z)
  }

  val p2: Process[Task, (Msg, StudyingMachine)] = p1 |> MyZipWithState(StudyingMachine())(next)
  val maybeM = p2.runLast.run
  maybeM foreach println
}
