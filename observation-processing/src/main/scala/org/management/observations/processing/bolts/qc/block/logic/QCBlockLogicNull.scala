package org.management.observations.processing.bolts.qc.block.logic

// Tuples used in the processing
import org.management.observations.processing.tuples.{QCOutcomeQualitative, SemanticObservation, SemanticObservationFlow}

// Function being extended
import org.apache.flink.api.common.functions.MapFunction

/**
  * QCBlockLogicNull
  *
  * Checks that a value exists for a given value type, if not
  * the null check fails.
  */
class QCBlockLogicNull extends MapFunction[SemanticObservation, QCOutcomeQualitative] with SemanticObservationFlow{

  def map(in: SemanticObservation): QCOutcomeQualitative = {

    if((in.observationType == "numeric" || in.observationType == "count") && in.numericalObservation.isEmpty){
      createQCOutcomeQualitative(in,
        "http://placeholder.catalogue.ceh.ac.uk/qc/null/value",
        "fail"
      )
    }else if(in.observationType == "category" && in.categoricalObservation.isEmpty){
      createQCOutcomeQualitative(in,
        "http://placeholder.catalogue.ceh.ac.uk/qc/null/value",
        "fail"
      )
    }else{
      createQCOutcomeQualitative(in,
        "http://placeholder.catalogue.ceh.ac.uk/qc/null/value",
        "pass"
      )
    }
  }
}
