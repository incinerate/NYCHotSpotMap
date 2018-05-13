import java.awt.Color

import com.vividsolutions.jts.geom._
import org.apache.log4j.{Level, Logger}
import org.apache.spark.serializer.KryoSerializer
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.datasyslab.geospark.enums.{GridType, IndexType}
import org.datasyslab.geospark.formatMapper.shapefileParser.ShapefileReader
import org.datasyslab.geospark.spatialOperator.JoinQuery
import org.datasyslab.geospark.spatialRDD.{CircleRDD, SpatialRDD}
import org.datasyslab.geosparksql.utils.{Adapter, GeoSparkSQLRegistrator}
import org.datasyslab.geosparkviz.core.Serde.GeoSparkVizKryoRegistrator
import org.datasyslab.geosparkviz.core.{ImageGenerator, RasterOverlayOperator}
import org.datasyslab.geosparkviz.extension.visualizationEffect.{HeatMap, ScatterPlot}
import org.datasyslab.geosparkviz.utils.ImageType


object FinalProject extends App{

	val resourceFolder = System.getProperty("user.dir")+"/src/test/resources/"

  // Data link (in shapefile): https://geo.nyu.edu/catalog/nyu_2451_34514
  val nycArealandmarkShapefileLocation = resourceFolder+"nyc-area-landmark-shapefile"

  // Data link (in CSV): http://www.nyc.gov/html/tlc/html/about/trip_record_data.shtml
  val nyctripCSVLocation1 = "/media/scott/KINGSTON/2016_Green_Taxi_Trip_Data.csv"
  val nyctripCSVLocation2 = resourceFolder+"2016_subset_1.csv"
  val colocationMapLocation = resourceFolder+"colocationMap"

  val sparkSession:SparkSession = SparkSession.builder().config("spark.serializer",classOf[KryoSerializer].getName).
    config("spark.kryo.registrator", classOf[GeoSparkVizKryoRegistrator].getName)
    .master("local[*]").appName("GeoSpark-Analysis").getOrCreate()
  Logger.getLogger("org").setLevel(Level.WARN)
  Logger.getLogger("akka").setLevel(Level.WARN)
//  val data = rawdataTransform();
//  data.show()
  visualizeSpatialColocation()
  calculateSpatialColocation()

  System.out.println("Finished")

  def rawdataTransform(): DataFrame = {
    Logger.getLogger("org").setLevel(Level.WARN)
    Logger.getLogger("akka").setLevel(Level.WARN)

    var rawdataDf = sparkSession.read.format("csv").option("delimiter",",").option("header","false").load(nyctripCSVLocation1)
    rawdataDf.createOrReplaceTempView("rawdata")
    val tempDF = rawdataDf.select("_c5","_c6")
    val header = tempDF.first();
    val dataDF = tempDF.toDF("X","Y").filter(_ != header)

    println(dataDF.count())
    dataDF
  }


  def visualizeSpatialColocation(tripDf : DataFrame, sparkSession: SparkSession): Unit =
  {
    Logger.getLogger("org").setLevel(Level.WARN)
    Logger.getLogger("akka").setLevel(Level.WARN)

    GeoSparkSQLRegistrator.registerAll(sparkSession.sqlContext)

    // Prepare NYC area landmarks which includes airports, museums, colleges, hospitals
    var arealmRDD = ShapefileReader.readToPolygonRDD(sparkSession.sparkContext, nycArealandmarkShapefileLocation)

    // Prepare NYC taxi trips. Only use the taxi trips' pickup points
    // var tripDf = sparkSession.read.format("csv").option("delimiter",",").option("header","false").load(nyctripCSVLocation)
    // Convert from DataFrame to RDD. This can also be done directly through GeoSpark RDD API.
    tripDf.createOrReplaceTempView("tripdf")
    var tripRDD = new SpatialRDD[Geometry]
    tripRDD.rawSpatialRDD = Adapter.toRdd(sparkSession.sql("select ST_Point(cast(tripdf.X as Decimal(24, 14)), cast(tripdf.Y as Decimal(24, 14))) from tripdf"))

    // Convert the Coordinate Reference System from degree-based to meter-based. This returns the accurate distance calculate.
    arealmRDD.CRSTransform("epsg:4326","epsg:3857")
    tripRDD.CRSTransform("epsg:4326","epsg:3857")

    arealmRDD.analyze()
    tripRDD.analyze()

    //1200
    val imageResolutionX = 1000
    val imageResolutionY = 1000

    val frontImage = new ScatterPlot(imageResolutionX, imageResolutionY, arealmRDD.boundaryEnvelope, true)
    frontImage.CustomizeColor(0, 0, 0, 255, Color.GREEN, true)
    frontImage.Visualize(sparkSession.sparkContext, arealmRDD)

    val backImage = new HeatMap(imageResolutionX, imageResolutionY, arealmRDD.boundaryEnvelope, true, 1)
    backImage.Visualize(sparkSession.sparkContext, tripRDD)

    val overlayOperator = new RasterOverlayOperator(backImage.rasterImage)
    overlayOperator.JoinImage(frontImage.rasterImage)

    val imageGenerator = new ImageGenerator
    imageGenerator.SaveRasterImageAsLocalFile(overlayOperator.backRasterImage, colocationMapLocation, ImageType.SVG)

//    sparkSession.stop()
  }

