import sbt._
import Keys._

object ShelleyMSS extends Build {
  lazy val base: Project = Project(
    id = "pbs-mss",
    base = file("."),
    settings = commonSettings
  )

  def commonSettings = Defaults.defaultSettings ++ Seq(
    organization := "edu.umd.mith",
    version := "0.0.0-SNAPSHOT",
    resolvers ++= Seq(
      Resolver.sonatypeRepo("snapshots"),
      "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases"
    ),
    scalaVersion := "2.10.3",
    scalacOptions := Seq(
      "-feature",
      "-deprecation",
      "-unchecked",
      "-language:higherKinds"
    ),
    libraryDependencies ++= Seq(
      "org.parboiled" %% "parboiled" % "2.0-M2",
      "org.scalaz" %% "scalaz-core" % "7.0.5",
      "org.scalaz.stream" %% "scalaz-stream" % "0.3.1"
    )
  )
}

