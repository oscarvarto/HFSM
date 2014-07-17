name := "HFSM"

version := "1.0"

scalaVersion := "2.11.1"

scalacOptions ++= Seq(
  "-language:_",
  "-feature"
)

resolvers += "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases"

libraryDependencies ++= Seq(
  "org.scalaz" %% "scalaz-core" % "7.0.6",
  "com.chuusai" % "shapeless_2.11" % "2.0.0",
  "org.scalaz.stream" %% "scalaz-stream" % "0.4.1",
  "org.specs2" % "specs2_2.11" % "2.3.13" % "test"
)
    
