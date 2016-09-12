package org.management.observations.processing.bolts.transform

// The function being extended
import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.configuration.Configuration

// The tuples used within this bolt
import org.management.observations.processing.tuples.RawObservation

// The connection to the registry
import com.redis.RedisClient

// System KVP properties
import org.management.observations.processing.ProjectConfiguration
import scala.collection.JavaConversions._

/**
  * RawCSVToObservation
  *
  * - Takes a string CSV representation of an observation, performs
  *   checks to ensure it corresponds to the expected format and
  *   returns the original string with the check outcome summary.
  *   Also decides whether the observation is numeric or categorical
  */
class RawCSVToObservation extends RichMapFunction[String, RawObservation]{

  // Read the parameter configuration file
  @transient var params: ParameterTool = ParameterTool.fromMap(mapAsJavaMap(ProjectConfiguration.configMap))
  @transient var redisCon =  new RedisClient(params.get("redis-conn-ip"),params.get("redis-conn-port").toInt)


  override def open(parameters: Configuration) = {
    this.params = ParameterTool.fromMap(mapAsJavaMap(ProjectConfiguration.configMap))
    this.redisCon =  new RedisClient(params.get("redis-conn-ip"),params.get("redis-conn-port").toInt)
  }

  def map(in: String): RawObservation = {

    /**
      * Four checks are performed:
      *
      * - Does the observation have the correct number of fields,
      *   and a value
      * - Does the observation have a millisecond timestamp
      * - Does the value conform to the type held in the registry
      * - Does the observation metadata have corresponding match
      *   in the registry (feature, procedure, observable property)
      * - Does the observation have correctly formatted metadata fields
      */

    // Four fields without metadata, five with metadata
    val correctFields: Boolean = in.split(",").size == 4 || in.split(",").size == 5

    if(correctFields) {

      // Does the entry have metadata
      val hasMeta: Boolean = {
        if(in.split(",").size == 5)
          true
        else
          false
      }

      // Check whether the observed value has a size or is empty
      val correctValueSize: Boolean = {
        if(!hasMeta){
          in.split(",")(0).size > 0 &&
            in.split(",")(1).size > 0 &&
            in.split(",")(2).size > 0 &&
            in.split(",")(3).size > 0
        }else{
          in.split(",")(0).size > 0 &&
            in.split(",")(1).size > 0 &&
            in.split(",")(2).size > 0 &&
            in.split(",")(3).size > 0 &&
            in.split(",")(4).size > 0
        }
      }

      // Check the time representation is the correct format
      val timeMilli: Boolean = try {
        in.split(",")(2).toLong; true
      } catch {
        case e: Exception => false
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

      val observableProperty: Option[String] = try{
        this.redisCon.get(lookupKey + "::observableproperty")
      }catch {
        case e: Exception => None
      }

      val observationType: Option[String] = try{
        this.redisCon.get(lookupKey + "::observationtype")
      }catch {
        case e: Exception => None
      }

      val registryOK: Boolean = {
        if (feature.isEmpty || procedure.isEmpty || observableProperty.isEmpty || observationType.isEmpty) false
        else true
      }

      /**
        * Does the value conform to the registry held type?  "NotAValue" is
        * checked for first as this conforms to all types, if not, then
        * values parsed as expected.  Category, as a string, does not have
        * checks at present, future work will point it towards a list of
        * acceptable values.
        */
      val typeOK: Boolean = {
        if(observationType.isDefined){
          if(in.split(",")(3) == "NotAValue"){
            true
          }else if(observationType.get == "numeric"  || observationType.get == "count"){
            try {
              in.split(",")(3).toDouble; true
            } catch {
              case e: Exception => false
            }
          }else if(observationType.get == "category"){
            true
          }else{
            false
          }
        }else{
          false
        }
      }

      /**
        * Is the metadata formatted correctly, expected format is:
        * key=value::key=value::key=value
        */
      val metaOK: Boolean = {
        if(!hasMeta){
          true
        }else{
          if(in.split(",")(4).split("::").filter(_.split("=").size != 2).size == 0){
            true
          }else{
            false
          }
        }
      }

      /**
        * Use the outcome of the above tests to generate the RawObservation
        * output objects.
        */
      val parseOK: Boolean = correctValueSize && timeMilli && registryOK && typeOK && metaOK

      val parseMessage: String = {
        if(!correctValueSize) "Malformed observation tuple."
        else if(!registryOK) "Registry lookup failed."
        else if(!timeMilli) "Incorrect time representation."
        else if(!typeOK) "Observation type from registry not matched."
        else if(!metaOK) "Metadata is not correctly formatted."
        else "Parsed OK."
      }

      val currObs: String = {
        if (parseOK && !hasMeta)
          feature.get + "," + procedure.get + "," + observableProperty.get + "," + in.split(",")(2)+","+in.split(",")(3)
        else if (parseOK && hasMeta)
          feature.get + "," + procedure.get + "," + observableProperty.get + "," + in.split(",")(2)+","+in.split(",")(3)+","+in.split(",")(4)
        else in
      }

      val obsType: String = {
        if(parseOK) observationType.get
        else "Unknown"
      }

      new RawObservation(
        currObs,
        obsType,
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