Please take some time to read the following. My goal is to be able to do the above using scalaz and scalaz-stream.
A beginning is here:
https://github.com/oscarvarto/HFSM/blob/master/src/main/scala/com/optrak/experiment/streamVersions/StudyingStream.scala

HFSM
====

The smaller constituents FSMs are constructed using `trait AFSMSupport`, here:
https://github.com/oscarvarto/HFSM/blob/master/src/main/scala/com/optrak/experiment/AlternativeFSM.scala

I have used, among other things, the pattern explained in Section 18.13 "Family Polymorphism" of the book "Scala for the Impatient" by Cay S. Horstmann. 

Section 18.13 of the book (page 261 in eBook) explains that abstract types and inner traits can be used to avoid the proliferation of type parameters. `trait AFSMSupport` has the following abstract types:
* `NormalState`
* `SuperState`
* `Msg`
* `Data`

and the following inner trait
* `AFSM[T]` 

and defines additional type aliases, classes and methods using those types. Clearly, writing the above using type parameters would be verbose and cumbersome.

Let's explain the intended usage for the traits and types defined in `AFSMSupport`.

We are using types to differentiate between //normal states// from //super states// in a Hierarchical FSM. That allows to easily write code based on the different types of transitions. See lines 10-14 from the file `AlternativeFSM.scala` for the definition of `trait Transition`.

The type alias `type S = Either[NormalState, SuperState]` in line 19 is used as the type of the state a FSM can be in. For example, for the `StudyingModule` defined using AFSMSupport
https://github.com/oscarvarto/HFSM/blob/master/src/main/scala/com/optrak/experiment/StudyingModule.scala

The `StudyingFSM` defined in line 80 of the file above could be in the states defined in lines 14, 18-20. As we are using type `S` instead of directly `NormalState` or `SuperState`, this FSM could be in one of the following `S`-states:
* `Left(Final): S`
* `Left(Thinking): S` (because `Thinking` is a `NormalState`)
* `Left(Reading): S` (because `Reading` is a `NormalState`)
* `Right(SettingTasks): S` (because `SettingTasks` is a `SuperState`)

Using `S`-values, no matter the names of the states neither the Module we are defining (e.g. `BeingStudentModule`, `StudyingModule`, `SettingTasksModule`), we can define the transition type using `transitionType(s1: S, s2: S)` in lines 36-41 of AlternativeFSM.scala here:
https://github.com/oscarvarto/HFSM/blob/master/src/main/scala/com/optrak/experiment/AlternativeFSM.scala#L36

When a message is processed by a FSM, it returns a tuple of a new FSM (of the same type) and a `Option[Transition]`. See the signature of the process method in line 43 of AlternativeFSM.scala

The reason a message could not trigger a `S`-state transition is because it could only change its `Data`. See `type Data` in line 24. To avoid having too many `S`-states in a multidimensional complex case, we are using the generic `case class StateData[D](state: S, data: D)`. See line 26.

So, actual state information is in the form of `S`-state and `Data`. If we had a function `f` with the following signature
```scala
val f: (Msg, StateData[Data]) => StateData[Data]
```

then it would be possible to define how a FSM processes messages, that is, how it changes its `S`-state and `Data` information in response to a `Msg`.  

To completely define such a function `f`, we could use a (possibly very) big pattern match having all the combinations of
* messages and
* `S`-states and
* `Data`

for each FSM. To keep things more manageable and organized, it would be easier to define a handler for each message and then build a function from these handlers.

To reduce boilerplate, we are taking advantage of `PartialFunction`s. Let's put a simple example. Suppose we want to define a function that handles the message `StopReading`,
for the `NormalStates` and the sole `SuperState` defined in `StudyingModule.scala` (lines 18-20):
  https://github.com/oscarvarto/HFSM/blob/master/src/main/scala/com/optrak/experiment/StudyingModule.scala#L18
and `Data` given by `SMData` (line 34) 

