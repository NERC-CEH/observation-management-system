package org.management.observations.processing.tuples

/**
  *
  * @param job the Flink job that the model needs to be routed to, this identifies
  *            the Kafka queue(s) the observation should be placed on.
  * @param model the model within a particular job the observation should be used with
  * @param key the key to group observations within the job-model combination
  */
case class RoutedObservationDetail(job: String, model: String, key: String)