  def calculateSpatialColocation(tripDf : DataFrame, sparkSession: SparkSession): Unit =
  {
    Logger.getLogger("org").setLevel(Level.WARN)
    Logger.getLogger("akka").setLevel(Level.WARN)

    GeoSparkSQLRegistrator.registerAll(sparkSession.sqlContext)


    // Prepare NYC area landmarks which includes airports, museums, colleges, hospitals
    var arealmRDD = new SpatialRDD[Geometry]()
    arealmRDD.rawSpatialRDD = ShapefileReader.readToGeometryRDD(sparkSession.sparkContext, nycArealandmarkShapefileLocation)
    // Use the center point of area landmarks to check co-location. This is required by Ripley's K function.
    arealmRDD.rawSpatialRDD = arealmRDD.rawSpatialRDD.rdd.map[Geometry](f=>f.getCentroid)

    // The following two lines are optional. The purpose is to show the structure of the shapefile.
    var arealmDf = Adapter.toDf(arealmRDD, sparkSession)
    arealmDf.show()

    // Prepare NYC taxi trips. Only use the taxi trips' pickup points
    // var tripDf = sparkSession.read.format("csv").option("delimiter",",").option("header","false").load(nyctripCSVLocation)
    tripDf.show() // Optional
    // Convert from DataFrame to RDD. This can also be done directly through GeoSpark RDD API.
    tripDf.createOrReplaceTempView("tripdf")
    var tripRDD = new SpatialRDD[Geometry]
    tripRDD.rawSpatialRDD = Adapter.toRdd(sparkSession.sql("select ST_Point(cast(tripdf.X as Decimal(24, 14)), cast(tripdf.Y as Decimal(24, 14))) from tripdf"))

    // Convert the Coordinate Reference System from degree-based to meter-based. This returns the accurate distance calculate.
    arealmRDD.CRSTransform("epsg:4326","epsg:3857")
    tripRDD.CRSTransform("epsg:4326","epsg:3857")

    arealmRDD.analyze()
    tripRDD.analyze()

    // Cache indexed NYC taxi trip rdd to improve iterative performance
    tripRDD.spatialPartitioning(GridType.KDBTREE)
    tripRDD.buildIndex(IndexType.QUADTREE, true)
    tripRDD.indexedRDD = tripRDD.indexedRDD.cache()

    // Parameter settings. Check the definition of Ripley's K function.
    val area = tripRDD.boundaryEnvelope.getArea
    val maxDistance = 0.01*Math.max(tripRDD.boundaryEnvelope.getHeight,tripRDD.boundaryEnvelope.getWidth)
    val iterationTimes = 10
    val distanceIncrement = maxDistance/iterationTimes
    val beginDistance = 0.0
    var currentDistance = 0.0

    // Start the iteration
    println("distance(meter),observedL,difference,coLocationStatus")
    for (i <- 1 to iterationTimes)
    {
      currentDistance = beginDistance + i*distanceIncrement

      var bufferedArealmRDD = new CircleRDD(arealmRDD,currentDistance)
      bufferedArealmRDD.spatialPartitioning(tripRDD.getPartitioner)
//    Run GeoSpark Distance Join Query
      var adjacentMatrix = JoinQuery.DistanceJoinQueryFlat(tripRDD, bufferedArealmRDD,true,true)

//      Uncomment the following two lines if you want to see what the join result looks like in SparkSQL
//      var adjacentMatrixDf = Adapter.toDf(adjacentMatrix, sparkSession)
//      adjacentMatrixDf.show()

      var observedK = adjacentMatrix.count()*area*1.0/(arealmRDD.approximateTotalCount*tripRDD.approximateTotalCount)
      var observedL = Math.sqrt(observedK/Math.PI)
      var expectedL = currentDistance
      var colocationDifference = observedL  - expectedL
      var colocationStatus = {if (colocationDifference>0) "Co-located" else "Dispersed"}

      println(s"""$currentDistance,$observedL,$colocationDifference,$colocationStatus""")
    }
    sparkSession.stop()
  }

