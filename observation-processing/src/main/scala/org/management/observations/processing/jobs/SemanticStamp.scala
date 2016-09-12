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
import org.management.observations.processing.bolts.transform._
import org.management.observations.processing.tuples.{RawObservation, SemanticObservation}

// System KVP properties and time representations
import java.util.Properties
import org.management.observations.processing.ProjectConfiguration
import scala.collection.JavaConversions._

/**
  * SemanticStamp:
  *
  * - read raw sensor observation tuples from the kafka queue, produce a full
  *    semantic record from the lookup values stored in the registry
  *    keystore.
  *
  * - write the semantic observation record into the database.
  *
  * - pipe the stream of observations to the QCBlockLogic for the default checks
  *   applicable to all sensor observations.
  *
  * - pipe observations that fail to parse into a queue for manual evaluation
  *
  */

object SemanticStamp{

  def main(args: Array[String]) {

    // Create the environment
    val env = StreamExecutionEnvironment.getExecutionEnvironment

    // Read the parameter configuration file
    val params: ParameterTool = ParameterTool.fromMap(mapAsJavaMap(ProjectConfiguration.configMap))

    // Generate the properties for the kafka connections
    val kafkaProp = new Properties()
    kafkaProp.setProperty("bootstrap.servers", params.get("kafka-bootstrap"));

    /**
      * Basic read/write observation stream:
      *
      * Connect to the kafka queue, read the tuples as a single simple string,
      * then use a map function to produce a stream of RawObservation objects
      */
    val rawStream: DataStream[RawObservation] = env
      .addSource(new FlinkKafkaConsumer09[String](
        params.get("kafka-ingest-raw-observations"),
        new SimpleStringSchema(),
        kafkaProp)
      )
      .map(new RawCSVToObservation())

    /**
      * Write the RawObservation objects that failed to parse to failed stream
      * queue, first creating the object serializer for writing to Kafka
      */
    val rawType: TypeInformation[RawObservation] = TypeExtractor.createTypeInfo(classOf[RawObservation])
    val rawTypeSchema = new TypeInformationSerializationSchema[RawObservation](rawType, new ExecutionConfig())

    rawStream
    .filter(_.parseOK == false)
    .addSink(new FlinkKafkaProducer09[RawObservation](
      params.get("kafka-producer"),
      params.get("kafka-produce-malformed-raw-observations"),
      rawTypeSchema)
    )

    /**
      * Convert RawObservations to SemanticObservations, and write to the
      * queue for persistence
      */
    val semanticType: TypeInformation[SemanticObservation] = TypeExtractor.createTypeInfo(classOf[SemanticObservation])
    val semanticTypeSchema = new TypeInformationSerializationSchema[SemanticObservation](semanticType, new ExecutionConfig())

    val semanticStream: DataStream[SemanticObservation] = rawStream
      .filter(_.parseOK == true)
      .filter(_.observationType == "numeric")
      .map(new RawToSemanticObservation())

    semanticStream
      .addSink(new FlinkKafkaProducer09[SemanticObservation](
        params.get("kafka-producer"),
        params.get("kafka-produce-observation-persist"),
        semanticTypeSchema)
      )

    /**
      * Write the RawObservation to the QC Logic job that processes all observation data
      */
    semanticStream
      .addSink(new FlinkKafkaProducer09[SemanticObservation](
        params.get("kafka-producer"),
        params.get("kafka-produce-qc-logic-queue"),
        semanticTypeSchema)
      )

    env.execute("Observation Semantic Stamp")
  }
}