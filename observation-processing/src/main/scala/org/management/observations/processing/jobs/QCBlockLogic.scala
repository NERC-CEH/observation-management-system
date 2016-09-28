package org.management.observations.processing.jobs

// Execution environment
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.java.utils.ParameterTool

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
import org.management.observations.processing.ProjectConfiguration
import scala.collection.JavaConversions._

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
  *
  * This block can be used with any observation type, as it is the metadata
  * associated with the observation rather than the observation itself that
  * is checked.
  */

object QCBlockLogic extends SemanticObservationFlow{

  def main(args: Array[String]) {

    /**
      * Create the environment
      */
    val env = StreamExecutionEnvironment.getExecutionEnvironment

    // Read the parameter configuration file
    val params: ParameterTool = ParameterTool.fromMap(mapAsJavaMap(ProjectConfiguration.configMap))

    // Generate the properties for the kafka connections
    val kafkaProp = new Properties()
    kafkaProp.setProperty("bootstrap.servers", params.get("kafka-bootstrap"));

    /**
      * Generate the deserializer information to read SemanticObservation objects
      * from the Kafka queue
      */
    val semanticType: TypeInformation[SemanticObservation] = TypeExtractor.createTypeInfo(classOf[SemanticObservation])
    val semanticTypeSchema = new TypeInformationSerializationSchema[SemanticObservation](semanticType, new ExecutionConfig())


    // Connect to the observation queue, read the SemanticObservationNumeric objects into observationStream
    val observationStream: DataStream[SemanticObservation] = env
      .addSource(new FlinkKafkaConsumer09[SemanticObservation](
        params.get("kafka-ingest-qc-logic-queue"),
        semanticTypeSchema,
        kafkaProp)
      )

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
      .map(_.toString)
      .addSink(new FlinkKafkaProducer09[String](
        params.get("kafka-producer"),
        params.get("kafka-produce-qc-qualitative"),
        new SimpleStringSchema)
      )

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
      .map(_.toString)
      .addSink(new FlinkKafkaProducer09[String](
        params.get("kafka-producer"),
        params.get("kafka-produce-qc-quantitative"),
        new SimpleStringSchema)
      )

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
      .map(_.toString)
      .addSink(new FlinkKafkaProducer09[String](
        params.get("kafka-producer"),
        params.get("kafka-produce-qc-qualitative"),
        new SimpleStringSchema)
      )

    valueMetaStream
      .map(_.toString)
      .addSink(new FlinkKafkaProducer09[String](
        params.get("kafka-producer"),
        params.get("kafka-produce-qc-quantitative"),
        new SimpleStringSchema)
      )

    /**
      * Send all the observations to the ObservationRoute job, which provides
      * the metadata necessary for each observation to have the correct QC
      * checks applied, and to be included in the calculation of derived data.
      */
    observationStream
      .filter(_.numericalObservation.isDefined)
      .addSink(new FlinkKafkaProducer09[SemanticObservation](
        params.get("kafka-producer"),
        params.get("kafka-produce-routing-assignment"),
        semanticTypeSchema)
      )

    env.execute("QC Block Logic")
  }
}
