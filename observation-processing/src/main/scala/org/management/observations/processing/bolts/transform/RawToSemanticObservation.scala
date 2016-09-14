package org.management.observations.processing.bolts.transform

// Used for retrieving the semantic information from the registry
import java.time.ZoneOffset

// Used for extracting the month and year from the timestamp
import java.time.LocalDateTime

// The function being extended
import org.apache.flink.api.common.functions.MapFunction

// The tuples used within this bolt
import org.management.observations.processing.tuples.RawObservation
import org.management.observations.processing.tuples.SemanticObservation


/**
  * RawToSemanticObservation
  *
  * - Transform a raw observation into a semantic observation by
  *   parsing the datetime, separating the metadata fields into
  *   separate variables, and adding the extra fields necessary
  *   to record processing and observation status within the
  *   database
  *
  * - It is expected that RawCSVToObservation has performed the
  *   checks necessary to get to this level of processing.
  */
class RawToSemanticObservation extends MapFunction[RawObservation, SemanticObservation] {

  def map(in: RawObservation):SemanticObservation = {

    val currObservation = in.observation.split(",")

    val feature = currObservation(0)
    val procedure = currObservation(1)
    val observableproperty = currObservation(2)

    // Parse the year and month from the timestamp
    val year = LocalDateTime.ofEpochSecond(currObservation(3).toLong/1000,0,ZoneOffset.UTC).getYear
    val month = LocalDateTime.ofEpochSecond(currObservation(3).toLong/1000,0,ZoneOffset.UTC).getMonthValue
    val phenomenontimestart = currObservation(3).toLong
    val phenomenontimeend = phenomenontimestart

    // Create the entry for the numeric observation
    val numericValue: Option[Double] = {
      if(in.observationType == "numeric" || in.observationType == "count") {
        if (currObservation(4) == "NotAValue")
          None
        else
          Some(currObservation(4).toDouble)
      }else{
        None
      }
    }

    // Create the entry for the categorical observation
    val categoricValue: Option[String] = {
      if(in.observationType == "category") {
        Some(currObservation(4))
      }else{
        None
      }
    }

    // Create the metadata map when metadata records are provided
    val metaMap = scala.collection.mutable.Map[String, String]()
    if(currObservation.size == 6){
      currObservation(5).split("::").foreach(x => metaMap.put(x.split("=")(0), x.split("=")(1)))
    }


    /**
      * Create the initial values for the stage of
      * processing.
      *
      * TODO: modify to lookup the registry as each sensor
      * will have its group of settings depending on
      * further processing required of it.
      */
    val quality = 0
    val accuracy = 0
    val status = "Raw"
    val processing = "SemanticStamp"
    val uncertml = None
    val comment = "No processing performed."
    val location = None
    val parameters: Option[scala.collection.mutable.Map[String, String]] =
      if(metaMap.keys.nonEmpty) Some(metaMap)
      else None

    new SemanticObservation(feature,
      procedure,
      observableproperty,
      year,
      month,
      phenomenontimestart,
      phenomenontimeend,
      in.observationType,
      categoricValue,
      numericValue,
      quality,
      accuracy,
      status,
      processing,
      uncertml,
      comment,
      location,
      parameters)
  }
}
