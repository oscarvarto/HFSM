package com.optrak.experiment

/**
 * Created by oscarvarto on 2014/07/16.
 */
object StateExperiment extends App {
  sealed trait Input
  case object Coin extends Input
  case object Turn extends Input
  case class Machine(locked: Boolean, candies: Int, coins: Int)

  import scalaz.State
  val S = scalaz.StateT.stateMonad[Machine]
  import scalaz.State._
  import scalaz.std.list._

  /**
   * The rules of the machine are as follows:
   * 1. Inserting a coin into a locked machine will cause it to unlock if there is any candy left.
   * 2. Turning the knob on an unlocked machine will cause it to dispense candy and become locked.
   * 3. Turning the knob on a locked machine or inserting a coin into an unlocked machine does nothing.
   * 4. A machine that is out of candy ignores all inputs.
   *
   * The method simulateMachine should operate the machine based on the list of inputs
   * and return the number of coins and candies left in the machine at the end.
   * For example, if the input Machine has 10 coins and 5 candies, and a
   * total of 4 candies are successfully bought, the output should be (14, 1).
   */
  def rules(i: Input): State[Machine, (Int, Int)] = for {
    _ <- modify((m: Machine) => (i, m) match {
      case (_, Machine(_, 0, _)) => m // Rule 4
      case (Coin, Machine(false, _, _)) => m // Rule 3b
      case (Turn, Machine(true, _, _)) => m // Rule 3a
      case (Coin, Machine(true, candy, coin)) => Machine(false, candy, coin + 1) // Rule 1
      case (Turn, Machine(false, candy, coin)) => Machine(true, candy - 1, coin) // Rule 2
    })
    m <- get
  } yield (m.coins, m.candies)

  def simulateMachine(inputs: List[Input]): State[Machine, (Int, Int)] = for {
    _ <- S.sequence(inputs.map(rules))
    m <- get[Machine]
  } yield (m.coins, m.candies)

  println(simulateMachine(List(Coin, Turn, Coin, Turn, Coin, Turn, Coin, Turn))(Machine(true, 5, 10)))

  import scalaz.stream.{process1 => P1, _}
  import scalaz.concurrent.Task

  val p1: Process[Task, Input] = Process(Coin, Turn, Coin, Turn, Coin, Turn, Coin, Turn)

  val next: (Input, Machine) => Machine = { case (i, m) =>
    (i, m) match {
      case (_, Machine(_, 0, _)) => m // Rule 4
      case (Coin, Machine(false, _, _)) => m // Rule 3b
      case (Turn, Machine(true, _, _)) => m // Rule 3a
      case (Coin, Machine(true, candy, coin)) => Machine(false, candy, coin + 1) // Rule 1
      case (Turn, Machine(false, candy, coin)) => Machine(true, candy - 1, coin) // Rule 2
    }
  }
  val p2: Process[Task, (Input, Machine)] = p1 |> P1.zipWithState(Machine(true, 5, 10))(next)
  val maybeM = p2.runLast.run map { case (i, m) => next(i, m) }
  println(maybeM)
}
