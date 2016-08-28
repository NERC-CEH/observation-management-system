package org.management.observations.processing.tuples

/**
  * QCEvent
  *
  * - Used to represent QC events, which do not identify single observations
  *   such as QCOutcomeQualitative/Quantitative based tuples.  These identify
  *   temporal durations where a QC issue has been identified, and needs to be
  *   looked at.  Essentially a flag in the timeseries for areas of interest.
  *
  * @param feature the site the observation was generated at, assumed static
  * @param procedure the sensor that observed the value
  * @param observableproperty the phenomena the sensor is observing
  * @param event the QC event taking place
  * @param eventtimestart the timestamp of the start of the event
  * @param eventtimeend the timestamp of the start of the event
  */

case class QCEvent(feature: String,
                   procedure: String,
                   observableproperty: String,
                   event: String,
                   eventtimestart: Long,
                   eventtimeend: Long){

  override def toString: String  = feature + "," +
    procedure + "," +
    observableproperty + "," +
    event + "," +
    eventtimestart.toString + "," +
    eventtimeend.toString
}
