package org.management.observations.processing.tuples


case class RoutedObservation (observation: SemanticObservation,
                              routes: Array[RoutedObservationDetail]){}
