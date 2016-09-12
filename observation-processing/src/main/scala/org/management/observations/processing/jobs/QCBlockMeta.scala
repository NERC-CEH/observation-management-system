package org.management.observations.processing.jobs

// Execution environment

// Used to provide serializer/deserializer information for user defined objects
// to place onto the Kafka queue
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.streaming.connectors.kafka._
import org.apache.flink.streaming.util.serialization._

// Necessary for the serialization of objects to the Kafka queue, without an error is thrown
// regarding implicit type identification (taken from the mailing list).
import org.apache.flink.streaming.api.scala._

// Tuples used within the job
import org.management.observations.processing.tuples.{MetaOutcomeQualitative, MetaDataObservation, MetaOutcomeQuantitative}

// Bolts used within the job
import org.management.observations.processing.bolts.transform.RawCSVToMetaRecord
import org.management.observations.processing.bolts.qc.block.meta._

// System KVP properties and time representations
import java.util.Properties
import org.management.observations.processing.ProjectConfiguration
import scala.collection.JavaConversions._

object QCBlockMeta {

  def main(args: Array[String]) {

    /**
      * Create the environment
      */
    val env = StreamExecutionEnvironment.getExecutionEnvironment

    // Read the parameter configuration file
    val params: ParameterTool = ParameterTool.fromMap(mapAsJavaMap(ProjectConfiguration.configMap))

    /**
      * Generate the properties for the kafka connections, this includes creating the schema
      * val, used to deserialize the SemanticObservationNumeric objects from the Kafka queue
      */
    val kafkaProp = new Properties()
    kafkaProp.setProperty("bootstrap.servers", params.get("kafka-bootstrap"));


    // Connect to the meta-data observation queue, read the CSV strings
    val metaStream: DataStream[String] = env
      .addSource(new FlinkKafkaConsumer09[String](
        params.get("kafka-ingest-meta-observations"),
        new SimpleStringSchema,
        kafkaProp)
      )

    // Parse into MetaData records
    val metaRecordStream: DataStream[MetaDataObservation] = metaStream
      .map(new RawCSVToMetaRecord())

    // Filter into streams that are value or identity based
    val valueBasedStream: DataStream[MetaDataObservation] = metaRecordStream
      .filter(_.parseOK == true)
      .filter(_.value != "NotAValue")

    val identityBasedStream: DataStream[MetaDataObservation] = metaRecordStream
      .filter(_.parseOK == true)
      .filter(_.value == "NotAValue")

    val failedStream: DataStream[MetaDataObservation] = metaRecordStream
      .filter(_.parseOK == false)

    /**
      * Value based records
      *
      * Similar to QCBlockThreshold threshold based checks, except that on fail
      * it is all the related sensors that are marked as having a QC fail
      * not the meta-data stream.
      *
      * Write the stream to persistence once the QC output has been written
      */
    val valueQCOutcomes: DataStream[MetaOutcomeQuantitative] = valueBasedStream
      .flatMap(new QCBlockMetaValueRangeCheck())

    val quantitativeType: TypeInformation[MetaOutcomeQuantitative] = TypeExtractor.createTypeInfo(classOf[MetaOutcomeQuantitative])
    val quantitativeTypeSchema = new TypeInformationSerializationSchema[MetaOutcomeQuantitative](quantitativeType, new ExecutionConfig())

    valueQCOutcomes
      .addSink(new FlinkKafkaProducer09[MetaOutcomeQuantitative](
        params.get("kafka-producer"),
        params.get("kafka-produce-qc-meta-quantitative"),
        quantitativeTypeSchema)
      )

    /**
      * Identity based records
      *
      * Records who's existence trigger a QC fail based purely on the identify
      * of the record, rather than any corresponding values.  Output and persist
      * the QC failure records, followed by persisting the stream.
      */
    val identityQCOutcomes: DataStream[MetaOutcomeQualitative] = identityBasedStream
      .flatMap(new QCBlockMetaIdentityCheck())

    val qualitativeType: TypeInformation[MetaOutcomeQualitative] = TypeExtractor.createTypeInfo(classOf[MetaOutcomeQualitative])
    val qualitativeTypeSchema = new TypeInformationSerializationSchema[MetaOutcomeQualitative](qualitativeType, new ExecutionConfig())

    identityQCOutcomes
      .addSink(new FlinkKafkaProducer09[MetaOutcomeQualitative](
        params.get("kafka-producer"),
        params.get("kafka-produce-qc-meta-qualitative"),
        qualitativeTypeSchema)
      )

    /**
      * Failed to parse records
      */
    val metaType: TypeInformation[MetaDataObservation] = TypeExtractor.createTypeInfo(classOf[MetaDataObservation])
    val metaTypeSchema = new TypeInformationSerializationSchema[MetaDataObservation](metaType, new ExecutionConfig())
    failedStream
      .addSink(new FlinkKafkaProducer09[MetaDataObservation](
        params.get("kafka-producer"),
        params.get("kafka-produce-malformed-meta-observations"),
        metaTypeSchema)
      )

    env.execute("QC MetaData Block")
  }
}