  def visualizeSpatialColocation(): Unit =
  {
    val sparkSession:SparkSession = SparkSession.builder().config("spark.serializer",classOf[KryoSerializer].getName).
      config("spark.kryo.registrator", classOf[GeoSparkVizKryoRegistrator].getName)
      .master("local[*]").appName("GeoSpark-Analysis").getOrCreate()
    Logger.getLogger("org").setLevel(Level.WARN)
    Logger.getLogger("akka").setLevel(Level.WARN)

    GeoSparkSQLRegistrator.registerAll(sparkSession.sqlContext)

    // Prepare NYC area landmarks which includes airports, museums, colleges, hospitals
    var arealmRDD = ShapefileReader.readToPolygonRDD(sparkSession.sparkContext, nycArealandmarkShapefileLocation)

    // Prepare NYC taxi trips. Only use the taxi trips' pickup points
    var tripDf = sparkSession.read.format("csv").option("delimiter",",").option("header","false").load(nyctripCSVLocation2)
    // Convert from DataFrame to RDD. This can also be done directly through GeoSpark RDD API.
    tripDf.createOrReplaceTempView("tripdf")
    var tripRDD = new SpatialRDD[Geometry]
    tripRDD.rawSpatialRDD = Adapter.toRdd(sparkSession.sql("select ST_Point(cast(tripdf._c0 as Decimal(24, 14)), cast(tripdf._c1 as Decimal(24, 14))) from tripdf"))

    // Convert the Coordinate Reference System from degree-based to meter-based. This returns the accurate distance calculate.
    arealmRDD.CRSTransform("epsg:4326","epsg:3857")
    tripRDD.CRSTransform("epsg:4326","epsg:3857")

    arealmRDD.analyze()
    tripRDD.analyze()

    val imageResolutionX = 1000
    val imageResolutionY = 1000

    val frontImage = new ScatterPlot(imageResolutionX, imageResolutionY, arealmRDD.boundaryEnvelope, true)
    frontImage.CustomizeColor(0, 0, 0, 255, Color.GREEN, true)
    frontImage.Visualize(sparkSession.sparkContext, arealmRDD)

    val backImage = new HeatMap(imageResolutionX, imageResolutionY, arealmRDD.boundaryEnvelope, true, 1)
    backImage.Visualize(sparkSession.sparkContext, tripRDD)

    val overlayOperator = new RasterOverlayOperator(backImage.rasterImage)
    overlayOperator.JoinImage(frontImage.rasterImage)

    val imageGenerator = new ImageGenerator
    imageGenerator.SaveRasterImageAsLocalFile(overlayOperator.backRasterImage, colocationMapLocation, ImageType.PNG)

    sparkSession.stop()
  }

