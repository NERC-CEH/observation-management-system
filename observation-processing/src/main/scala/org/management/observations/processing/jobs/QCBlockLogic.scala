package org.management.observations.processing.jobs

// Execution environment
import org.apache.flink.api.common.ExecutionConfig


// Used to provide serializer/deserializer information for user defined objects
// to place onto the Kafka queue
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.apache.flink.streaming.util.serialization._
import org.apache.flink.api.common.typeinfo.TypeInformation

// Necessary for the serialization of objects to the Kafka queue, without an error is thrown
// regarding implicit type identification (taken from the mailing list).
import org.apache.flink.streaming.api.scala._

// Kafka connection for source and sink
import org.apache.flink.streaming.connectors.kafka._

// The tuple types used within this job, and the bolts used to convert from the
// raw CSV format to a RawObservation, and from RawObservation to SemanticObservationNumeric
import org.management.observations.processing.tuples.{QCOutcomeQualitative, QCOutcomeQuantitative, SemanticObservationFlow, SemanticObservation}

// Temporal QC bolt
import org.management.observations.processing.bolts.qc.block.logic._

// System KVP properties and time representations
import java.util.Properties

/**
  * QCBlockLogic
  *
  * - perform a number of logic based QC checks on the initial datastream,
  *   including:
  *
  *   - Null observation check, do the observations have a corresponding
  *     numeric value.
  *
  *   - Time attribute check, do the observations arrive in the correct
  *     order and is the intended observation spacing exceeded.
  *
  * - generate default 'pass' outcomes for metadata based checks
  */

object QCBlockLogic extends SemanticObservationFlow{

  def main(args: Array[String]) {

    /**
      * Create the environment
      */
    val env = StreamExecutionEnvironment.getExecutionEnvironment

    /**
      * Generate the properties for the kafka connections, this includes creating the schema
      * val, used to deserialize the SemanticObservationNumeric objects from the Kafka queue
      */
    val kafkaProp = new Properties()
    kafkaProp.setProperty("bootstrap.servers", "localhost:9092");
    kafkaProp.setProperty("zookeeper.connect", "localhost:2181");
    kafkaProp.setProperty("group.id", "QCBlockLogic");

    val semanticType: TypeInformation[SemanticObservation] = TypeExtractor.createTypeInfo(classOf[SemanticObservation])
    val semanticTypeSchema = new TypeInformationSerializationSchema[SemanticObservation](semanticType, new ExecutionConfig())


    // Connect to the observation queue, read the SemanticObservationNumeric objects into observationStream
    val observationStream: DataStream[SemanticObservation] = env
      .addSource(new FlinkKafkaConsumer09[SemanticObservation]("observation-qc-logic", semanticTypeSchema, kafkaProp))

    /**
      * Null QC Check
      *
      * Any observation found in the stream may or may not be identified as having
      * a null observed value.  For each observation a QCOutcomeQualitative object
      * is created to record the observations pass or failure depending on whether
      * the value is defined.
      */
    val qualitativeType: TypeInformation[QCOutcomeQualitative]= TypeExtractor.createTypeInfo(classOf[QCOutcomeQualitative])
    val qualitativeTypeSchema = new TypeInformationSerializationSchema[QCOutcomeQualitative](qualitativeType, new ExecutionConfig())

    val nullQC: DataStream[QCOutcomeQualitative] = observationStream
      .map(new QCBlockLogicNull())

    nullQC
      .addSink(new FlinkKafkaProducer09[QCOutcomeQualitative]("localhost:9092", "qualitative-persist", qualitativeTypeSchema))

    /**
      * Time Order + Spacing event stream:
      *
      * Perform a check that the observations are arriving in ascending order,
      * and check that the difference in observation time is within the
      * intended observation spacing threshold.  Persist the QC outcome values,
      * and create events should any of the checks fail
      */
    val timeQC: DataStream[QCOutcomeQuantitative] = observationStream
      .keyBy("feature","procedure","observableproperty")
      .countWindow(1,1)
      .apply(new QCBlockLogicTimeseries())

    val quantitativeType: TypeInformation[QCOutcomeQuantitative] = TypeExtractor.createTypeInfo(classOf[QCOutcomeQuantitative])
    val quantitativeTypeSchema = new TypeInformationSerializationSchema[QCOutcomeQuantitative](quantitativeType, new ExecutionConfig())

    timeQC
      .addSink(new FlinkKafkaProducer09[QCOutcomeQuantitative]("localhost:9092", "quantitative-persist", quantitativeTypeSchema))

    /**
      * Default metadata based outcomes:
      *
      * MetaData regarding the site is usually slow velocity and
      * lagging by hours, or months, depending on the type.  When
      * an observation is generated, it passes all existing checks
      * by default, and only when metadata observations are added
      * do these get modified to fail where necessary (in QCBlockMeta).
      *
      */
    val identityMetaStream: DataStream[QCOutcomeQualitative] = observationStream
      .flatMap(new QCBlockLogicDefaultMetaIdentity())

    val valueMetaStream: DataStream[QCOutcomeQuantitative] = observationStream
      .flatMap(new QCBlockLogicDefaultMetaValue())

    identityMetaStream
      .addSink(new FlinkKafkaProducer09[QCOutcomeQualitative]("localhost:9092", "qualitative-persist", qualitativeTypeSchema))

    valueMetaStream
      .addSink(new FlinkKafkaProducer09[QCOutcomeQuantitative]("localhost:9092", "quantitative-persist", quantitativeTypeSchema))

    /**
      * Send all the observations that have a value to QC Block Threshold,
      * all null valued observations are dropped at this stage.
      *
      * Out of order observations are dropped silently in further processing
      * blocks where the timestamp is used, by the ascendingTimestamp function.
      */
    observationStream
      .filter(_.numericalObservation.isDefined)
      .addSink(new FlinkKafkaProducer09[SemanticObservation]("localhost:9092", "observation-qc-threshold", semanticTypeSchema))

    env.execute("QC Block Logic")
  }
}
