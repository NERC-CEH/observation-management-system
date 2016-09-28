package org.management.observations.processing.tuples

/**
  * This observation groups an observation with the metadata used to route the
  * observation to the Flink Job's and their subsequent models, annd includes the
  * key used to group observations within the model.
  *
  * @param observation the semantic observation to be routed to different jobs/models
  * @param routes the array of job/model/routing key values specifying where the observation
  *               must go.
  */
case class RoutedObservation (observation: BasicNumericObservation,
                              routes: Array[RoutedObservationDetail]){}
