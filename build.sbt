import BuildHelper._

inThisBuild(
  List(
    organization := "dev.zio",
    homepage     := Some(url("https://zio.dev")),
    licenses     := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers   := List(
      Developer(
        "jdegoes",
        "John De Goes",
        "john@degoes.net",
        url("http://degoes.net")
      )
    )
  )
)

addCommandAlias("fix", "; all compile:scalafix test:scalafix; all scalafmtSbt scalafmtAll")
addCommandAlias("check", "; scalafmtSbtCheck; scalafmtCheckAll; compile:scalafix --check; test:scalafix --check")

val zioVersion = "2.0.0-RC1"

lazy val guava = project
  .in(file("."))
  .enablePlugins(BuildInfoPlugin)
  .settings(stdSettings("zio-interop-guava"))
  .settings(buildInfoSettings("zio.interop.guava"))
  .settings(testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"))
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio"         %% "zio"          % zioVersion,
      "com.google.guava" % "guava"        % "31.0.1-jre",
      "dev.zio"         %% "zio-test"     % zioVersion % Test,
      "dev.zio"         %% "zio-test-sbt" % zioVersion % Test
    )
  )
  .enablePlugins(BuildInfoPlugin)
