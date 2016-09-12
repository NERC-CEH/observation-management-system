package org.management.observations.processing.bolts.qc.block.threshold
// Used for connecting to the Redis registry
import com.redis.RedisClient
import org.apache.flink.api.java.utils.ParameterTool

// The function being extended and collecting output
import org.apache.flink.api.common.functions.RichFlatMapFunction

// Used for passing parameters to the open() function
import org.apache.flink.configuration.Configuration

// Used for parsing timestamps
import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneOffset}

// The collector for objects to return into the datastream
import org.apache.flink.util.Collector

// The tuples used within this bolt
import org.management.observations.processing.tuples.{QCOutcomeQuantitative, SemanticObservation, SemanticObservationFlow}

// System KVP properties
import org.management.observations.processing.ProjectConfiguration
import scala.collection.JavaConversions._

/**
  * QCBlockThresholdRangeCheck
  *
  * - performs a check on a single observation against minimum and
  *     maximum thresholds.  A unique feature, procedure, observed
  *     phenomenon combination can have zero or more thresholds and
  *     this iterates over those found, emitting QCOutcomeQualitative
  *     objects for each check performed.
  *
  * - Min/Max values can be created/referenced in a range of ways depending on
  *     the method of generation.  Some thresholds may use values that
  *     do not change over time, others may use thresholds centered around
  *     hourly, daily, or monthly points.  With those that change centre around
  *     specific temporal points it is necessary to identify the resolution of
  *     the point (hourly, half-daily, daily, monthly), and to then identify
  *     the exact point closest to the current observation.
  */
class QCBlockThresholdRangeCheck extends RichFlatMapFunction[SemanticObservation,QCOutcomeQuantitative] with SemanticObservationFlow{

  @transient var params: ParameterTool = ParameterTool.fromMap(mapAsJavaMap(ProjectConfiguration.configMap))
  @transient var redisCon: RedisClient = new RedisClient(params.get("redis-conn-ip"),params.get("redis-conn-port").toInt)

  override def open(parameters: Configuration) = {
    this.params = ParameterTool.fromMap(mapAsJavaMap(ProjectConfiguration.configMap))
    this.redisCon =  new RedisClient(params.get("redis-conn-ip"),params.get("redis-conn-port").toInt)
  }

  def flatMap(obs: SemanticObservation, out: Collector[QCOutcomeQuantitative]): Unit = {

    // Create the static part of the registry key
    val testKey: String = obs.feature+"::"+obs.procedure+"::"+obs.observableproperty+"::thresholds::range"

    // Connect to registry and retrieve the threshold tests
    val rangeTests = try {
      this.redisCon.get(testKey)
    }catch {
      case e: Exception => None
    }

    // Check that a value was returned from the registry, and if so
    // split on '::', and iterate over each item
    if(rangeTests.isDefined){
      val individualTests: Array[String] = rangeTests.get.split("::")

      // Call the test iterator
      processTest(individualTests, List(obs), obs.numericalObservation.get, obs.phenomenontimestart)

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
            val testId: String = "http://placeholder.catalogue.ceh.ac.uk/qc/range/" + test + "/min"
            val outcome: String = if(quantitativeVal > 0) "fail" else "pass"

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
            val testId = "http://placeholder.catalogue.ceh.ac.uk/qc/range/" + test + "/max"
            val outcome = if(quantitativeVal > 0) "fail" else "pass"

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
}