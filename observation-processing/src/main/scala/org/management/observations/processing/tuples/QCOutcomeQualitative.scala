package org.management.observations.processing.tuples

/**
  * QCOutcomeQualitative
  *
  * - Represents the qualitative only QC checks that use either
  *   a boolean or categorical outcome value
  *
  * @param feature the site the observation was generated at, assumed static
  * @param procedure the sensor that observed the value
  * @param observableproperty the phenomena the sensor is observing
  * @param year the year of the observation
  * @param month the month of the observation
  * @param phenomenontimestart the timestamp
  * @param qualifier the QC check being performed
  * @param qualitative the outcome of the QC check
  */
case class QCOutcomeQualitative(feature: String,
                                procedure: String,
                                observableproperty: String,
                                year: Int,
                                month: Int,
                                phenomenontimestart: Long,
                                qualifier: String,
                                qualitative: String)  {

  override def toString: String = feature + "::" +
    procedure + "::" +
    observableproperty + "::" +
    year.toString +"::" +
    month.toString + "::" +
    phenomenontimestart.toString + "::" +
    qualifier + "::" +
    qualitative + "::" +
    "Auto-generated."
}
