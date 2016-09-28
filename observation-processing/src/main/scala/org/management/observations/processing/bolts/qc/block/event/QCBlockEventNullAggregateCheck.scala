package org.management.observations.processing.bolts.qc.block.event

// Used for connecting to the Redis registry
import com.redis.RedisClient
import org.apache.flink.api.java.utils.ParameterTool
import org.management.observations.processing.tuples.{QCOutcomeQualitative}

// The base class for the key tuple, in this case Tuple3
import org.apache.flink.api.java.tuple.Tuple

// Used for passing parameters to the open() function
import org.apache.flink.configuration.Configuration

// The function being extended
import org.apache.flink.streaming.api.scala.function.RichWindowFunction

// The meta-data holder for the window information
import org.apache.flink.streaming.api.windowing.windows.TimeWindow

// The collector for objects to return into the datastream
import org.apache.flink.util.Collector

// The tuples used within this bolt
import org.management.observations.processing.tuples.QCEvent

// System KVP properties
import org.management.observations.processing.ProjectConfiguration
import scala.collection.JavaConversions._

/**
  * QCCheckNullAggregate
  *
  * - Compares the number of null observations within a bounded window
  *    against a threshold
  *
  * - Generate a QCEvent onto the datastream when the threshold is exceeded
  */
class QCBlockEventNullAggregateCheck extends RichWindowFunction[QCOutcomeQualitative, QCEvent, Tuple, TimeWindow]{

  @transient var params: ParameterTool = ParameterTool.fromMap(mapAsJavaMap(ProjectConfiguration.configMap))
  @transient var redisCon =  new RedisClient(params.get("redis-conn-ip"),params.get("redis-conn-port").toInt)

  override def open(parameters: Configuration) = {
    this.params = ParameterTool.fromMap(mapAsJavaMap(ProjectConfiguration.configMap))
    this.redisCon =  new RedisClient(params.get("redis-conn-ip"),params.get("redis-conn-port").toInt)
  }

  def apply(key: Tuple, window: TimeWindow, input: Iterable[QCOutcomeQualitative], out: Collector[QCEvent]): Unit = {

    // Retrieve the meta-data fields from the key and window elements
    val feature = key.getField(0).toString
    val procedure = key.getField(1).toString
    val observableproperty = key.getField(2).toString

    val eventtimestart = window.getStart
    val eventtimeend = window.getEnd

    /**
      * Check test threshold reached.
      *
      * Retrieve the correct threshold (1h, 12h, 24h) by checking the start and end
      * time of the window.  It is possible that a 24h window may have observations
      * covering only a 10h duration, and so the following will use the 12h threshold
      * for both the 12h and 24h windows.
      *
      * Compare against a duration of 1.5 hours, 12.5 hours, and 24.5 hours
      * 1.5 hours = 5400
      * 12.5 hours = 45000
      * 24.5 hours = 88200
      */

    // Calculate window size, lookup registry for threshold
    val timeDiff = eventtimeend-eventtimestart
    val windowDuration: String = if(timeDiff < 5400000) "1h" else if(timeDiff < 45000000) "12h" else "24h"
    val event = params.get("qc-event-null-aggregate-prefix")+ windowDuration
    val nullThreshold = this.redisCon.get(feature+"::"+procedure+"::"+observableproperty+"::thresholds::null::aggregate::"+windowDuration)

    if(nullThreshold.isDefined){
      // Compare threshold to observations in window, if exceeded generate an event
      if(nullThreshold.get.toInt <= input.size)
        out.collect(QCEvent(feature, procedure, observableproperty, event, eventtimestart, eventtimeend))
    }
  }
}
