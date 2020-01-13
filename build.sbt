val sparkVersion = "2.4.4"
val catsCoreVersion = "2.0.0"
val catsEffectVersion = "2.0.0"
val catsMtlVersion = "0.7.0"
val scalatest = "3.0.8"
val shapeless = "2.3.3"
val scalacheck = "1.14.3"
val irrecVersion = "0.2.1"

lazy val root = Project("frameless", file("." + "frameless")).in(file("."))
  .aggregate(core, cats, dataset, ml, docs)
  .settings(framelessSettings: _*)
  .settings(noPublishSettings: _*)

lazy val core = project
  .settings(name := "frameless-core")
  .settings(framelessSettings: _*)
  .settings(publishSettings: _*)


lazy val cats = project
  .settings(name := "frameless-cats")
  .settings(framelessSettings: _*)
  .settings(publishSettings: _*)
  .settings(
    addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
    scalacOptions += "-Ypartial-unification"
  )
  .settings(libraryDependencies ++= Seq(
    "org.typelevel"    %% "cats-core"      % catsCoreVersion,
    "org.typelevel"    %% "cats-effect"    % catsEffectVersion,
    "org.typelevel"    %% "cats-mtl-core"  % catsMtlVersion,
    "org.typelevel"    %% "alleycats-core" % catsCoreVersion,
    "org.apache.spark" %% "spark-core"     % sparkVersion % "provided",
    "org.apache.spark" %% "spark-sql"      % sparkVersion % "provided"))
  .dependsOn(dataset % "test->test;compile->compile")

lazy val dataset = project
  .settings(name := "frameless-dataset")
  .settings(framelessSettings: _*)
  .settings(framelessTypedDatasetREPL: _*)
  .settings(publishSettings: _*)
  .settings(libraryDependencies ++= Seq(
    "org.apache.spark" %% "spark-core"      % sparkVersion % "provided",
    "org.apache.spark" %% "spark-sql"       % sparkVersion % "provided",
    "net.ceedubs"      %% "irrec-regex-gen" % irrecVersion % Test
  ))
  .dependsOn(core % "test->test;compile->compile")

lazy val ml = project
  .settings(name := "frameless-ml")
  .settings(framelessSettings: _*)
  .settings(framelessTypedDatasetREPL: _*)
  .settings(publishSettings: _*)
  .settings(libraryDependencies ++= Seq(
    "org.apache.spark" %% "spark-core" % sparkVersion % "provided",
    "org.apache.spark" %% "spark-sql"  % sparkVersion % "provided",
    "org.apache.spark" %% "spark-mllib"  % sparkVersion % "provided"
  ))
  .dependsOn(
    core % "test->test;compile->compile",
    dataset % "test->test;compile->compile"
  )

lazy val docs = project
  .settings(framelessSettings: _*)
  .settings(noPublishSettings: _*)
  .enablePlugins(MdocPlugin)
  .settings(mdocIn := file(".") / "docs" / "src" / "main" / "tut")
  .settings(mdocOut := file(".") / "docs" / "target")
  .settings(libraryDependencies ++= Seq(
    "org.apache.spark" %% "spark-core" % sparkVersion,
    "org.apache.spark" %% "spark-sql"  % sparkVersion,
    "org.apache.spark" %% "spark-mllib"  % sparkVersion
  ))
  .settings(
    addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
    scalacOptions ++= Seq(
      "-Ypartial-unification",
      "-Ydelambdafy:inline"
    )
  )
  .dependsOn(dataset, cats, ml)

lazy val framelessSettings = Seq(
  organization := "org.typelevel",
  crossScalaVersions := Seq("2.11.12", "2.12.8"),
  scalaVersion := crossScalaVersions.value.last,
  scalacOptions ++= commonScalacOptions(scalaVersion.value),
  licenses += ("Apache-2.0", url("http://opensource.org/licenses/Apache-2.0")),
  testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-oDF"),
  libraryDependencies ++= Seq(
    "com.chuusai" %% "shapeless" % shapeless,
    "org.scalatest" %% "scalatest" % scalatest % "test",
    "org.scalacheck" %% "scalacheck" % scalacheck % "test"),
  javaOptions in Test ++= Seq("-Xmx1G", "-ea"),
  fork in Test := true,
  parallelExecution in Test := false
) ++ consoleSettings

