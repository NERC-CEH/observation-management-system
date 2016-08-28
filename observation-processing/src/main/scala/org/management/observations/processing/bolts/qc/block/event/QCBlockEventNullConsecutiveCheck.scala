package org.management.observations.processing.bolts.qc.block.event

import com.redis.RedisClient
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.api.java.tuple.Tuple
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.scala.function.RichWindowFunction
import org.apache.flink.streaming.api.windowing.windows.GlobalWindow
import org.apache.flink.util.Collector
import org.management.observations.processing.tuples.{QCEvent, SemanticObservation}

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
class QCBlockEventNullConsecutiveCheck extends RichWindowFunction[SemanticObservation, QCEvent, Tuple, GlobalWindow] {

  /**
    * Tuple3:
    *   Long: previous observation timestamp
    *   Int: count of consecutive observations
    *   Boolean: has an alert been generated on the current consecutive run
    */
  var state: ValueState[Tuple3[Long,Int, Boolean]] = null

  @transient var redisCon: RedisClient = new RedisClient("localhost", 6379)

  override def open(parameters: Configuration) = {
    this.redisCon = new RedisClient("localhost", 6379)

    // Register the state variable
    state = getRuntimeContext.getState(new ValueStateDescriptor("QCNullConsecutive",classOf[Tuple3[Long, Int, Boolean]],null))
  }

  def apply(key: Tuple, window: GlobalWindow, input: Iterable[SemanticObservation], out: Collector[QCEvent]): Unit = {

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
      val expectedTimeDiff = this.redisCon.get(input.head.feature+":"+input.head.procedure+":"+input.head.observableproperty+":meta:intendedspacing")
      if(expectedTimeDiff.isDefined){
        val wideTimeDiff = expectedTimeDiff.get.toLong + expectedTimeDiff.get.toLong * 0.5

        // Check if the observations are consecutive
        val isConsecutive = input.head.phenomenontimestart - state.value()._1 <= wideTimeDiff

        /**
          * If observations are consecutive retrieve threshold if exists, update count,
          * and check whether an alert should be generated, if no threshold exists,
          * reset all values
           */
        if(isConsecutive) {

          val currentCount = state.value()._2 + 1
          val countThreshold = this.redisCon.get(input.head.feature+":"+input.head.procedure+":"+input.head.observableproperty+":thresholds:null:consecutive:")

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
                    "Consecutive Null values exceeds threshold",
                    input.head.phenomenontimestart,
                    input.head.phenomenontimeend)
                )
              }
              state.update((input.head.phenomenontimestart, currentCount, true))
            }else{
              // When the threshold is not matched or exceeded, update the timestamp
              // and count, and ensure event is false
              state.update((input.head.phenomenontimestart, currentCount, false))
            }
          }else{
            // When threshold not defined, set to empty
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