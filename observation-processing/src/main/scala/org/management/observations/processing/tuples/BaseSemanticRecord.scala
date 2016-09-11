package org.management.observations.processing.tuples

/**
  * Abstract base class that the following extend:
  *
  *   - MetaOutcomeQualitative
  *   - MetaOutcomeQuantitative
  *   - RawObservation
  *   - SemanticObservation
  *   - QCOutcomeQualitative
  *   - QCOutcomeQuantitative
  *
  *   Used to allow type hierarchy use in function calls
  *   within SemanticObservationFlow.
  */
abstract class BaseSemanticRecord {
  def feature: String
  def procedure: String
  def observableproperty: String

}
