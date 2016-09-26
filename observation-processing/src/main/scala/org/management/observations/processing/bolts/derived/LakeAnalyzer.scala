package org.management.observations.processing.bolts.derived

// Used for connecting to the Redis registry
import com.redis.RedisClient
import org.apache.flink.api.java.utils.ParameterTool
import org.management.observations.processing.tuples.BasicObservation

// The function being extended and related
import org.apache.flink.streaming.api.scala.function.RichWindowFunction
import org.apache.flink.api.java.tuple.Tuple
import org.apache.flink.streaming.api.windowing.windows.TimeWindow

// Used for passing parameters to the open() function
import org.apache.flink.configuration.Configuration

// The collector for objects to return into the datastream
import org.apache.flink.util.Collector

// System KVP properties
import org.management.observations.processing.ProjectConfiguration
import scala.collection.JavaConversions._

// Access to R environment
import org.ddahl.rscala._

/**
  * Lake Analyzer is a package within R, that provides functions to generate
  * derived data from a buoy PRT chain.  This bolt makes use of rscala to
  * connect to an R session instance, transfer data between environments,
  * and emit the derived data back into the workflow.
  */
class LakeAnalyzer extends RichWindowFunction[BasicObservation, String, Tuple, TimeWindow] {

  @transient var params: ParameterTool = ParameterTool.fromMap(mapAsJavaMap(ProjectConfiguration.configMap))
  @transient var redisCon: RedisClient = new RedisClient(params.get("redis-conn-ip"), params.get("redis-conn-port").toInt)
  @transient var rconn: RClient = RClient()

  override def open(parameters: Configuration) = {
    this.params = ParameterTool.fromMap(mapAsJavaMap(ProjectConfiguration.configMap))
    this.redisCon = new RedisClient(params.get("redis-conn-ip"), params.get("redis-conn-port").toInt)
    this.rconn = RClient()
  }

  def apply(key: Tuple, window: TimeWindow, input: Iterable[BasicObservation], out: Collector[String]): Unit = {

    /**
      * To use rLakeAnalyzer, the depth of every sensor is needed, so the
      * registry is used to lookup these values.
      *
      * Then an array of arrays is generated with the datetime, value, depth
      * fields, as this is comparable to a matrix within R.
      *
      * The datetime is modified into seconds since the epoch (from milliseconds)
      */
    val depthEntries = input.map(x =>
      Array(
        x.phenomenontimestart / 1000,
        x.value, {
          val tmp = this.redisCon.get(x.feature + "::" + x.procedure + "::" + x.observableproperty + "::depth"); if (tmp.isDefined) tmp.get.toInt else -100
        }
      )
    ).filter(x => x(2) != -100).toArray

    /**
      * Retrieve the feature, used as the feature associated with the derived
      * data when it is fed back into the RAW observation queue
      */
    val feature = input.head.feature

    // This checks that there are entries within the array once the filter
    if (depthEntries.length > 0) {

      /**
        * The segment below is fragile - there is no checking for correct input/
        * output records, or recovering from exceptions. 
        */
      // TODO: Add exception catching, identify potential errors thrown

      // Setup the R environment
      this.rconn.eval("library(rLakeAnalyzer)")
      this.rconn.eval("library(reshape2)")
      this.rconn.eval("library(stringr)")
      this.rconn.eval("library(dplyr)")
      this.rconn.eval("options(scipen = 999)")

      // Wrangle the data in R
      this.rconn.set("lakedata",depthEntries)
      this.rconn.eval("lakedata <- data.frame(lakedata)")
      this.rconn.eval("colnames(lakedata) <- c('datetime','value','depth')")
      this.rconn.eval("lakedata$datetime = as.integer(lakedata$datetime)")
      this.rconn.eval("lakedata$value = as.numeric(lakedata$value)")
      this.rconn.eval("lakedata$depth = as.integer(lakedata$depth)")
      this.rconn.eval("lakedata$depth <- str_c('wtr_',lakedata$depth)")
      this.rconn.eval("lakedata = dcast(lakedata, datetime ~ depth)")

      /**
        * When retrieving the data from R, the datetime field is first
        * transformed to a string, and the whole matrix is read as a
        * string.  This is due to the datetime being converted to a float
        * when using evalD2, or getD2.  It was necessary to use Dn as
        * the output value is a decimal value.
        */

      // Buoyancy Frequency
      this.rconn.eval("output = ts.buoyancy.freq(lakedata)")
      this.rconn.eval("output$datetime = str_c(output$datetime)")
      this.rconn.eval("output = as.matrix(output)")
      val buoyFreq = this.rconn.getS2("output")

      buoyFreq.foreach(x =>
        out.collect(
          feature + ",BUOYFREQ," + x(0) + "," + x(1)
        )
      )

      // Center Buoyancy
      this.rconn.eval("output = ts.center.buoyancy(lakedata)")
      this.rconn.eval("output$datetime = str_c(output$datetime)")
      this.rconn.eval("output = as.matrix(output)")
      val centerBuoy = rconn.getS2("output")

      centerBuoy.foreach(x =>
        out.collect(
          feature + ",CENTREBUOY," + x(0) + "," + x(1)
        )
      )

      // Metalimnion Depths (has a lower and upper column)
      this.rconn.eval("output = ts.meta.depths(lakedata)")
      this.rconn.eval("output$datetime = str_c(output$datetime)")
      this.rconn.eval("output = as.matrix(output)")
      val metaDepth = rconn.getS2("output")

      metaDepth.foreach(x =>
        out.collect(
          feature + ",METADEPTHBOTTOM," + x(0) + "," + x(2)
        )
      )

      metaDepth.foreach(x =>
        out.collect(
          feature + ",METADEPTHTOP," + x(0) + "," + x(1)
        )
      )

      // Thermocline Depth
      this.rconn.eval("output = ts.thermo.depth(lakedata)")
      this.rconn.eval("output$datetime = str_c(output$datetime)")
      this.rconn.eval("output = as.matrix(output)")
      val thermoDepth = rconn.getS2("output")

      thermoDepth.foreach(x =>
        out.collect(
          feature + ",THERMODEPTH," + x(0) + "," + x(1)
        )
      )
    }
  }
}
