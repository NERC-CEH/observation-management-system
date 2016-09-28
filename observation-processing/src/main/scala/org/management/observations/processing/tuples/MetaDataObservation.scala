package org.management.observations.processing.tuples

/**
  * Represents MetaData information parsed into the system, which relates
  * to the feature and includes activities such as cleaning, maintenance,
  * or battery levels.
  *
  * @param feature The feature the metadata relates to
  * @param dataType The type of metadata, e.g. battery, cleaning
  * @param startTime The start period of the metadata record
  * @param endTime The end period of the metadata record
  * @param value The value associated with the record, not always present
  *              as some records work by identity only
  * @param parseOK Boolean to indicate that the start/end times and value
  *                corresponded to expected format
  * @param parseMessage Information on the outcome of the observation creation
  *
  *
  */
case class MetaDataObservation(feature: String,
                               dataType: String,
                               startTime: String,
                               endTime: String,
                               value: String,
                               parseOK: Boolean,
                               parseMessage: String){

  override def toString: String = feature +
    ',' +
    dataType +
    ',' +
    startTime +
    ',' +
    endTime +
    ',' +
    value +
    ',' +
    parseOK +
    ',' +
    parseMessage
}
