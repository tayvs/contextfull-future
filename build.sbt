

ThisBuild / version := "0.1.0"

ThisBuild / scalaVersion := "2.13.11"

lazy val future = (project in file("."))


lazy val test = (project in file("test"))
  .settings(
    dependencyOverrides += "org.tayvs" %% "future" % "0.1.0"
  )
//  .dependsOn(future)
//  .copy(dependencyOverrides = dependencyOverrides += future)
//  .copy(dependencies = dependencyOverrides += future)
//  .settings(
//    dependencyOverrides += future,
//    libraryDependencies += future
//  )
//  .ove