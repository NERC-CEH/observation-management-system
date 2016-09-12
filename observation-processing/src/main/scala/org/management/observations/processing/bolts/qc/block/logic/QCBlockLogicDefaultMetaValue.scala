package org.management.observations.processing.bolts.qc.block.logic

// For connection to registry
import com.redis.RedisClient
import org.apache.flink.api.java.utils.ParameterTool

// Function being extended
import org.apache.flink.api.common.functions.RichFlatMapFunction

// Rich open() function configuration
import org.apache.flink.configuration.Configuration

// Collector used to group the outcome objects for new data stream
import org.apache.flink.util.Collector

// Import the tuples
import org.management.observations.processing.tuples._

// System KVP properties
import org.management.observations.processing.ProjectConfiguration
import scala.collection.JavaConversions._

/**
  * QCBlockLogicDefaultMetaValue
  *
  * Iterates over each particular value check that the current feature
  * can have, and produces a pass outcome for each
  */
class QCBlockLogicDefaultMetaValue extends RichFlatMapFunction[SemanticObservation, QCOutcomeQuantitative] with SemanticObservationFlow{

  @transient var params: ParameterTool = ParameterTool.fromMap(mapAsJavaMap(ProjectConfiguration.configMap))
  // Create the connection to the registry
  @transient var redisCon: RedisClient = new RedisClient(params.get("redis-conn-ip"),params.get("redis-conn-port").toInt)

  override def open(parameters: Configuration) = {
    this.params = ParameterTool.fromMap(mapAsJavaMap(ProjectConfiguration.configMap))
    this.redisCon = new RedisClient(params.get("redis-conn-ip"),params.get("redis-conn-port").toInt)
  }

  def flatMap(in: SemanticObservation, out: Collector[QCOutcomeQuantitative]): Unit = {

    val valueChecks: Option[String] = try {
      this.redisCon.get(in.feature + "::meta::value")
    }catch {
      case e: Exception => None
    }

    if(valueChecks.isDefined){

      /**
        * For each check type, e.g. battery,
        * it is necessary to retrieve all the types of
        * check that may be applied, e.g.
        * battery/static, battery/hourly etc.
        */
      valueChecks.get.split("::").foreach(valCheck => {

        val checkInstance: Option[String] = try{
          this.redisCon.get(in.feature +"::meta::value::" + valCheck + "::thresholds::range")
        }catch{
          case e: Exception => None
        }

        if(checkInstance.isDefined){
          checkInstance.get.split("::").foreach(valCheckInstance =>{

            out.collect(createQCOutcomeQuantitative(in,
              "http://placeholder.catalogue.ceh.ac.uk/qc/meta/value/" + valCheck + "/" + valCheckInstance + "/min",
              "pass",
              0))
            out.collect(createQCOutcomeQuantitative(in,
              "http://placeholder.catalogue.ceh.ac.uk/qc/meta/value/" + valCheck + "/" + valCheckInstance + "/max",
              "pass",
              0))

          })
        }
      })
    }
  }
}
