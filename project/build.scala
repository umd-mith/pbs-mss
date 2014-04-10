import sbt._, Keys._

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
      "org.scalaz" %% "scalaz-core" % "7.0.6",
      "org.scalaz.stream" %% "scalaz-stream" % "snapshot-0.4"
    )
  )
}

