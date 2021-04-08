import sbt.Keys._
import sbt._
import sbtbuildinfo.BuildInfoKeys._
import sbtbuildinfo._
import scalafix.sbt.ScalafixPlugin.autoImport._

object BuildHelper {
  private val versions: Map[String, String] = {
    import org.snakeyaml.engine.v2.api.{Load, LoadSettings}

    import java.util.{List => JList, Map => JMap}
    import scala.jdk.CollectionConverters._

    val doc  = new Load(LoadSettings.builder().build())
      .loadFromReader(scala.io.Source.fromFile(".github/workflows/ci.yml").bufferedReader())
    val yaml = doc.asInstanceOf[JMap[String, JMap[String, JMap[String, JMap[String, JMap[String, JList[String]]]]]]]
    val list = yaml.get("jobs").get("test").get("strategy").get("matrix").get("scala").asScala
    list.map(v => (v.split('.').take(2).mkString("."), v)).toMap
  }
  val Scala211: String   = versions("2.11")
  val Scala212: String   = versions("2.12")
  val Scala213: String   = versions("2.13")
  val ScalaDotty: String = versions("3.0")

  private val SilencerVersion = "1.7.3"

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

  def extraOptions(scalaVersion: String, optimize: Boolean) =
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((3, 0))  =>
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
    crossScalaVersions := Seq(Scala211, Scala212, Scala213),
    ThisBuild / scalaVersion := Scala213,
    scalacOptions := stdOptions ++ extraOptions(scalaVersion.value, optimize = !isSnapshot.value),
    libraryDependencies ++= {
      if (scalaVersion.value == ScalaDotty)
        Seq(
          ("com.github.ghik" % s"silencer-lib_$Scala213" % SilencerVersion % Provided)
            .cross(CrossVersion.for3Use2_13)
        )
      else
        Seq(
          "com.github.ghik" % "silencer-lib" % SilencerVersion % Provided cross CrossVersion.full,
          compilerPlugin("com.github.ghik" % "silencer-plugin" % SilencerVersion cross CrossVersion.full),
          compilerPlugin("org.typelevel"  %% "kind-projector"  % "0.11.3" cross CrossVersion.full)
        )
    },
    semanticdbEnabled := scalaVersion.value != ScalaDotty, // enable SemanticDB
    semanticdbOptions += "-P:semanticdb:synthetics:on",
    semanticdbVersion := scalafixSemanticdb.revision, // use Scalafix compatible version
    ThisBuild / scalafixScalaBinaryVersion := CrossVersion.binaryScalaVersion(scalaVersion.value),
    ThisBuild / scalafixDependencies ++= List(
      "com.github.liancheng" %% "organize-imports" % "0.5.0",
      "com.github.vovapolu"  %% "scaluzzi"         % "0.1.18"
    ),
    Test / parallelExecution := true,
    incOptions ~= (_.withLogRecompileOnMacro(false))
  )
}
