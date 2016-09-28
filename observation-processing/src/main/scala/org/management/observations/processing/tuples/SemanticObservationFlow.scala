package org.management.observations.processing.tuples

/**
  * SemanticObservationFlow
  *
  * A helper trait used to create single instances of functions that are used
  * by multiple jobs/bolts, taking parameters or string serialized objects.
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

  def createQCOutcomeQualitative(obs: BasicNumericObservation,
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

  def createQCOutcomeQualitative(serialized: String): QCOutcomeQualitative = {
    val qcSplit = serialized.split("::")
    new QCOutcomeQualitative(
      qcSplit(0),
      qcSplit(1),
      qcSplit(2),
      qcSplit(3).toInt,
      qcSplit(4).toInt,
      qcSplit(5).toLong,
      qcSplit(6),
      qcSplit(7)
    )
  }

  def createQCOutcomeQuantitative(obs: BasicNumericObservation,
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


  def createQCOutcomeQuantitative(serialized: String): QCOutcomeQuantitative = {
    val qcSplit = serialized.split("::")
    new QCOutcomeQuantitative(
      qcSplit(0),
      qcSplit(1),
      qcSplit(2),
      qcSplit(3).toInt,
      qcSplit(4).toInt,
      qcSplit(5).toLong,
      qcSplit(6),
      qcSplit(7),
      qcSplit(8).toDouble
    )
  }

  def createQCEvent(obs: BasicNumericObservation,
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

  def createBasicNumericObservationWOKey(obs: SemanticObservation): BasicNumericObservation ={
    new BasicNumericObservation(
      obs.feature: String,
      obs.procedure: String,
      obs.observableproperty: String,
      obs.year,
      obs.month,
      "NA",
      obs.phenomenontimestart: Long,
      obs.phenomenontimeend: Long,
      obs.numericalObservation.get: Double)
  }

  def createBasicNumericObservationWKey(obs: SemanticObservation, key: String): BasicNumericObservation ={
    new BasicNumericObservation(
      obs.feature: String,
      obs.procedure: String,
      obs.observableproperty: String,
      obs.year,
      obs.month,
      key,
      obs.phenomenontimestart: Long,
      obs.phenomenontimeend: Long,
      obs.numericalObservation.get: Double)
  }
}

