

ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "org.tayvs"
ThisBuild / scalaVersion := "2.13.11"

ThisBuild / resolvers += "Artima Maven Repository" at "https://repo.artima.com/releases"

lazy val future = (project in file("."))
  .settings(
    libraryDependencies ++= Seq(
//      "org.scalactic" %% "scalactic" % "3.2.16" % "test",
      "org.scalatest" %% "scalatest" % "3.2.16" % "test"
    )
  )

lazy val test = (project in file("test"))
  .settings(
    libraryDependencies += "org.tayvs" %% "future" % "0.1.0-SNAPSHOT",
    dependencyOverrides += "org.tayvs" %% "future" % "0.1.0-SNAPSHOT",
    Compile / managedClasspath += Attributed.blank(file("lib/scala")),

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