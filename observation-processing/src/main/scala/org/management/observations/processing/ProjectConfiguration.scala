package org.management.observations.processing

/**
  * Created by dciar86 on 12/09/16.
  */
object ProjectConfiguration {

  val configMap = Map[String,String](
    // Redis Server
    ("redis-conn-ip","192.168.0.32"),
    ("redis-conn-port","6379"),

    // Kafka Server
    ("kafka-bootstrap","localhost:9092"),
    ("kafka-producer","localhost:9092"),

    // Kafka Queue Mappings
    // SemanticStamp
    ("kafka-ingest-raw-observations","raw-observations"),
    ("kafka-produce-malformed-raw-observations","raw-observations-malformed"),
    ("kafka-produce-observation-persist","observation-persist"),
    ("kafka-produce-qc-logic-queue","observation-qc-logic"),
    // QCBlockLogic
    ("kafka-ingest-qc-logic-queue","observation-qc-logic"),
    ("kafka-produce-qc-quantitative","qc-quantitative-persist"),
    ("kafka-produce-qc-qualitative","qc-qualitative-persist"),
    ("kafka-produce-qc-threshold","observation-qc-threshold"),
    // QCBlockMeta
    ("kafka-ingest-meta-observations","meta-observations"),
    ("kafka-produce-qc-meta-quantitative","meta-quantitative-persist"),
    ("kafka-produce-qc-meta-qualitative","meta-qualitative-persist"),
    ("kafka-produce-malformed-meta-observations","meta-observations-malformed"),
    // QCBlockThreshold
    ("kafka-ingest-qc-threshold","observation-qc-threshold")




  )
}
