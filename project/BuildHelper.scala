import sbt._
import Keys._
import sbtbuildinfo._
import dotty.tools.sbtplugin.DottyPlugin.autoImport._
import BuildInfoKeys._
import scalafix.sbt.ScalafixPlugin.autoImport._

object BuildHelper {
  // Keep this consistent with the version in .github/workflows/ci.yml
  val Scala211   = "2.11.12"
  val Scala212   = "2.12.13"
  val Scala213   = "2.13.4"
  val ScalaDotty = "3.0.0-M3"

  private val SilencerVersion = "1.7.2"

  val compileOnlyDeps = Seq("com.github.ghik" % "silencer-lib" % SilencerVersion % Provided cross CrossVersion.full)

  private val stdOptions = Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-unchecked"
  ) ++ {
    if (sys.env.contains("CI")) {
      Seq("-Xfatal-warnings")
    } else {
      Nil // to enable Scalafix locally
    }
  }

  private val std2xOptions = Seq(
    "-language:higherKinds",
    "-language:existentials",
    "-explaintypes",
    "-Yrangepos",
    "-Xlint:_,-missing-interpolator,-type-parameter-shadow",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard"
  )

  private def optimizerOptions(optimize: Boolean) =
    if (optimize)
      Seq(
        "-opt:l:inline",
        "-opt-inline-from:zio.internal.**"
      )
    else Nil

  def buildInfoSettings(packageName: String) = Seq(
    buildInfoKeys := Seq[BuildInfoKey](organization, moduleName, name, version, scalaVersion, sbtVersion, isSnapshot),
    buildInfoPackage := packageName
  )

  def extraOptions(scalaVersion: String, isDotty: Boolean, optimize: Boolean) =
    CrossVersion.partialVersion(scalaVersion) match {
      case _ if isDotty  =>
        Seq(
          "-language:implicitConversions",
          "-Xignore-scala2-macros"
        )
      case Some((2, 13)) =>
        Seq(
          "-Ywarn-unused:params,-implicits"
        ) ++ std2xOptions ++ optimizerOptions(optimize)
      case Some((2, 12)) =>
        Seq(
          "-opt-warnings",
          "-Ywarn-extra-implicit",
          "-Ywarn-unused:_,imports",
          "-Ywarn-unused:imports",
          "-Ypartial-unification",
          "-Yno-adapted-args",
          "-Ywarn-inaccessible",
          "-Ywarn-infer-any",
          "-Ywarn-nullary-override",
          "-Ywarn-nullary-unit",
          "-Ywarn-unused:params,-implicits",
          "-Xfuture",
          "-Xsource:2.13",
          "-Xmax-classfile-name",
          "242"
        ) ++ std2xOptions ++ optimizerOptions(optimize)
      case Some((2, 11)) =>
        Seq(
          "-Ypartial-unification",
          "-Yno-adapted-args",
          "-Ywarn-inaccessible",
          "-Ywarn-infer-any",
          "-Ywarn-nullary-override",
          "-Ywarn-nullary-unit",
          "-Xexperimental",
          "-Ywarn-unused-import",
          "-Xfuture",
          "-Xsource:2.13",
          "-Xmax-classfile-name",
          "242"
        ) ++ std2xOptions
      case _             => Seq.empty
    }

  def stdSettings(prjName: String) = Seq(
    name := s"$prjName",
    scalacOptions := stdOptions,
    crossScalaVersions := Seq("2.13.4", "2.12.13", "2.11.12"),
    scalaVersion in ThisBuild := crossScalaVersions.value.head,
    scalacOptions := stdOptions ++ extraOptions(scalaVersion.value, isDotty.value, optimize = !isSnapshot.value),
    libraryDependencies ++= {
      if (isDotty.value)
        Seq(
          ("com.github.ghik" % s"silencer-lib_$Scala213" % SilencerVersion % Provided)
            .withDottyCompat(scalaVersion.value)
        )
      else
        Seq(
          "com.github.ghik" % "silencer-lib" % SilencerVersion % Provided cross CrossVersion.full,
          compilerPlugin("com.github.ghik" % "silencer-plugin" % SilencerVersion cross CrossVersion.full),
          compilerPlugin("org.typelevel"  %% "kind-projector"  % "0.11.3" cross CrossVersion.full)
        )
    },
    semanticdbEnabled := !isDotty.value, // enable SemanticDB
    semanticdbOptions += "-P:semanticdb:synthetics:on",
    semanticdbVersion := scalafixSemanticdb.revision, // use Scalafix compatible version
    ThisBuild / scalafixScalaBinaryVersion := CrossVersion.binaryScalaVersion(scalaVersion.value),
    ThisBuild / scalafixDependencies ++= List(
      "com.github.liancheng" %% "organize-imports" % "0.4.4",
      "com.github.vovapolu"  %% "scaluzzi"         % "0.1.16"
    ),
    parallelExecution in Test := true,
    incOptions ~= (_.withLogRecompileOnMacro(false))
  )
}
