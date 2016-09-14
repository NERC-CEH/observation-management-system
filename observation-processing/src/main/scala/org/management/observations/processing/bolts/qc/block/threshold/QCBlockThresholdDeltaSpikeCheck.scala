package org.management.observations.processing.bolts.qc.block.threshold

// Used for connecting to the Redis registry
import com.redis.RedisClient
import org.apache.flink.api.java.utils.ParameterTool

// The function being extended and related
import org.apache.flink.streaming.api.scala.function.RichWindowFunction
import org.apache.flink.streaming.api.windowing.windows.GlobalWindow
import org.apache.flink.api.java.tuple.Tuple

// Used for passing parameters to the open() function
import org.apache.flink.configuration.Configuration

// The collector for objects to return into the datastream
import org.apache.flink.util.Collector

// The tuples used within this bolt
import org.management.observations.processing.tuples.{QCOutcomeQuantitative, SemanticObservation, SemanticObservationFlow}

// Used to parse date time
import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneOffset}

// System KVP properties
import org.management.observations.processing.ProjectConfiguration
import scala.collection.JavaConversions._

/**
  * QCBlockThresholdDeltaStepCheck
  *
  * - performs a check on a window of three concurrent observations,
  *     where the acceleration between the three points
  *     minus the acceleration between the first and third point is compared
  *     to a maximum threshold.  This test identifies spikes in
  *     the data.
  *
  * - Thresholds may be created/referenced in a range of ways depending
  *     on the method of generation.  Some thresholds may use values that
  *     do not change over time, others may use thresholds centered around
  *     hourly, daily, or monthly points.  With those that change centre around
  *     specific temporal points it is necessary to identify the resolution of
  *     the point (hourly, half-daily, daily, monthly), and to then identify
  *     the exact point closest to the current observation.
  *
  * - This test is used for three concurrent observations, for
  *     larger windows, a separate acceleration test should be introduced.
  */
class QCBlockThresholdDeltaSpikeCheck extends RichWindowFunction[SemanticObservation, QCOutcomeQuantitative, Tuple, GlobalWindow] with SemanticObservationFlow{

  @transient var params: ParameterTool = ParameterTool.fromMap(mapAsJavaMap(ProjectConfiguration.configMap))
  @transient var redisCon: RedisClient = new RedisClient(params.get("redis-conn-ip"),params.get("redis-conn-port").toInt)

  override def open(parameters: Configuration) = {
    this.params = ParameterTool.fromMap(mapAsJavaMap(ProjectConfiguration.configMap))
    this.redisCon =  new RedisClient(params.get("redis-conn-ip"),params.get("redis-conn-port").toInt)
  }

