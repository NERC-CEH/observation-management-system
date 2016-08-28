package org.management.observations.processing.timing

import org.apache.flink.streaming.api.functions.AssignerWithPeriodicWatermarks
import org.apache.flink.streaming.api.watermark.Watermark
import org.management.observations.processing.tuples.SemanticObservation

/**
  * ObservationEventTime
  *
  * - Create timestamps and watermarks in the stream of SemanticObservationNumeric
  *   records.
  *
  * - Allow windows to have out of order events up to maxOutOfOrderness value,
  *   measured in milliseconds
  */
class ObservationEventTime extends AssignerWithPeriodicWatermarks[SemanticObservation]{

  /**
    * All values below are defined in milliseconds, with currentMaxTimestamp
    * and lastEmittedWatermark defining the distance between origin and
    * their represented time point.
     */
  private var currentMaxTimestamp: Long = 0
  private var lastEmittedWatermark: Long = 0
  private val maxOutOfOrderness: Long = 6000

  def extractTimestamp(obsTup: SemanticObservation, prevTime: Long): Long = {

    val curTimestamp: Long = obsTup.phenomenontimestart

    if(curTimestamp > currentMaxTimestamp){
      currentMaxTimestamp = curTimestamp
    }

    curTimestamp
  }

  def getCurrentWatermark: Watermark = {

    val potentialWatermark: Long = currentMaxTimestamp + maxOutOfOrderness

    if(potentialWatermark >= lastEmittedWatermark){
      lastEmittedWatermark = potentialWatermark
    }

    new Watermark(lastEmittedWatermark)
  }
}