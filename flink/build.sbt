import sbt.Keys.libraryDependencies

resolvers in ThisBuild ++= Seq(
  "Apache Development Snapshot Repository" at "https://repository.apache.org/content/repositories/snapshots/",
  Resolver.mavenLocal)
resolvers += "Artima Maven Repository" at "http://repo.artima.com/releases"

name := "flink"

version := "0.1-SNAPSHOT"

val settings = Seq(
  organization := "org.codefeedr"
)
scalaVersion in ThisBuild := "2.11.11"

parallelExecution in Test := false

val flinkVersion = "1.4.2"
val dep_flink = Seq(
  "org.apache.flink" %% "flink-scala" % flinkVersion % "provided",
  "org.apache.flink" %% "flink-streaming-scala" % flinkVersion % "provided",
  "org.apache.flink" %% "flink-table" % flinkVersion % "provided"
)

val dep_core = Seq(
  "codes.reactive" %% "scala-time" % "0.4.1",
  "org.apache.zookeeper" % "zookeeper" % "3.4.9",
  "org.mockito" % "mockito-core" % "2.13.0" % "test",
  "org.json4s" % "json4s-scalap_2.11" % "3.6.0-M2",
  "org.json4s" % "json4s-jackson_2.11" % "3.6.0-M2",


  "org.scalactic" %% "scalactic" % "3.0.1",
  "org.scalatest" %% "scalatest" % "3.0.1" % "test",

  "ch.qos.logback" % "logback-classic" % "1.1.7",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",

  "org.eclipse.mylyn.github" % "org.eclipse.egit.github.core" % "2.1.5" % "provided",
  "com.typesafe" % "config" % "1.3.1",
  "org.mongodb.scala" %% "mongo-scala-driver" % "2.1.0",

  "org.apache.kafka" % "kafka-clients" % "1.0.0",
  "com.jsuereth" %% "scala-arm" % "2.0",
  "org.scala-lang.modules" % "scala-java8-compat_2.11" % "0.8.0",
  "org.scala-lang.modules" %% "scala-async" % "0.9.7",
  "io.reactivex" %% "rxscala" % "0.26.5"
)



lazy val flink = (project in file("."))
  .settings(
    libraryDependencies ++= dep_core,
    libraryDependencies ++= dep_flink,
    libraryDependencies ~= { _.map(_.exclude("org.slf4j", "slf4j-log4j12")) }
  )



// make run command include the provided dependencies
run in Compile := Defaults.runTask(fullClasspath in Compile,
  mainClass in (Compile, run),
  runner in (Compile, run))

// exclude Scala library from assembly
//assemblyOption in assembly := (assemblyOption in assembly).value
//  .copy(includeScala = false)