def commonScalacOptions(scalaVersion: String): Seq[String] = {

  val versionSpecific = CrossVersion.partialVersion(scalaVersion) match {
    case Some((2, 11)) =>
      Seq("-Xlint:-missing-interpolator,_", "-Yinline-warnings")
    case Some((2, n)) if n >= 12 =>
      Seq("-Xlint:-missing-interpolator,-unused,_")
  }

  Seq(
    "-target:jvm-1.8",
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-unchecked",
    "-Xfatal-warnings",
    "-Yno-adapted-args",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Ywarn-unused-import",
    "-Ywarn-value-discard",
    "-language:existentials",
    "-language:implicitConversions",
    "-language:higherKinds",
    "-Xfuture") ++ versionSpecific
}

lazy val consoleSettings = Seq(
  scalacOptions in (Compile, console) ~= {_.filterNot("-Ywarn-unused-import" == _)},
  scalacOptions in (Test, console) := (scalacOptions in (Compile, console)).value
)

lazy val framelessTypedDatasetREPL = Seq(
  initialize ~= { _ => // Color REPL
    val ansi = System.getProperty("sbt.log.noformat", "false") != "true"
    if (ansi) System.setProperty("scala.color", "true")
  },
  initialCommands in console :=
    """
      |import org.apache.spark.{SparkConf, SparkContext}
      |import org.apache.spark.sql.SparkSession
      |import frameless.functions.aggregate._
      |import frameless.syntax._
      |
      |val conf = new SparkConf().setMaster("local[*]").setAppName("frameless repl").set("spark.ui.enabled", "false")
      |implicit val spark = SparkSession.builder().config(conf).appName("REPL").getOrCreate()
      |
      |import spark.implicits._
      |
      |spark.sparkContext.setLogLevel("WARN")
      |
      |import frameless.TypedDataset
    """.stripMargin,
  cleanupCommands in console :=
    """
      |spark.stop()
    """.stripMargin
)

lazy val publishSettings = Seq(
  publishMavenStyle := true,
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },
  publishArtifact in Test := false,
  pomIncludeRepository := Function.const(false),
  pomExtra in Global := {
    <url>https://github.com/typelevel/frameless</url>
    <scm>
      <url>git@github.com:typelevel/frameless.git</url>
      <connection>scm:git:git@github.com:typelevel/frameless.git</connection>
    </scm>
    <developers>
      <developer>
        <id>OlivierBlanvillain</id>
        <name>Olivier Blanvillain</name>
        <url>https://github.com/OlivierBlanvillain/</url>
      </developer>
      <developer>
        <id>adelbertc</id>
        <name>Adelbert Chang</name>
        <url>https://github.com/adelbertc/</url>
      </developer>
      <developer>
        <id>imarios</id>
        <name>Marios Iliofotou</name>
        <url>https://github.com/imarios/</url>
      </developer>
      <developer>
        <id>kanterov</id>
        <name>Gleb Kanterov</name>
        <url>https://github.com/kanterov/</url>
      </developer>
      <developer>
        <id>non</id>
        <name>Erik Osheim</name>
        <url>https://github.com/non/</url>
      </developer>
      <developer>
        <id>jeremyrsmith</id>
        <name>Jeremy Smith</name>
        <url>https://github.com/jeremyrsmith/</url>
      </developer>
    </developers>
  }
)

lazy val noPublishSettings = Seq(
  publish := (()),
  publishLocal := (()),
  publishArtifact := false
)

lazy val credentialSettings = Seq(
  // For Travis CI - see http://www.cakesolutions.net/teamblogs/publishing-artefacts-to-oss-sonatype-nexus-using-sbt-and-travis-ci
  credentials ++= (for {
    username <- Option(System.getenv().get("SONATYPE_USERNAME"))
    password <- Option(System.getenv().get("SONATYPE_PASSWORD"))
  } yield Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", username, password)).toSeq
)

copyReadme := copyReadmeImpl.value
lazy val copyReadme = taskKey[Unit]("copy for website generation")
lazy val copyReadmeImpl = Def.task {
  val from = baseDirectory.value / "README.md"
  val to   = baseDirectory.value / "docs" / "src" / "main" / "tut" / "README.md"
  sbt.IO.copy(List((from, to)), overwrite = true, preserveLastModified = true, preserveExecutable = true)
}
