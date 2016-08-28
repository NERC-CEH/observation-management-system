package org.management.observations.processing.tuples

/**
  * Create the basic representation of a RAW observation tuple,
  * which corresponds to the RAW string, and the outcomes of
  * coherence checks on whether it corresponds to the expected format
  *
  * @param observation the RAW observation as it was placed onto the queue
  * @param parseOK did the observation pass all checks
  * @param parseMessage comment accompanying the parseOK parameter
  */
case class RawObservation(observation: String,
                          observationType: String,
                          parseOK: Boolean,
                          parseMessage: String) {

  // Override toString to provide the CSV representation of the object
  override def toString: String = observation+"(end of csv tuple):"+observationType+":"+parseOK+":"+parseMessage
}