scalaVersion := "2.13.5"

organization := "com.pirum"
organizationName := "Pirum Systems"
organizationHomepage := Some(url("https://www.pirum.com"))

libraryDependencies ++= Seq(
  "dev.zio" %% "zio" % "1.0.7",
  "dev.zio" %% "zio-test" % "1.0.7",
  "dev.zio" %% "zio-test-sbt" % "1.0.7"
)

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")