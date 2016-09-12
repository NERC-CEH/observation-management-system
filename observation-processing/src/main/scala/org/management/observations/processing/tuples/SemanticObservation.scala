package org.management.observations.processing.tuples

/**
  * SemanticObservation
  *
  * @param procedure the sensor that observed the value
  * @param feature the site the observation was generated at, assumed static
  * @param observableproperty the property being observed
  * @param year the year the observation was recorded in, the initial year if spans multiple years
  * @param month the month the observation was recorded in, the initial month if spans multiple months
  * @param phenomenontimestart the datetime that the observation was recorded, format %Y-%m-%dT%H:%M:%D
  * @param phenomenontimeend the datetime that the observation completed being recorded, format %Y-%m-%dT%H:%M:%D
  * @param observationType the type of observation, numeric or categorical (using type hierarchy
  *                        with SemanticObservationNumeric/Categorical inheriting from a superclass, then
  *                        using the superclass as return type for map, apply etc did not work)
  * @param categoricalObservation optional categorical observation value
  * @param numericalObservation optional numerical observation value
  * @param quality the qualitative overall quality flag associated with the observation
  * @param accuracy the quantitative overall quality value associated with the observation
  * @param status
  * @param processing the processing stage the observation is currently found at
  * @param uncertml uncertml object
  * @param comment overall comment regarding the observation
  * @param location location parameters for the spatial sampling profile
  * @param parameters any additional free-form parameters
  */

case class SemanticObservation(procedure: String,
                               feature: String,
                               observableproperty: String,
                               year: Int,
                               month: Int,
                               phenomenontimestart: Long,
                               phenomenontimeend: Long,
                               observationType: String,
                               categoricalObservation: Option[String],
                               numericalObservation: Option[Double],
                               quality: Int,
                               accuracy: Double,
                               status: String,
                               processing: String,
                               uncertml: Option[String],
                               comment: String,
                               location: Option[String],
                               parameters: Option[scala.collection.mutable.Map[String,String]])
  extends BaseSemanticRecord {

    override def toString: String = procedure +
      ',' +
      feature +
      ',' +
      observableproperty +
      ',' +
      year.toString +
      ',' +
      month.toString +
      ',' +
      phenomenontimestart.toString +
      ',' +
      phenomenontimeend.toString +
      ',' +
      observationType +
      ',' +
      categoricalObservation +
      ',' +
      numericalObservation +
      ',' +
      quality.toString +
      ',' +
      accuracy.toString +
      ',' +
      status +
      ',' +
      processing +
      ',' +
      uncertml +
      ',' +
      comment +
      ',' +
      location +
      ',' +
      parameters.toString
}