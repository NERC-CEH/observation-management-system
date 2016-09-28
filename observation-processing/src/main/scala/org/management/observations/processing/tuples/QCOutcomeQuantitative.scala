package org.management.observations.processing.tuples

/**
  * QCOutcomeQualitative
  *
  * - Represents the quantitative  QC checks that use either
  *   a boolean or categorical outcome value as well as a numeric representation
  *
  * @param feature the site the observation was generated at, assumed static
  * @param procedure the sensor that observed the value
  * @param observableproperty the phenomena the sensor is observing
  * @param year the year of the observation
  * @param month the month of the observation
  * @param phenomenontimestart the timestamp
  * @param qualifier the QC check being performed
  * @param qualitative the outcome of the QC check
  * @param quantitative the measured outcome of the QC check
  */
case class QCOutcomeQuantitative(feature: String,
                                 procedure: String,
                                 observableproperty: String,
                                 year: Int,
                                 month: Int,
                                 phenomenontimestart: Long,
                                 qualifier: String,
                                 qualitative: String,
                                 quantitative: Double)  {

  override def toString: String = feature + "::" +
    procedure + "::" +
    observableproperty + "::" +
    year.toString +"::" +
    month.toString + "::" +
    phenomenontimestart.toString + "::" +
    qualifier + "::" +
    qualitative + "::" +
    quantitative.toString + "::" +
    "Auto-generated."
}