```scala
object Foo extends App {
  import StudyingModule._

  val stopR: StateData[Data] => StateData[Data] = {
    case StateData(Left(Reading), data @ SMData(Some(book), _)) => StateData(Left(Thinking), data.copy(currentBook = None))
  }
  stopR(StateData(Left(Thinking), SMData(None, School)))
}
```

Obviously, despite the code compiles, it fails at runtime because the match is not exhaustive (we are not saying what to do in //every// possibility of the input). To handle every possibility, we could change the above to
```scala
object Foo extends App {
  import StudyingModule._
  val stopR: StateData[Data] => StateData[Data] = {
    case StateData(Left(Reading), data @ SMData(Some(book), _)) => StateData(Left(Thinking), data.copy(currentBook = None))
    case sd => sd
  }
  stopR(StateData(Left(Thinking), SMData(None, School)))
}
```
     
That is, we don't change StateData[Data] when the received message makes no sense for current StateData[Data]. We could avoid repeating this kind of no-op in every handler using `PartialFunction.lift`:
http://www.scala-lang.org/api/current/index.html#scala.PartialFunction@lift:A=%3EOption[B]
The scaladoc for this method says it returns
"a function that takes an argument x to Some(this(x)) if this is defined for x, and to None otherwise"
So, we could write the handlers using `PartialFunction`s and then convert to normal #Function1# using `PartialFunction.lift`. If the final answer is a None, then we could interpret that as "ignore the incoming message and do not change current StateData[Data] information".

To simplify/speed up the writing of these handlers, we are using a simple method that simply calls `lift` on the partial function passed as a parameter:
https://github.com/oscarvarto/HFSM/blob/master/src/main/scala/com/optrak/experiment/AlternativeFSM.scala#L31
(See definition of type alias `SD` and `SDF` in lines 27-28 of `AlternativeFSM.scala`)
       
Now, the above handler could be written like:
```scala
// Handling StopReading:
// + You must be reading a book so that this message changes something
// + Next state is Thinking
val stopReading: SDF = p2f {
  case StateData(Left(Reading), data @ SMData(Some(book), _)) => StateData(Left(Thinking), data.copy(currentBook = none))
}
```
Now let's take a look at the type alias `Next` here:
https://github.com/oscarvarto/HFSM/blob/master/src/main/scala/com/optrak/experiment/AlternativeFSM.scala#L29
This definition could also be written as:
```scala
type Next = Msg => StateData[Data] => Option[StateData[Data]]
```

Here we are //currying// the parameters of the function (intentionally). That allows us to write a handler for each kind of message separately. Take a quick look at lines 36-70 to see all the possible handlers for the messages defined for `StudyingModule`. The complete set of messages is handled by a function of type `Next`. See lines 72-78 for an example for `StudyingModule`.

We said above that //if the final answer is a None, then we could interpret that as "ignore the incoming message and do not change current StateData[Data] information"//. That idea is coded in the `process(Msg)` method in trait `AFSM[T]` in lines 43-49 of `AlternativeFSM.scala`
Notes:
* I am using the syntax `a -> b` as a syntactic sugar for `(a, b)`. That avoids a deep nesting of parens.
* The self type (see line 33 and the following usage of type parameter `T`) allows to get the same type of FSM when `create(StateData[Data])` is called. 

With the above machinery and type plumbing, it is reasonably easy to write separate modules. Take some time to read the kind of code a user of `AlternativeFSM.scala` (that could be considered as "library"-code from this perspective) would have to write for his //modules// (the smaller constituents FSMs):
* `BeingStudentModule`
* `StudyingModule`
* `SettingTasksModule`

What's next? We have to glue together the smaller FSMs to build our (flat implementation of) the HFSM. Shapeless 2.0.0 has been used here, particularly `Coproduct` and `Poly1`. If you need a reminder of this, please read carefully the corresponding sections and examples here:
https://github.com/milessabin/shapeless/wiki/Feature-overview:-shapeless-2.0.0

We are going to use a List as a container for the FSMs. When a FSM makes a transition to a `SuperState`, we should append the corresponding (conceptually, the inner FSM in the HFSM) FSM. When a FSM makes a transition out of a `SuperState` (to a `NormalState` or to a different `SuperState`) we should update the //stack// (implemented as a List of FSMs) of FSMs accordingly, maybe popping a FSM and possibly stacking a new one.

Which is the type of the List of FSMs? For our example, we want the List to have elements of type `BeingStudentFSM` **or** `StudyingFSM` **or** `SettingTasksFSM`. A kind of "sum type" of every possible type for our FSMs is implemented by `shapeless.Coproduct`. For our example:
https://github.com/oscarvarto/HFSM/blob/master/src/main/scala/com/optrak/experiment/SFSM.scala#L15
```scala
type BST = BeingStudentFSM :+: StudyingFSM :+: SettingTasksFSM :+: CNil
```

The basic idea is that the incoming messages should be processed by the head of the list of FSMs. There's no need to pass the message to other FSMs in the tail. But each FSM has a different set of messages that it can process (the `trait Msg` is different for every module). So, we need a polymorphic method (`HFSM.process(M)`) to handle messages of a Coproduct type of the different types of messages. See
https://github.com/oscarvarto/HFSM/blob/master/src/main/scala/com/optrak/experiment/SFSM.scala#L27

Notice our usage of selector syntax to rename imported members in line 4 of the `SFSM.scala` file. See section 7.9 "Renaming and Hiding Members" of the book //"Scala for the Impatient"// for a reminder.

The `case class HFSM` contains a definition of a polymorphic function value `object f extends Poly1` to handle the different types of messages.

Notice the usage of for comprehensions to handle `Option[A]` values. So, the final yielded value (a tuple) should be wrapped in an Option. The different possibilities for the final tuple are (wrapped in an Option):
* `Option[(BeingStudentModule.Msg, HFSM)]` 
* `Option[(StudyingModule.Msg, HFSM)]` 
* `Option[(SettingTasksModule.Msg, HFSM)]`

Then, the return type of the `HFSM.process(M)` method could be understood (the actual type is much more complicated) as something like:
`Option[(BeingStudentModule.Msg, HFSM)] :+: Option[(StudyingModule.Msg, HFSM)] :+: Option[(SettingTasksModule.Msg, HFSM)] :+: CNil`
that is, a Coproduct type.

To be able to call `Coproduct.select[T]` on the answer, we need the constituents of the Coproduct type to be of different types. That is the reason I have returned the incoming message (extending `Msg` **in the corresponding module**) together with the new HFSM. This is exactly what Miles Sabin does in his example of a `Poly1` function value, in the section explaining Coproduct:
https://github.com/milessabin/shapeless/wiki/Feature-overview:-shapeless-2.0.0#coproducts-and-discriminated-unions
```scala
object size extends Poly1 {
  implicit def caseInt = at[Int](i => (i, i))
  implicit def caseString = at[String](s => (s, s.length))
  implicit def caseBoolean = at[Boolean](b => (b, 1))
} 
```

Notice how the second element of the tuple is always an `Int` (in our example, that corresponds to the type `HFSM`).

When calling `Coproduct.select[T]` we get a `Option[T]`. Therefore, given that in our example `T` is one of:
* `Option[(BeingStudentModule.Msg, HFSM)]` 
* `Option[(StudyingModule.Msg, HFSM)]` 
* `Option[(SettingTasksModule.Msg, HFSM)]`

we get an `Option[Option[T]]`. That's why we are calling `Option.flatten`

**IMPORTANT:** It is responsibility of the caller of `HFSM.process(M)` to select the appropriate `T` **when calling `Coproduct.select[T]` ** 

If, after flattening we still get a None value, that must be our fault while programming the logic to handle the stack of FSMs (we might be sending the wrong type of Msg to the current head of the List of FSMs //or// we might have the wrong type of FSM at the current head).

Notice how the smaller modules are completely independent from each other and from the HFSM. It is responsibility of the HFSM to implement the logic that knows about the relationships between the smaller FSMs. For our example, this is done in the definition of the polymorphic function value `object f extends Poly1`.

Taking advantage of the decoupling between different FSMs, we could make proper unit testing of each Module. After fully unit testing each module, we could have more confidence on the integration testing done on HFSM.
