package org.management.observations.processing.tuples


case class BasicObservation(feature: String,
                            procedure: String,
                            observableproperty: String,
                            routingkey: String,
                            phenomenontimestart: Long,
                            phenomenontimeend: Long,
                            value: Double) extends BaseSemanticRecord {

}
