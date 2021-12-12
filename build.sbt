name := "akka-playground"

version := "0.1"

scalaVersion := "2.13.7"

lazy val akkaVersion      = "2.6.17"
lazy val akkaHttpVersion  = "10.2.7"
val jwtVersion            = "9.0.2"
lazy val scalaTestVersion = "3.2.10"

libraryDependencies ++= Seq(
  "com.typesafe.akka"    %% "akka-stream"          % akkaVersion,
  "com.typesafe.akka"    %% "akka-actor-typed"     % akkaVersion,
  "com.typesafe.akka"    %% "akka-http"            % akkaHttpVersion,
  "com.typesafe.akka"    %% "akka-http-spray-json" % akkaHttpVersion,
  "com.typesafe.akka"    %% "akka-http-testkit"    % akkaHttpVersion,
  "com.typesafe.akka"    %% "akka-testkit"         % akkaVersion,
  "com.typesafe.akka"    %% "akka-stream-testkit"  % akkaVersion,
  "org.scalatest"        %% "scalatest"            % scalaTestVersion,
  "com.github.jwt-scala" %% "jwt-spray-json"       % jwtVersion,
  "ch.qos.logback"        % "logback-classic"      % "1.2.6"
)

scalacOptions in Compile ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlog-reflective-calls",
  "-Xlint"
)
