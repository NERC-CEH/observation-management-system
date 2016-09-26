package org.management.observations.processing.timing

import org.apache.flink.streaming.api.functions.AssignerWithPeriodicWatermarks
import org.apache.flink.streaming.api.watermark.Watermark
import org.management.observations.processing.tuples.BasicObservation

/**
  * LakeAnalyzerEventTime
  *
  * - Create timestamps and watermarks in the stream of tuples used to create
  *   derived data from the rLakeAnalyzer package
  *
  * - Allow windows to have out of order events up to maxOutOfOrderness value,
  *   measured in milliseconds, in this case, two hours
  */
class LakeAnalyzerEventTime extends AssignerWithPeriodicWatermarks[BasicObservation]{

  /**
    * All values below are defined in milliseconds, with currentMaxTimestamp
    * and lastEmittedWatermark defining the distance between origin and
    * their represented time point.
    */
  private var currentMaxTimestamp: Long = 0
  private var lastEmittedWatermark: Long = 0
  private val maxOutOfOrderness: Long = 14400000

  def extractTimestamp(obsTup: BasicObservation, prevTime: Long): Long = {

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
