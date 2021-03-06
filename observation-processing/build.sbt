name := "ObservationStreamProcessing"

version := "1.0"

scalaVersion := "2.11.8"

/**
  * Used to resolve to version 1.1 before official release, kept as a template
  * for when the next release candidate is published
  *
  * resolvers += "Flink 1.1 release snapshot" at "https://repository.apache.org/content/repositories/orgapacheflink-1098/"
  */


/**
  * All Flink dependencies
  */
libraryDependencies ++=  Seq("org.apache.flink" % "flink-streaming-scala_2.11" % "1.1.0"  % "provided",
  "org.apache.flink" % "flink-connector-kafka-0.9_2.11" % "1.1.0",
  "org.apache.flink" % "flink-connector-cassandra_2.11" % "1.1.0",
  "org.apache.commons" % "commons-math3" % "3.6.1",
  "org.apache.flink" % "flink-streaming-contrib_2.11" % "1.1.0" % "provided",
  "org.apache.flink" % "flink-test-utils_2.11" % "1.1.0"  % "provided",
  "org.apache.flink" % "flink-test-utils-junit" % "1.1.0" % "provided",
  "org.apache.flink" % "flink-connector-filesystem_2.11" % "1.1.0"  % "provided")

/*
libraryDependencies ++=  Seq("org.apache.flink" % "flink-streaming-scala_2.11" % "1.1.0" ,
  "org.apache.flink" % "flink-connector-kafka-0.9_2.11" % "1.1.0",
  "org.apache.flink" % "flink-connector-cassandra_2.11" % "1.1.0",
  "org.apache.flink" % "flink-streaming-contrib_2.11" % "1.1.0" ,
  "org.apache.flink" % "flink-test-utils_2.11" % "1.1.0"  ,
  "org.apache.flink" % "flink-test-utils-junit" % "1.1.0" ,
  "org.apache.flink" % "flink-connector-filesystem_2.11" % "1.1.0"  )
*/


/**
  * Default dependency
  */
libraryDependencies ++= Seq("org.scala-lang" % "scala-compiler" % "2.11.8",
  "org.scala-lang" % "scala-reflect" % "2.11.8")

/**
  * Used for connecting to the Redis registry instance
  */
libraryDependencies ++= Seq(
  "net.debasishg" %% "redisclient" % "3.0"
)

/**
  * Contains many maths functions useful for datastream processing
  */
libraryDependencies += "org.apache.commons" % "commons-math3" % "3.6.1"

/**
  * Dependencies for unit testing
  */

libraryDependencies += "junit" % "junit" % "4.10" % "test"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.0" % "test"

/**
  * R Interfacing library
  */
libraryDependencies += "org.ddahl" % "rscala_2.11" % "1.0.13"

// sbt 'set test in assembly := {}' clean assembly
