name := "matching"

version := "0.1"

scalaVersion := "2.12.3"

scalacOptions ++= Seq(
  "-deprecation"
  , "-unchecked"
  , "-encoding", "UTF-8"
  , "-Xverify"
  , "-feature"
  , "-language:postfixOps"
)

val cats = "1.0.0-MF"

libraryDependencies ++= Seq(
  "co.fs2" %% "fs2-core" % "0.9.6",
  "org.typelevel" %% "cats-core" % cats,
  "org.scala-stm" %% "scala-stm" % "0.8",
  "org.scalatest" %% "scalatest" % "3.0.1" % "test"
)

fork := true

addCompilerPlugin(
  "org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full
)

javaOptions in reStart ++= Seq("-XX:+UseConcMarkSweepGC","-Xmx4g", "-Xms1g")