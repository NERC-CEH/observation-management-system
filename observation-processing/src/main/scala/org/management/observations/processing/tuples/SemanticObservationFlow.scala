package org.management.observations.processing.tuples

/**
  * SemanticObservationFlow
  *
  * The purpose of this trait is to create single instances of
  * functions that take a semantic observation and other parameters
  * and create the QCOutcomeX, QCMetaOutcomeX, and QCEvent objects
  */

trait SemanticObservationFlow {

  def createQCOutcomeQualitative(obs: SemanticObservation,
                                 qualifier: String,
                                 qualitative: String): QCOutcomeQualitative = {
    new QCOutcomeQualitative(
      obs.feature,
      obs.procedure,
      obs.observableproperty,
      obs.year,
      obs.month,
      obs.phenomenontimestart,
      qualifier,
      qualitative
    )
  }

  def createQCOutcomeQuantitative(obs: SemanticObservation,
                                  qualifier: String,
                                  qualitative: String,
                                  quantitative: Double): QCOutcomeQuantitative = {
    new QCOutcomeQuantitative(
      obs.feature,
      obs.procedure,
      obs.observableproperty,
      obs.year,
      obs.month,
      obs.phenomenontimestart,
      qualifier,
      qualitative,
      quantitative
    )
  }

  def createQCEvent(obs: BaseSemanticRecord,
                    event: String,
                    eventTimeStart: Long,
                    eventTimeEnd: Long): QCEvent ={
    new QCEvent(
      obs.feature,
      obs.procedure,
      obs.observableproperty,
      event,
      eventTimeStart,
      eventTimeEnd)
  }
}

