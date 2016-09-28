package org.management.observations.processing.bolts.routing

// Execution environment
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.configuration.Configuration
import org.apache.flink.api.common.functions.{RichFlatMapFunction}
import org.apache.flink.util.Collector

// Redis connection
import com.redis.RedisClient

// The tuple types used within this job, and the bolts used to convert from the
// raw CSV format to a RawObservation, and from RawObservation to SemanticObservationNumeric
import org.management.observations.processing.tuples.{RoutedObservation, RoutedObservationDetail, SemanticObservation, SemanticObservationFlow}

// System KVP properties and time representations
import org.management.observations.processing.ProjectConfiguration
import scala.collection.JavaConversions._

/**
  * Class connects to the registry and retrieves the routing data for the given observation,
  * if no routing information is found, nothing is output.
  */
class InjectRoutingInfo extends RichFlatMapFunction[SemanticObservation, RoutedObservation] with SemanticObservationFlow {

  @transient var params: ParameterTool = ParameterTool.fromMap(mapAsJavaMap(ProjectConfiguration.configMap))
  @transient var redisCon: RedisClient = new RedisClient(params.get("redis-conn-ip"),params.get("redis-conn-port").toInt)

  override def open(parameters: Configuration) = {
    this.params = ParameterTool.fromMap(mapAsJavaMap(ProjectConfiguration.configMap))
    this.redisCon =  new RedisClient(params.get("redis-conn-ip"),params.get("redis-conn-port").toInt)
  }

  override def flatMap(obs: SemanticObservation, out:Collector[RoutedObservation]): Unit = {

    /**
      * Create the key to identify this observation in the registry, lookup
      * its routing information, and create the RoutedObservation.
      *
      * If there is no matching metadata in the registry, do not return the observation
      */
    val registryKey: String = obs.feature + "::" + obs.procedure + "::" + obs.observableproperty + "::routing"
    val routingMetadata: Option[String] = this.redisCon.get(registryKey)

    if(routingMetadata.isDefined) {
      val routes = routingMetadata.get.split("::").map(x => new RoutedObservationDetail(x.split(",")(0),x.split(",")(1),x.split(",")(2)))
      out.collect(new RoutedObservation(createBasicNumericObservationWOKey(obs),routes))
    }
  }
}
