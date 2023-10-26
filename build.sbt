

ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "org.tayvs"
ThisBuild / scalaVersion := "2.13.12"

ThisBuild / resolvers += "Artima Maven Repository" at "https://repo.artima.com/releases"

lazy val future = (project in file("."))
  .enablePlugins(JmhPlugin)
  .settings(
    libraryDependencies ++= Seq(
//      "org.scalactic" %% "scalactic" % "3.2.16" % "test",
      "org.scalatest" %% "scalatest" % "3.2.16" % "test"
    )
  )

lazy val bench = project
  .dependsOn(future % "test->test")
  .enablePlugins(JmhPlugin)
  .settings(
    Jmh / sourceDirectory := (Test / sourceDirectory).value,
    Jmh / classDirectory := (Test / classDirectory).value,
    Jmh / dependencyClasspath := (Test / dependencyClasspath).value,
    // rewire tasks, so that 'bench/Jmh/run' automatically invokes 'bench/Jmh/compile' (otherwise a clean 'bench/Jmh/run' would fail)
    Jmh / compile := (Jmh / compile).dependsOn(Test / compile).value,
    Jmh / run := (Jmh / run).dependsOn(Jmh / compile).evaluated
  )

lazy val futureOverride = (project in file("future-override"))
  .dependsOn(future)

lazy val test = (project in file("test"))
  .settings(
    libraryDependencies += "org.tayvs" %% "futureoverride" % "0.1.0-SNAPSHOT",
    dependencyOverrides += "org.tayvs" %% "futureoverride" % "0.1.0-SNAPSHOT",
//    Compile / managedClasspath += Attributed.blank(file("lib/scala")),

    assembly / assemblyMergeStrategy := {
      case PathList("scala", "concurrent", _*) => MergeStrategy.first
      case x =>
        val oldStrategy = (assembly / assemblyMergeStrategy).value
        if (oldStrategy == MergeStrategy.deduplicate)
          MergeStrategy.first
        else
          oldStrategy(x)
    }

    //    managedClasspath / excludeFilter := "Future$.scala"
    //    classPath
    //    dependencyClasspath
  )