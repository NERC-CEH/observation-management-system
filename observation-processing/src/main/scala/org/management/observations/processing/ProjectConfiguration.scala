package org.management.observations.processing

/**
  * Created by dciar86 on 12/09/16.
  */
object ProjectConfiguration {

  val configMap = Map[String,String](

    /**
      * Redis Server
      *
      * Redis server connection details, for local and server based use
      * Server: ("redis-conn-ip","192.168.3.5")
      * Local: ("redis-conn-ip","localhost")
      */
//    ("redis-conn-ip","192.168.3.5"),
    ("redis-conn-ip","localhost"),
    ("redis-conn-port","6379"),

    /**
      * Kafka Server
      *
      * Kafka server connection details
      */
    ("kafka-bootstrap","192.168.3.5:9092"),
    ("kafka-producer","192.168.3.5:9092"),

    /**
      * Kafka Queue Mappings
      *
      * The mappings for the kafka data sources and data sinks, where key names
      * give the direction of the dataflow (ingest/produce), and the values they
      * map to may not be unique, e.g. one key refers to the production onto a
      * given queue, and another refers to the reading from the same queue.
      */

    // SemanticStamp Job
    ("kafka-ingest-raw-observations","raw-observations"),
    ("kafka-produce-malformed-raw-observations","raw-observations-malformed"),
    ("kafka-produce-observation-persist","observation-persist"),
    ("kafka-produce-qc-logic-queue","observation-qc-logic"),

    // QCBlockLogic Job
    ("kafka-ingest-qc-logic-queue","observation-qc-logic"),
    ("kafka-produce-qc-quantitative","qc-quantitative-persist"),
    ("kafka-produce-qc-qualitative","qc-qualitative-persist"),
    ("kafka-produce-routing-assignment","observation-routing"),

    // Observation Route Job
    ("kafka-ingest-routing-assignment","observation-routing"),
    ("kafka-produce-qc-threshold","observation-qc-threshold"),
    ("kafka-produce-derived-data","derived-creation"),

    // QCBlockMeta Job
    ("kafka-ingest-meta-observations","meta-observations"),
    ("kafka-produce-qc-meta-quantitative","meta-quantitative-persist"),
    ("kafka-produce-qc-meta-qualitative","meta-qualitative-persist"),
    ("kafka-produce-malformed-meta-observations","meta-observations-malformed"),

    // QCBlockThreshold Job
    ("kafka-ingest-qc-threshold","observation-qc-threshold"),

    // QCBlockEvent Job
    ("kafka-ingest-qc-event-qualitative","qc-qualitative-persist"),
    ("kafka-ingest-qc-event-quantitative","qc-quantitative-persist"),
    ("kafka-produce-event","event-persist"),

    // Derived Data
    ("kafka-ingest-derived","derived-creation"),
    ("kafka-produce-raw-observations","raw-observations"),

    /**
      * Semantic links, referencing the catalogue objects
      *
      * The following values refer to the semantic URI catalog/vocabulary
      * server, where metadata about these entries can be found.  The URI's
      * are used to identify whether QC outcomes have passed or failed, or the
      * QC check or event that has taken place.
      */

    // QC check outcome qualifiers
    ("qc-outcome-pass","http://placeholder.catalogue.ceh.ac.uk/pass"),
    ("qc-outcome-fail","http://placeholder.catalogue.ceh.ac.uk/fail"),

    // QC checks associated with QCBlockLogic and QCBlockMeta jobs
    ("qc-logic-null","http://placeholder.catalogue.ceh.ac.uk/qc/null/value"),
    ("qc-logic-null","http://placeholder.catalogue.ceh.ac.uk/qc/null/value"),
    ("qc-logic-order","http://placeholder.catalogue.ceh.ac.uk/qc/timing/order"),
    ("qc-logic-spacing","http://placeholder.catalogue.ceh.ac.uk/qc/timing/intendedspacing"),
    ("qc-logic-meta-value-range-prefix","http://placeholder.catalogue.ceh.ac.uk/qc/meta/value/range/"),
    ("qc-logic-meta-identity-prefix","http://placeholder.catalogue.ceh.ac.uk/qc/meta/identity/"),

    // QC checks associated with QCBlockThreshold jobs
    ("qc-threshold-delta-spike-prefix","http://placeholder.catalogue.ceh.ac.uk/qc/delta/spike/"),
    ("qc-threshold-delta-step-prefix","http://placeholder.catalogue.ceh.ac.uk/qc/delta/step/"),
    ("qc-threshold-sigma-prefix","http://placeholder.catalogue.ceh.ac.uk/qc/sigma/"),
    ("qc-threshold-range-prefix","http://placeholder.catalogue.ceh.ac.uk/qc/range/"),

    // QC events asociated with QCBlockEvent
    ("qc-event-null-aggregate-prefix","http://placeholder.catalogue.ceh.ac.uk/event/null/aggregate/"),
    ("qc-event-null-consecutive","http://placeholder.catalogue.ceh.ac.uk/event/null/consecutive"),
    ("qc-event-order","http://placeholder.catalogue.ceh.ac.uk/event/timing/order"),
    ("qc-event-spacing","http://placeholder.catalogue.ceh.ac.uk/event/timing/spacing"),


    /**
      * Observation Route
      *
      * Once observations have been loaded from their CSV representation in the
      * SemanticStamp job, and have been processed by the QCBlockLogic job,
      * the further routing through the system is dependent on the types of check
      * and processing (model input, multivariate calculation etc.) a particular
      * observation may be used with.
      *
      * The lookups below are used by the ObservationRoute Job, to decide which observations
      * to send to particular Jobs.  For example, "routing-qc-block-threshold" is used to
      * identify the URI (at this state just a text value) that identifies the QCBlockThreshold Job
      * within the catalog/vocabulary, and to use this value to direct observations marked
      * with this URI in the registry to that kafka ingestion queue
      *
      * Registry job value = threshold, refers to the QC Block Threshold
      * Registry model value = sigma, refers to the sigma model within named job
      */
    ("routing-qc-block-threshold","threshold"),
    ("routing-qc-block-threshold-delta","http://placeholder.catalogue.ceh.ac.uk/qc/delta"),
    ("routing-qc-block-threshold-sigma","http://placeholder.catalogue.ceh.ac.uk/qc/sigma"),
    ("routing-qc-block-threshold-range","http://placeholder.catalogue.ceh.ac.uk/qc/range"),

    ("routing-derived","derived"),
    ("routing-derived-lake-analyzer-high-res","http://placeholder.catalogue.ceh.ac.uk/derived/lake-analyzer")



  )
}
