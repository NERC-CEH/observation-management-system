package org.management.observations.processing.tuples

/**
  * MetaOutcomeQualitative
  *
  * - Represents the qualitative only MetaQC check outcome where
  *   an event has been recorded and affects observations.
  *
  * - This needs to be parsed and used as an update query against the
  *   database using the phenomenon time bounds to limit the effect.
  *   This differs from the QCOutcomeQualitative as that only needs
  *   the start time of the observation of interest, while this needs
  *   to update a potential range of observations.
  *
  * @param feature the site the observation was generated at, assumed static
  * @param procedure the sensor that observed the value
  * @param observableproperty the phenomena the sensor is observing
  * @param phenomenontimestart the start of the duration
  * @param phenomenontimeend the end of the duration
  * @param qualifier the QC check being performed
  * @param qualitative the outcome of the QC check
  */
case class MetaOutcomeQualitative(feature: String,
                                procedure: String,
                                observableproperty: String,
                                phenomenontimestart: Long,
                                phenomenontimeend: Long,
                                qualifier: String,
                                qualitative: String) {

  override def toString: String = feature + "," +
    procedure + "," +
    observableproperty + "," +
    phenomenontimestart.toString + "," +
    phenomenontimeend.toString + "," +
    qualifier + "," +
    qualitative
}
