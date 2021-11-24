import sbt.Tests.{Group, SubProcess}
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
import uk.gov.hmrc.SbtAutoBuildPlugin

lazy val scoverageSettings = {
  import scoverage.ScoverageKeys
  Seq(
    // Semicolon-separated list of regexs matching classes to exclude
    ScoverageKeys.coverageExcludedPackages := """uk\.gov\.hmrc\.traderservices\.wiring;uk\.gov\.hmrc\.BuildInfo;.*\.Routes;.*\.RoutesPrefix;.*Filters?;MicroserviceAuditConnector;Module;GraphiteStartUp;.*\.Reverse[^.]*""",
    ScoverageKeys.coverageMinimum := 80.00,
    ScoverageKeys.coverageFailOnMinimum := false,
    ScoverageKeys.coverageHighlighting := true,
    parallelExecution in Test := false
  )
}

lazy val compileDeps = Seq(
  ws,
  "uk.gov.hmrc"                  %% "bootstrap-backend-play-28" % "5.12.0",
  "uk.gov.hmrc"                  %% "auth-client"               % "5.7.0-play-28",
  "com.kenshoo"                  %% "metrics-play"              % "2.7.3_0.8.2",
  "com.github.robtimus"           % "data-url"                  % "2.0",
  "com.fasterxml.jackson.module" %% "jackson-module-scala"      % "2.12.5",
  "com.github.arturopala"        %% "validator"                 % "0.3.0"
)

def testDeps(scope: String) =
  Seq(
    "org.scalatest"       %% "scalatest"    % "3.2.9"  % scope,
    "com.vladsch.flexmark" % "flexmark-all" % "0.36.8" % scope
  )

lazy val itDeps = Seq(
  "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0"  % "it",
  "com.github.tomakehurst"  % "wiremock-jre8"      % "2.30.1" % "it"
)

lazy val root = (project in file("."))
  .settings(
    name := "file-transmission-synchronous",
    organization := "uk.gov.hmrc",
    scalaVersion := "2.12.12",
    PlayKeys.playDefaultPort := 10003,
    libraryDependencies ++= compileDeps ++ testDeps("test") ++ testDeps("it") ++ itDeps,
    publishingSettings,
    scoverageSettings,
    unmanagedResourceDirectories in Compile += baseDirectory.value / "resources",
    scalafmtOnCompile in Compile := true,
    scalafmtOnCompile in Test := true
  )
  .configs(IntegrationTest)
  .settings(
    Keys.fork in IntegrationTest := false,
    Defaults.itSettings,
    unmanagedSourceDirectories in IntegrationTest += baseDirectory(_ / "it").value,
    parallelExecution in IntegrationTest := false,
    testGrouping in IntegrationTest := oneForkedJvmPerTest((definedTests in IntegrationTest).value),
    scalafmtOnCompile in IntegrationTest := true,
    majorVersion := 0
  )
  .enablePlugins(PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin)

inConfig(IntegrationTest)(org.scalafmt.sbt.ScalafmtPlugin.scalafmtConfigSettings)

def oneForkedJvmPerTest(tests: Seq[TestDefinition]) =
  tests.map { test =>
    new Group(test.name, Seq(test), SubProcess(ForkOptions().withRunJVMOptions(Vector(s"-Dtest.name=${test.name}"))))
  }
