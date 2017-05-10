val commonSettings = Seq(
  version := "1.0",
  scalaVersion := "2.11.11",
  crossScalaVersions := Seq("2.10.6", "2.11.11", "2.12.2")
)

lazy val root = project
  .in(file("."))
  .settings(
    commonSettings ++ Seq(name := "future-throttle"): _*
  )

lazy val monix = project
  .in(file("monix"))
  .settings(
    commonSettings ++ Seq(
      name := "future-throttle-monix",
      libraryDependencies ++= Seq("io.monix" %% "monix-eval" % "2.3.0")): _*
  )
