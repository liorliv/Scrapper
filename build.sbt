ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.12.11"

lazy val root = (project in file("."))
  .settings(
    name := "scrapper"
  )

PB.protocVersion := "3.17.3"

Compile / PB.targets := Seq(
  scalapb.gen() -> (Compile / sourceManaged).value / "scalapb"
)

libraryDependencies += "org.scalameta" %% "munit" % "0.7.22" % Test
libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.14.0"
libraryDependencies += "com.typesafe.akka" %% "akka-actor-typed" % "2.6.15"
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % "2.6.15"
libraryDependencies += "com.typesafe.akka" %% "akka-http" % "10.2.6"
libraryDependencies += "org.jsoup" % "jsoup" % "1.14.3"
libraryDependencies ++= Seq(
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
  "io.grpc" % "grpc-netty" % scalapb.compiler.Version.grpcJavaVersion,
  "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion
)


testFrameworks += new TestFramework("munit.Framework")
