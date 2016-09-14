package org.management.observations.processing

/**
  * Created by dciar86 on 12/09/16.
  */
object ProjectConfiguration {

  val configMap = Map[String,String](
    /**
      * Redis Server
      */
    ("redis-conn-ip","192.168.3.5"),
    ("redis-conn-port","6379"),

    /**
      * Kafka Server
      */
    ("kafka-bootstrap","192.168.3.5:9092"),
    ("kafka-producer","192.168.3.5:9092"),

    /**
      * Kafka Queue Mappings
      */

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
    ("kafka-ingest-qc-threshold","observation-qc-threshold"),

    // ScalaPersistToDatabase
    ("kafka-ingest-semantic-observations","observation-persist"),

    // QC Qualifiers and outcome types
    ("qc-outcome-pass","http://placeholder.catalogue.ceh.ac.uk/pass"),
    ("qc-outcome-fail","http://placeholder.catalogue.ceh.ac.uk/fail"),

    // QC Logic + Meta
    ("qc-logic-null","http://placeholder.catalogue.ceh.ac.uk/qc/null/value"),
    ("qc-logic-null","http://placeholder.catalogue.ceh.ac.uk/qc/null/value"),
    ("qc-logic-order","http://placeholder.catalogue.ceh.ac.uk/qc/timing/order"),
    ("qc-logic-spacing","http://placeholder.catalogue.ceh.ac.uk/qc/timing/intendedspacing"),
    ("qc-logic-meta-value-range-prefix","http://placeholder.catalogue.ceh.ac.uk/qc/meta/value/range/"),
    ("qc-logic-meta-identity-prefix","http://placeholder.catalogue.ceh.ac.uk/qc/meta/identity/"),

    // QC Threshold
    ("qc-threshold-delta-spike-prefix","http://placeholder.catalogue.ceh.ac.uk/qc/delta/spike/"),
    ("qc-threshold-delta-step-prefix","http://placeholder.catalogue.ceh.ac.uk/qc/delta/step/"),
    ("qc-threshold-sigma-prefix","http://placeholder.catalogue.ceh.ac.uk/qc/sigma/"),
    ("qc-threshold-range-prefix","http://placeholder.catalogue.ceh.ac.uk/qc/range/")


  )
}
