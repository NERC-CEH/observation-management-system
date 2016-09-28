package org.management.observations.processing.bolts.qc.block.event

// Used for connecting to the Redis registry
import com.redis.RedisClient
import org.apache.flink.api.java.utils.ParameterTool

// The base class for the key tuple, in this case Tuple3
import org.apache.flink.api.java.tuple.Tuple

// Used for passing parameters to the open() function
import org.apache.flink.configuration.Configuration

// The function being extended
import org.apache.flink.streaming.api.scala.function.RichWindowFunction
import org.apache.flink.streaming.api.windowing.windows.GlobalWindow

// The collector for objects to return into the datastream
import org.apache.flink.util.Collector

// The tuples used within this bolt
import org.management.observations.processing.tuples.{QCEvent, QCOutcomeQualitative}

// System KVP properties
import org.management.observations.processing.ProjectConfiguration
import scala.collection.JavaConversions._

// State libraries
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}

/**
  * QCCheckNullConsecutive
  *
  * - Checks that null observations are within the expected intended observation
  *    spacing as a sign of consecutive generation (as this stream is separate from
  *    the entire stream).  The current observation timestamp is within the window,
  *    while the previous is held in the state tuple
  *
  * - If observations are consecutive, increment the state tuple counting
  *    consecutive observations
  *
  * - If state count exceeds threshold, generate alert, update state variable
  *    with alert status true
  *
  * - If observations are not consecutive, set state variable with alert status to
  *   false and count to one.
  *
  * - If no threshold variable is within the registry, set state variable with alter
  * status to false and count to zero.
  */
class QCBlockEventNullConsecutiveCheck extends RichWindowFunction[QCOutcomeQualitative, QCEvent, Tuple, GlobalWindow] {

  /**
    * Tuple3:
    *   Long: previous observation timestamp
    *   Int: count of consecutive observations
    *   Boolean: has an alert been generated on the current consecutive run
    */
  var state: ValueState[Tuple3[Long,Int, Boolean]] = null

  @transient var params: ParameterTool = ParameterTool.fromMap(mapAsJavaMap(ProjectConfiguration.configMap))
  @transient var redisCon =  new RedisClient(params.get("redis-conn-ip"),params.get("redis-conn-port").toInt)

  override def open(parameters: Configuration) = {
    this.params = ParameterTool.fromMap(mapAsJavaMap(ProjectConfiguration.configMap))
    this.redisCon =  new RedisClient(params.get("redis-conn-ip"),params.get("redis-conn-port").toInt)

    // Register the state variable
    state = getRuntimeContext.getState(new ValueStateDescriptor("QCNullConsecutive",classOf[Tuple3[Long, Int, Boolean]],null))
  }

  def apply(key: Tuple, window: GlobalWindow, input: Iterable[QCOutcomeQualitative], out: Collector[QCEvent]): Unit = {

    //Check if this is the first observation, if so just initialise the state
    if(state.value() == null){
      state.update((input.head.phenomenontimestart, 0, false))
    }
    else{
      /**
        * Check that the difference in timestamps is within the observation range
        * by retrieving the intended spacing from the registry, allowing for a slightly
        * larger spacing in case of hardware resets, software updates etc. potentially
        * adding a delay in observation generation, but not enough to skip an observation
        * entirely.
        */

      val expectedTimeDiff = this.redisCon.get(input.head.feature+"::"+input.head.procedure+"::"+input.head.observableproperty+"::intendedspacing")
      if(expectedTimeDiff.isDefined){
        val wideTimeDiff = expectedTimeDiff.get.toLong * 1.5

        // Check if the observations are consecutive
        val isConsecutive = input.head.phenomenontimestart - state.value()._1 <= wideTimeDiff

        /**
          * If observations are consecutive retrieve threshold if exists, update count,
          * and check whether an alert should be generated, if no threshold exists,
          * reset all values
           */
        if(isConsecutive) {

          val currentCount = state.value()._2 + 1
          val countThreshold = this.redisCon.get(input.head.feature+"::"+input.head.procedure+"::"+input.head.observableproperty+"::thresholds::null::consecutive")

          if(countThreshold.isDefined){
            /**
              * If the count is equal to or exceeds the threshold, and an alert
              * has not previously been generated, create an event and update
              * the state with the new timestamp, count, and event status
              */
            if(currentCount >= countThreshold.get.toInt){
              if(!state.value()._3){
                out.collect(
                  new QCEvent(
                    input.head.feature,
                    input.head.procedure,
                    input.head.observableproperty,
                    params.get("qc-event-null-consecutive"),
                    input.head.phenomenontimestart,
                    input.head.phenomenontimestart)
                )
                state.update((input.head.phenomenontimestart, currentCount, true))
              }
            }else{
              // When the threshold is not matched or exceeded, update the timestamp
              // and count, and ensure event is false
              state.update((input.head.phenomenontimestart, currentCount, false))
            }
          }else{
            // When threshold not defined, set count to empty
            state.update((input.head.phenomenontimestart, 0, false))
          }
        }else {
          // When not consecutive, update the timestamp, set count to 1, and
          // update alert to false
          state.update((input.head.phenomenontimestart, 1, false))
        }
      }
    }
  }
}