name := """scala-cars"""
organization := "com.icebear"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.13.8"

libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "5.0.0" % Test

val circeVersion = "0.14.1"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)

libraryDependencies += "org.apache.spark" %% "spark-sql" % "3.2.1"
libraryDependencies += "mysql" % "mysql-connector-java" % "8.0.28"

routesGenerator := InjectedRoutesGenerator

//libraryDependencies += "com.github.nscala-time" %% "nscala-time" % "1.8.0"

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "com.icebear.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "com.icebear.binders._"
