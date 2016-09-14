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
  * QCBlockLogicDefaultMetaIdentity
  *
  * Iterates over each particular identity check that the current feature
  * can have, and produces a pass outcome for each
  */
class QCBlockLogicDefaultMetaIdentity extends RichFlatMapFunction[SemanticObservation, QCOutcomeQualitative] with SemanticObservationFlow{

  @transient var params: ParameterTool = ParameterTool.fromMap(mapAsJavaMap(ProjectConfiguration.configMap))
  // Create the connection to the registry
  @transient var redisCon: RedisClient = new RedisClient(params.get("redis-conn-ip"),params.get("redis-conn-port").toInt)

  override def open(parameters: Configuration) = {
    this.params = ParameterTool.fromMap(mapAsJavaMap(ProjectConfiguration.configMap))
    this.redisCon = new RedisClient(params.get("redis-conn-ip"),params.get("redis-conn-port").toInt)
  }

  def flatMap(in: SemanticObservation, out: Collector[QCOutcomeQualitative]): Unit = {

    val identityChecks: Option[String] = this.redisCon.get(in.feature + "::meta::identity")

    if(identityChecks.isDefined){

        identityChecks.get.split("::").foreach(x => {
        out.collect(createQCOutcomeQualitative(in,
          params.get("qc-logic-meta-identity-prefix") + x,
          params.get("qc-outcome-pass")))
      })
    }
  }
}
