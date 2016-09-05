package org.management.observations.processing.bolts.transform

// The function being extended
import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.configuration.Configuration

// The tuples used within this bolt
import org.management.observations.processing.tuples.RawObservation

// The connection to the registry
import com.redis.RedisClient

/**
  * RawCSVToObservation
  *
  * - Takes a string CSV representation of an observation, performs
  *   checks to ensure it corresponds to the expected format and
  *   returns the original string with the check outcome summary.
  *   Also decides whether the observation is numeric or categorical
  */
class RawCSVToObservation extends RichMapFunction[String, RawObservation]{

  @transient var redisCon: RedisClient = new RedisClient("10.0.0.3",6379)

  override def open(parameters: Configuration) = {
    this.redisCon =  new RedisClient("10.0.0.3",6379)
  }

  def map(in: String): RawObservation = {

    /**
      * Four checks are performed:
      *
      * - Does the observation have the correct number of fields,
      *   and a value
      * - Does the observation have a millisecond timestamp
      * - Does the value conform to the numeric or categorical type
      * - Does the observation metadata have corresponding match
      *   in the registry
      */
    val correctFields: Boolean = in.split(",").size == 4

    if(correctFields) {

      // Check whether the observed value has a size or is empty
      val correctValueSize: Boolean = {
        in.split(",")(0).size > 0 &&
          in.split(",")(1).size > 0 &&
          in.split(",")(2).size > 0 &&
          in.split(",")(3).size > 0
      }

      // Check the time representation is the correct format
      val timeMilli: Boolean = try {
        in.split(",")(2).toLong; true
      } catch {
        case e: Exception => false
      }

      // Does the value conform to a numeric or categorical representation
      val numericValue: Boolean = {
        if (in.split(",")(3) == "NotAValue") true
        else {
          try {
            in.split(",")(3).toDouble; true
          } catch {
            case e: Exception => false
          }
        }
      }

      /**
        * Is the metadata in the registry? Create var's as we want to use
        * the registry values later if they match.
        */
      val lookupKey = in.split(",")(0) + "::" + in.split(",")(1)

      val feature: Option[String] = try{
        this.redisCon.get(lookupKey + "::feature")
      }catch{
        case e: Exception => None
      }
      val procedure: Option[String] = try {
        this.redisCon.get(lookupKey + "::procedure")
      }catch {
        case e: Exception => None
      }

      val observableproperty: Option[String] = try{
        this.redisCon.get(lookupKey + "::observableproperty")
      }catch {
        case e: Exception => None
      }

      val registryOK: Boolean = {
        if (feature.isEmpty || procedure.isEmpty || observableproperty.isEmpty) false
        else true
      }

      /**
        * Use the outcome of the above tests to generate the RawObservation
        * output objects.
        */
      val dataType: String = if(numericValue) "Numerical" else "Categorical"

      val parseOK: Boolean = correctValueSize && timeMilli && registryOK

      val parseMessage: String = {
        if(!correctValueSize) "Malformed observation tuple."
        else if(!registryOK) "Registry lookup failed."
        else if(!timeMilli) "Incorrect time representation."
        else "Parsed OK."
      }

      val currObs: String = {
        if (parseOK) feature.get + "," + procedure.get + "," + observableproperty.get + "," + in.split(",")(2)+","+in.split(",")(3)
        else in
      }

      new RawObservation(
        currObs,
        dataType,
        parseOK,
        parseMessage
      )
    }else{
      new RawObservation(
        in,
        "Unknown",
        false,
        "Malformed observation tuple."
      )
    }
  }
}