
import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.{SaveMode, SparkSession}

object Entrance extends App {
  Logger.getLogger("org.spark_project").setLevel(Level.WARN)
  Logger.getLogger("org.apache").setLevel(Level.WARN)
  Logger.getLogger("akka").setLevel(Level.WARN)
  Logger.getLogger("com").setLevel(Level.WARN)

  val resourceFolder = System.getProperty("user.dir")+"/src/test/resources/"

  override def main(args: Array[String]) {
    val spark = SparkSession
      .builder()
      .appName("HotspotAnalysis")
      .config("spark.some.config.option", "some-value").master("local[*]")
      .getOrCreate()

//    paramsParser(spark, Array("test/output hotzoneanalysis src/resources/point-hotzone.csv src/resources/zone-hotzone.csv hotcellanalysis src/resources/yellow_tripdata_2009-01_point.csv"))
//    queryLoader(spark,"hotzoneanalysis","src/test/resources/point-hotzone.csv src/test/resources/zone-hotzone.csv","src/test/output")
    queryLoader(spark,"hotcellanalysis","src/test/resources/test.csv","src/test/output1")

    println("Task Finished")
  }

  //don't need to run if running on IDEA
  private def paramsParser(spark: SparkSession, args: Array[String]): Unit = {
    var paramOffset = 1
    var currentQueryParams = ""
    var currentQueryName = ""
    var currentQueryIdx = -1

    while (paramOffset <= args.length) {
      if (paramOffset == args.length || args(paramOffset).toLowerCase.contains("analysis")) {
        // Turn in the previous query
        if (currentQueryIdx != -1) queryLoader(spark, currentQueryName, currentQueryParams, args(0) + currentQueryIdx)

        // Start a new query call
        if (paramOffset == args.length) return

        currentQueryName = args(paramOffset)
        currentQueryParams = ""
        currentQueryIdx = currentQueryIdx + 1

        println(currentQueryParams)
        println(currentQueryName)
      }
      else {
        // Keep appending query parameters
        currentQueryParams = currentQueryParams + args(paramOffset) + " "
      }
      paramOffset = paramOffset + 1
    }
  }

  private def queryLoader(spark: SparkSession, queryName: String, queryParams: String, outputPath: String) {
    val queryParam = queryParams.split(" ")
    if (queryName.equalsIgnoreCase("hotcellanalysis")) {
      if (queryParam.length != 1) throw new ArrayIndexOutOfBoundsException("[Bigdata] Query " + queryName + " needs 1 parameters but you entered " + queryParam.length)
      HotcellAnalysis.runHotcellAnalysis(spark, queryParam(0)).limit(50).write.mode(SaveMode.Overwrite).csv(outputPath)
    }
    else if (queryName.equalsIgnoreCase("hotzoneanalysis")) {
      if (queryParam.length != 2) throw new ArrayIndexOutOfBoundsException("[BigData] Query " + queryName + " needs 2 parameters but you entered " + queryParam.length)
      HotzoneAnalysis.runHotZoneAnalysis(spark, queryParam(0), queryParam(1)).coalesce(1).write.mode(SaveMode.Ignore).csv(outputPath)
    }
    else {
      throw new NoSuchElementException("[BigData] The given query name " + queryName + " is wrong. Please check your input.")
    }
  }
}
