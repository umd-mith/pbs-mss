import sbt._, Keys._
import sbtassembly.Plugin._, AssemblyKeys._

object ShelleyMSS extends Build {
  lazy val base: Project = Project(
    id = "pbs-mss",
    base = file("."),
    settings = commonSettings ++ assemblySettings ++ Seq(
      mainClass in assembly := Some(
        "edu.umd.mith.sga.mss.oxford.OxfordImporter"
      )
    )
  )

  def commonSettings = Defaults.defaultSettings ++ Seq(
    organization := "edu.umd.mith",
    version := "0.1.0-SNAPSHOT",
    resolvers ++= Seq(
      "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases"
    ),
    scalaVersion := "2.10.4",
    scalacOptions := Seq(
      "-feature",
      "-deprecation",
      "-unchecked",
      "-language:higherKinds"
    ),
    libraryDependencies ++= Seq(
      "org.apache.sanselan" % "sanselan" % "0.97-incubator",
      "org.clapper" %% "argot" % "1.0.1",
      "org.scalaz" %% "scalaz-core" % "7.0.6",
      "org.scalaz.stream" %% "scalaz-stream" % "snapshot-0.4"
    )
  )
}

