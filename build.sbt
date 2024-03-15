import uk.gov.hmrc.{DefaultBuildSettings, SbtAutoBuildPlugin}

ThisBuild / scalaVersion := "2.13.12"
ThisBuild / majorVersion := 0

lazy val scoverageSettings = {
  import scoverage.ScoverageKeys
  Seq(
    // Semicolon-separated list of regexs matching classes to exclude
    ScoverageKeys.coverageExcludedPackages := """uk\.gov\.hmrc\.traderservices\.wiring;uk\.gov\.hmrc\.BuildInfo;.*\.Routes;.*\.RoutesPrefix;.*Filters?;MicroserviceAuditConnector;Module;GraphiteStartUp;.*\.Reverse[^.]*""",
    ScoverageKeys.coverageMinimumStmtTotal := 80.00,
    ScoverageKeys.coverageFailOnMinimum := false,
    ScoverageKeys.coverageHighlighting := true,
    Test / parallelExecution := false
  )
}

lazy val compileDeps = Seq(
  ws,
  "uk.gov.hmrc"                  %% "bootstrap-backend-play-30" % "8.4.0",
  "org.typelevel"                %% "cats-core"                 % "2.10.0",
  "com.github.robtimus"           % "data-url"                  % "2.0.1",
  "com.fasterxml.jackson.module" %% "jackson-module-scala"      % "2.17.0-rc1"
)

def testDeps(scope: String): Seq[ModuleID] =
  Seq(
    "uk.gov.hmrc"            %% s"bootstrap-test-play-30"        % "8.5.0" % scope,
    "com.vladsch.flexmark" % "flexmark-all" % "0.64.8" % scope
  )

lazy val itDeps = Seq(
  "uk.gov.hmrc"            %% s"bootstrap-test-play-30"        % "8.5.0" % Test,
  "com.github.tomakehurst" % "wiremock"      % "3.0.1" % Test
)

lazy val root = (project in file("."))
  .settings(
    name := "file-transmission-synchronous",
    organization := "uk.gov.hmrc",
    scalacOptions += "-Wconf:src=routes/.*:s",
    scalacOptions += "-Wconf:cat=unused-imports&src=html/.*:s",
    PlayKeys.playDefaultPort := 10003,
    libraryDependencies ++= compileDeps ++ testDeps("test") ++ itDeps,
    scoverageSettings,
    Compile / unmanagedResourceDirectories += baseDirectory.value / "resources",
    Compile / scalafmtOnCompile := true,
    Test / scalafmtOnCompile := true
  )
  .enablePlugins(PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin)


lazy val it = project
  .enablePlugins(PlayScala)
  .dependsOn(root % "test->test") // the "test->test" allows reusing test code and test dependencies
  .settings(DefaultBuildSettings.itSettings(true) ++ Seq(
    Test / Keys.fork := false,
    Test / javaOptions += "-Djava.locale.providers=CLDR,JRE",
    Test / parallelExecution := false,
    Test / resourceDirectories := Seq(baseDirectory.value / "conf", sourceDirectory.value / "resources"),
    libraryDependencies ++= (compileDeps ++ testDeps("test") ++ itDeps)
  ))
