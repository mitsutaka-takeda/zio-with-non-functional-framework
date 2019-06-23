name := "zio-with-non-functional-framework"

version := "0.1"

scalaVersion := "2.12.8"

libraryDependencies ++=  Seq(
  "org.scalaz" %% "scalaz-zio" % "1.0-RC5",
  "com.amazonaws" % "aws-lambda-java-core" % "1.2.0",
  "org.reactivestreams" % "reactive-streams" % "1.0.2"
)