import sbt.Keys.{libraryDependencies, version}


lazy val root = (project in file(".")).
  settings(
    name := "geospark-analysis",

    version := "0.1.0",

    scalaVersion := "2.11.11",

    organization := "org.datasyslab",

    publishMavenStyle := true
  )

val SparkVersion = "2.2.1"

val SparkCompatibleVersion = "2.2"

val HadoopVersion = "2.7.2"

val GeoSparkVersion = "1.1.2"

val dependencyScope = "compile"

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % SparkVersion % dependencyScope exclude("org.apache.hadoop", "*"),
  "org.apache.spark" %% "spark-sql" % SparkVersion % dependencyScope exclude("org.apache.hadoop", "*"),
  "org.apache.hadoop" % "hadoop-mapreduce-client-core" % HadoopVersion % dependencyScope,
  "org.apache.hadoop" % "hadoop-common" % HadoopVersion % dependencyScope,
  "org.datasyslab" % "geospark" % GeoSparkVersion,
  "org.datasyslab" % "geospark-sql_".concat(SparkCompatibleVersion) % GeoSparkVersion ,
  "org.datasyslab" % "geospark-viz" % GeoSparkVersion
)

assemblyMergeStrategy in assembly := {
  case PathList("org.datasyslab", "geospark", xs@_*) => MergeStrategy.first
  case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
  case path if path.endsWith(".SF") => MergeStrategy.discard
  case path if path.endsWith(".DSA") => MergeStrategy.discard
  case path if path.endsWith(".RSA") => MergeStrategy.discard
  case _ => MergeStrategy.first
}

resolvers +=
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

resolvers +=
  "Open Source Geospatial Foundation Repository" at "http://download.osgeo.org/webdav/geotools"