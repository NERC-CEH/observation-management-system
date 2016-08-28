package org.management.observations.processing.bolts.transform

// The function being extended
import org.apache.flink.api.common.functions.RichMapFunction

// The tuple being output
import org.management.observations.processing.tuples.MetaDataObservation

/**
  * RawCSVToObservation
  *
  * 3 checks are performed:
  *
  * - Does the observation have the correct number of fields and a value
  * - Does the observation have a millisecond time range
  * - Does the value conform to a numeric or null type
  *
  */
class RawCSVToMetaRecord extends RichMapFunction[String, MetaDataObservation]{
  def map(in: String): MetaDataObservation = {

    val correctFields: Boolean = in.split(",").size == 5

    if(correctFields){

      // Check whether the observation values have a size or are empty
      val correctValueSize: Boolean = {
        in.split(",")(0).size > 0 &&
        in.split(",")(1).size > 0 &&
        in.split(",")(2).size > 0 &&
        in.split(",")(3).size > 0 &&
        in.split(",")(4).size > 0
      }

      // Check the time is in the expected format
      val timeMilli: Boolean = {
        try{
          in.split(",")(2).toLong
          in.split(",")(3).toLong
          true
        }catch{
          case e: Exception => false
        }
      }

      // Check the value is null or able to be parsed
      val okValue: Boolean = {
        if(in.split(",")(4) == "NotAValue") true
        else{
          try{
            in.split(",")(4).toDouble
            true
          }catch{
            case e: Exception => false
          }
        }
      }

      // Flag if the value is numeric or not
      val numericValue: Boolean = {
        if(in.split(",")(4) == "NotAValue") false
        else{
          try{
            in.split(",")(4).toDouble
            true
          }catch{
            case e: Exception => false
          }
        }
      }

      val rawArray = in.split(",")

      val parseMessage = {
        if(!correctValueSize)
          "Parse failed.  Missing values, check all columns for values."
        else if(!timeMilli)
          "Parse failed.  Start and/or End time are malformed."
        else if(!okValue)
          "Parse failed.  Value is not numeric or correct text value."
        else
          "Parsed OK."
      }

      if(!correctValueSize || !timeMilli || !okValue){
        new MetaDataObservation(
          rawArray.mkString("::")+":endOfMalformedContents",
          "unknown",
          "unknown",
          "unknown",
          "unknown",
          false,
          parseMessage
        )
      }else{
        new MetaDataObservation(
          rawArray(0),
          rawArray(1),
          rawArray(2),
          rawArray(3),
          rawArray(4),
          true,
          parseMessage)
      }
    }else{
      new MetaDataObservation(
        in+"::"+":endOfMalformedContents",
        "unknown",
        "unknown",
        "unknown",
        "unknown",
        false,
        "Parse failed.  Malformed CSV, missing columns.")
    }
  }
}
