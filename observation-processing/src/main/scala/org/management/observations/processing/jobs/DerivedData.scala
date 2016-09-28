package org.management.observations.processing.jobs

// Execution environment and parameter tool
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.java.utils.ParameterTool
import org.management.observations.processing.bolts.derived.LakeAnalyzer
import org.management.observations.processing.tuples.BasicNumericObservation

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

// System KVP properties and time representations
import java.util.Properties
import org.management.observations.processing.ProjectConfiguration
import scala.collection.JavaConversions._

// Window and time libraries
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows
import org.management.observations.processing.timing.LakeAnalyzerEventTime

// Tuples used
import org.management.observations.processing.tuples.RoutedObservation

object DerivedData {


  def main(args: Array[String]): Unit = {

    // Create the environment, with EventTime
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)

    // Read the parameter configuration file
    val params: ParameterTool = ParameterTool.fromMap(mapAsJavaMap(ProjectConfiguration.configMap))

    /**
      * Generate the properties for the kafka connections, this includes creating the schema
      * val, used to deserialize the SemanticObservationNumeric objects from the Kafka queue
      */
    val kafkaProp = new Properties()
    kafkaProp.setProperty("bootstrap.servers", params.get("kafka-bootstrap"));

    val observationInfo: TypeInformation[RoutedObservation] = TypeExtractor.createTypeInfo(classOf[RoutedObservation])
    val observationSchema = new TypeInformationSerializationSchema[RoutedObservation](observationInfo, new ExecutionConfig())

    // Read routed observations into the stream
    val observationStream: DataStream[RoutedObservation] = env
      .addSource(new FlinkKafkaConsumer09[RoutedObservation](
        params.get("kafka-ingest-derived"),
        observationSchema,
        kafkaProp)
      )

    /**
      * Lake Analyzer Processing
      *
      * A tumbling window is used to group observations, with a watermark
      * added four hours after event-time to allow for potentially out of
      * order data aggregated from different sensors.  Tumbling used as
      * different depth observations should be taken at exactly the same
      * time instant.
      *
      * A transformation from the RoutedObservation to a smaller tuple is
      * carried out to minimise the size of the window.
      */

    val laHighTupleStream: DataStream[BasicNumericObservation] = observationStream
      .filter(_.routes.map(_.model).contains(params.get("routing-derived-lake-analyzer-high-res")))
      .map(x => x.observation)
      .assignTimestampsAndWatermarks(new LakeAnalyzerEventTime)


    /**
      * Feed the observations routed to the Lake Analyzer High Resolution
      * model into the Lake Analyzer bolt, grouping by feature as there is
      * one PRT chain to each feature.
      *
      * Write the observations to the Kafka RAW observation stream
      */
    val laDerivedStream: DataStream[String] = laHighTupleStream
      .keyBy("feature")
      .window(TumblingEventTimeWindows.of(Time.hours(2)))
      .apply(new LakeAnalyzer())

    laDerivedStream
      .addSink(new FlinkKafkaProducer09[String](
        params.get("kafka-producer"),
        params.get("kafka-produce-raw-observations"),
        new SimpleStringSchema))
  }
}
