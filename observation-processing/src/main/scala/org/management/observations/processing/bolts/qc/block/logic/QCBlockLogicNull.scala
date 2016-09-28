package org.management.observations.processing.bolts.qc.block.logic

// Used for connecting to the Redis registry
import org.apache.flink.api.java.utils.ParameterTool

// Allows the use of the state variables
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}

// Used for passing parameters to the open() function
import org.apache.flink.configuration.Configuration

// The function being extended
import org.apache.flink.api.common.functions.RichMapFunction

// The tuples used within this bolt
import org.management.observations.processing.tuples.{QCOutcomeQualitative, SemanticObservation, SemanticObservationFlow}

// System KVP properties
import org.management.observations.processing.ProjectConfiguration


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
