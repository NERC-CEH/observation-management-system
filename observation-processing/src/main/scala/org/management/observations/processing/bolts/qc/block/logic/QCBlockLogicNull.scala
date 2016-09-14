package org.management.observations.processing.bolts.qc.block.logic

// Tuples used in the processing
import com.redis.RedisClient
import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.configuration.Configuration
import org.management.observations.processing.ProjectConfiguration
import org.management.observations.processing.tuples.{QCOutcomeQualitative, SemanticObservation, SemanticObservationFlow}

import scala.collection.JavaConversions._

// Function being extended
import org.apache.flink.api.common.functions.MapFunction

/**
  * QCBlockLogicNull
  *
  * Checks that a value exists for a given value type, if not
  * the null check fails.
  */
class QCBlockLogicNull extends RichMapFunction[SemanticObservation, QCOutcomeQualitative] with SemanticObservationFlow{

  @transient var params: ParameterTool = ParameterTool.fromMap(mapAsJavaMap(ProjectConfiguration.configMap))


  override def open(parameters: Configuration) = {
    this.params = ParameterTool.fromMap(mapAsJavaMap(ProjectConfiguration.configMap))
  }

  def map(in: SemanticObservation): QCOutcomeQualitative = {

    if((in.observationType == "numeric" || in.observationType == "count") && in.numericalObservation.isEmpty){
      createQCOutcomeQualitative(in,
        params.get("qc-logic-null"),
        params.get("qc-outcome-fail")
      )
    }else if(in.observationType == "category" && in.categoricalObservation.isEmpty){
      createQCOutcomeQualitative(in,
        params.get("qc-logic-null"),
        params.get("qc-outcome-fail")
      )
    }else{
      createQCOutcomeQualitative(in,
        params.get("qc-logic-null"),
        params.get("qc-outcome-pass")
      )
    }
  }
}
