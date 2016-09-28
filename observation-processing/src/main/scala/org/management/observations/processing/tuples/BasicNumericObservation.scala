package org.management.observations.processing.tuples

/**
  * This class, a cut down version of SemanticObservation, that is specialised
  * for use by numeric values used in windowed aggregates.  It's used to create
  * a smaller per-observation footprint within the window aggregates.
  *
  * @param feature the site the observation was generated at, assumed static
  * @param procedure the sensor that observed the value
  * @param observableproperty the property being observed
  * @param routingkey the routing key, if used, with which to route this particular observation
  * @param phenomenontimestart the datetime that the observation was recorded, format %Y-%m-%dT%H:%M:%D
  * @param phenomenontimeend the datetime that the observation completed being recorded, format %Y-%m-%dT%H:%M:%D
  * @param numericalObservation the numeric value of the observation
  */
case class BasicNumericObservation(feature: String,
                                   procedure: String,
                                   observableproperty: String,
                                   year: Int,
                                   month: Int,
                                   routingkey: String,
                                   phenomenontimestart: Long,
                                   phenomenontimeend: Long,
                                   numericalObservation: Double){
}