  def calculateSpatialColocation(): Unit =
  {
    val sparkSession:SparkSession = SparkSession.builder().config("spark.serializer",classOf[KryoSerializer].getName).
      config("spark.kryo.registrator", classOf[GeoSparkVizKryoRegistrator].getName).
      master("local[*]").appName("GeoSpark-Analysis").getOrCreate()
    Logger.getLogger("org").setLevel(Level.WARN)
    Logger.getLogger("akka").setLevel(Level.WARN)

    GeoSparkSQLRegistrator.registerAll(sparkSession.sqlContext)


    // Prepare NYC area landmarks which includes airports, museums, colleges, hospitals
    var arealmRDD = new SpatialRDD[Geometry]()
    arealmRDD.rawSpatialRDD = ShapefileReader.readToGeometryRDD(sparkSession.sparkContext, nycArealandmarkShapefileLocation)
    // Use the center point of area landmarks to check co-location. This is required by Ripley's K function.
    arealmRDD.rawSpatialRDD = arealmRDD.rawSpatialRDD.rdd.map[Geometry](f=>f.getCentroid)

    // The following two lines are optional. The purpose is to show the structure of the shapefile.
    var arealmDf = Adapter.toDf(arealmRDD, sparkSession)
    arealmDf.show()

    // Prepare NYC taxi trips. Only use the taxi trips' pickup points
    var tripDf = sparkSession.read.format("csv").option("delimiter",",").option("header","false").load(nyctripCSVLocation2)
    tripDf.show() // Optional
    println(tripDf.count())
    // Convert from DataFrame to RDD. This can also be done directly through GeoSpark RDD API.
    tripDf.createOrReplaceTempView("tripdf")
    var tripRDD = new SpatialRDD[Geometry]
    tripRDD.rawSpatialRDD = Adapter.toRdd(sparkSession.sql("select ST_Point(cast(tripdf._c0 as Decimal(24, 14)), cast(tripdf._c1 as Decimal(24, 14))) from tripdf"))

    // Convert the Coordinate Reference System from degree-based to meter-based. This returns the accurate distance calculate.
    arealmRDD.CRSTransform("epsg:4326","epsg:3857")
    tripRDD.CRSTransform("epsg:4326","epsg:3857")

    // !!!NOTE!!!: Analyze RDD step can be avoided if you know the rectangle boundary of your dataset and approximate total count.
    arealmRDD.analyze()
    tripRDD.analyze()

    // Cache indexed NYC taxi trip rdd to improve iterative performance
    tripRDD.spatialPartitioning(GridType.KDBTREE)
    tripRDD.buildIndex(IndexType.QUADTREE, true)
    tripRDD.indexedRDD = tripRDD.indexedRDD.cache()

    // Parameter settings. Check the definition of Ripley's K function.
    val area = tripRDD.boundaryEnvelope.getArea
    val maxDistance = 0.01*Math.max(tripRDD.boundaryEnvelope.getHeight,tripRDD.boundaryEnvelope.getWidth)
    val iterationTimes = 10
    val distanceIncrement = maxDistance/iterationTimes
    val beginDistance = 0.0
    var currentDistance = 0.0

    // Start the iteration
    println("distance(meter),observedL,difference,coLocationStatus")
    for (i <- 1 to iterationTimes)
    {
      currentDistance = beginDistance + i*distanceIncrement

      var bufferedArealmRDD = new CircleRDD(arealmRDD,currentDistance)
      bufferedArealmRDD.spatialPartitioning(tripRDD.getPartitioner)
      //    Run GeoSpark Distance Join Query
      var adjacentMatrix = JoinQuery.DistanceJoinQueryFlat(tripRDD, bufferedArealmRDD,true,true)

      //      Uncomment the following two lines if you want to see what the join result looks like in SparkSQL
      //      var adjacentMatrixDf = Adapter.toDf(adjacentMatrix, sparkSession)
      //      adjacentMatrixDf.show()

      var observedK = adjacentMatrix.count()*area*1.0/(arealmRDD.approximateTotalCount*tripRDD.approximateTotalCount)
      var observedL = Math.sqrt(observedK/Math.PI)
      var expectedL = currentDistance
      var colocationDifference = observedL  - expectedL
      var colocationStatus = {if (colocationDifference>0) "Co-located" else "Dispersed"}

      println(s"""$currentDistance,$observedL,$colocationDifference,$colocationStatus""")
    }
    sparkSession.stop()
  }

}