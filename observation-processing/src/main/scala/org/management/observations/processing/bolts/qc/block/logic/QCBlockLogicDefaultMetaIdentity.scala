package org.management.observations.processing.bolts.qc.block.logic

// For connection to registry
import com.redis.RedisClient

// Function being extended
import org.apache.flink.api.common.functions.RichFlatMapFunction

// Rich open() function configuration
import org.apache.flink.configuration.Configuration

// Collector used to group the outcome objects for new data stream
import org.apache.flink.util.Collector

// Import the tuples
import org.management.observations.processing.tuples._

/**
  * QCBlockLogicDefaultMetaIdentity
  *
  * Iterates over each particular identity check that the current feature
  * can have, and produces a pass outcome for each
  */
class QCBlockLogicDefaultMetaIdentity extends RichFlatMapFunction[SemanticObservation, QCOutcomeQualitative] with SemanticObservationFlow{

  // Create the connection to the registry
  @transient var redisCon: RedisClient = new RedisClient("localhost", 6379)

  override def open(parameters: Configuration) = {
    this.redisCon = new RedisClient("localhost", 6379)
  }

  def flatMap(in: SemanticObservation, out: Collector[QCOutcomeQualitative]): Unit = {

    val identityChecks: Option[String] = this.redisCon.get(in.feature + "::meta::identity")

    if(identityChecks.isDefined){

        identityChecks.get.split("::").foreach(x => {
        out.collect(createQCOutcomeQualitative(in,
          "http://placeholder.catalogue.ceh.ac.uk/qc/meta/identity/"+ x,
          "pass"))
      })
    }
  }
}