  def apply(key: Tuple, window: GlobalWindow, input: Iterable[SemanticObservation], out: Collector[QCOutcomeQuantitative]): Unit = {

    // Retrieve the meta-data fields from the key and window elements
    val feature: String = key.getField(0).toString
    val procedure: String = key.getField(1).toString
    val observableproperty: String = key.getField(2).toString
    val testKey: String = feature + "::" + procedure + "::" + observableproperty +"::thresholds::delta::spike"

    /**
      * For three observation wide windows, identify the absolute
      * delta value between points the sum of A->B, B->C minus A->C.
      */
    if(input.size == 3) {

      val sortedWindow: List[Double] = input.toList.sortBy(_.phenomenontimestart).map(_.numericalObservation.get)
      val observationDeltaA: Double = math.abs(sortedWindow(0) - sortedWindow(1))
      val observationDeltaB: Double = math.abs(sortedWindow(1) - sortedWindow(2))
      val observationDeltaC: Double = math.abs(sortedWindow(0) - sortedWindow(2))

      val observationDelta: Double = (observationDeltaA + observationDeltaB) - observationDeltaC

      // Using the stream meta-data and window size category, lookup the threshold tests
      val deltaTests: Option[String] = try {
        this.redisCon.get(testKey)
      }catch {
        case e: Exception => None
      }

      // Check that a value was returned from the registry, and if so
      // split on '::', and iterate over each item
      if(deltaTests.isDefined) {
        val individualTests: Array[String] = deltaTests.get.split("::")

        val middleObs: Option[SemanticObservation] = input.toList.sortBy(_.phenomenontimestart).lift(1)

        // Call the test iterator
        processTest(individualTests, middleObs, observationDelta, input.head.phenomenontimestart)
      }
    }

    /**
      * This function takes the list of tests to be applied, and recursively iterates
      * over it.  For each test, the upper and lower bounds are retrieved and compared
      * with the observation value.  If the bounds are not exceeded, a pass value is
      * emitted, else a fail is emitted for every observation within the window, or
      * observations iterable - which in the case of Range checks is a single observation.
      *
      * Not separated into its own trait as unsure of how to deal with the
      * Redis connection at the moment.
      *
      * @param testList The list of checks necessary to undertake
      * @param observations The list of observations
      * @param observationValue The value being used with the checks
      * @param timeInstantMilli The middle time instant of multivalue observations,
      *                         or a single point's time instant.
      */
    def processTest(testList: Array[String],
                    observations: Iterable[SemanticObservation],
                    observationValue: Double,
                    timeInstantMilli: Long): Unit = {

      // Retrieve the current test at the head of the list
      val test: String = testList.head

      // Retrieve the type of test (whether static, or time point based)
      val testType: Option[String] = this.redisCon.get(testKey + "::" + test)

      if (testType.isDefined) {

        val timeInstant: LocalDateTime = LocalDateTime.ofEpochSecond(timeInstantMilli / 1000, 0, ZoneOffset.UTC)
        val currMin: Int = timeInstant.getMinute

        // Retrieve the min and max bounds based on the test type
        val minMaxKeys: (Option[String], Option[String]) = testType.getOrElse(None) match {
          case "single" => {
            /**
              * Single min/max value, not changing over time, simply retrieve
              * the min/max entries
              */
            (Some("::min"), Some("::max"))
          }
          case "hour" => {
            /**
              * Hourly point based threshold, must identify the closest hour
              * to the observation, and retrieve using the format:
              * %Y-%m-%dT%H:%M:%S e.g. 2016-01-01T22:23:45 => 2016-01-01T22
              */
            if (currMin <= 30) {
              val target: String = timeInstant.format(DateTimeFormatter.ofPattern("y-MM-dd'T'HH"))
              (Some("::min::" + target), Some("::max::" + target))
            }
            else {
              val target: String = timeInstant.plusHours(1).format(DateTimeFormatter.ofPattern("y-MM-dd'T'HH"))
              (Some("::min::" + target), Some("::max::" + target))
            }
          }
          case "day" => {
            /**
              * Daily point based threshold, must identify and retrieve using
              * the midday point closest to the observation
              * e.g. 2016-01-01T22:23:45 => 2016-01-01
              *
              * For this, it is a simple format, as we assume that exactly
              * midnight should fall on that day rather than the previous
              */
            val target: String = timeInstant.format(DateTimeFormatter.ofPattern("y-MM-dd"))
            (Some("::min::" + target), Some("::max::" + target))
          }
          case "month" => {
            /**
              * Month point based threshold, must identify and retrieve using
              * the month ID e.g. 2016-01-01T22:23:45 => 2016-01
              */
            val target: String = timeInstant.format(DateTimeFormatter.ofPattern("y-MM"))
            (Some("::min::" + target), Some("::max::" + target))
          }
          case _ => {
            /**
              * No match, error, do nothing at present
              */
            (None, None)
          }
        }

        /**
          * Min Max keys may or may not be defined, but Redis can still
          * be queried, and check only the value for comparison for
          * existence
          */
        val minCompareVal: Option[String] = try {
          this.redisCon.get(testKey + "::" + test + minMaxKeys._1.getOrElse(None))
        }catch {
          case e: Exception => None
        }
        val maxCompareVal: Option[String] = try {
          this.redisCon.get(testKey + "::" + test + minMaxKeys._2.getOrElse(None))
        }catch {
          case e: Exception => None
        }
        /**
          * For the min and max, compare to see if the bound is
          * exceeded, generate an outcome as necessary.
          */
        if(minCompareVal.isDefined) {

          val quantitativeVal: Double = minCompareVal.get.toDouble  - observationValue
          val testId: String = params.get("qc-threshold-delta-spike-prefix") + test + "/min"
          val outcome: String = if(quantitativeVal > 0) params.get("qc-outcome-fail") else params.get("qc-outcome-pass")

          observations.foreach(x =>
            out.collect(createQCOutcomeQuantitative(
              x,
              testId,
              outcome,
              quantitativeVal
            ))
          )
        }

        if(maxCompareVal.isDefined) {

          val quantitativeVal: Double = observationValue - maxCompareVal.get.toDouble
          val testId: String = params.get("qc-threshold-delta-spike-prefix") + test + "/max"
          val outcome: String = if(quantitativeVal > 0) params.get("qc-outcome-fail") else params.get("qc-outcome-pass")

          observations.foreach(x =>
            out.collect(createQCOutcomeQuantitative(
              x,
              testId,
              outcome,
              quantitativeVal
            ))
          )
        }
      }

      // If there are more tests to undertake, do so
      if(!testList.tail.isEmpty){
        processTest(testList.tail,
          observations,
          observationValue,
          timeInstantMilli)
      }
    }
  }
}