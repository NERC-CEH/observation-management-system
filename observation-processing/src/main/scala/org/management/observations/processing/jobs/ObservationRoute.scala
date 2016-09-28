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
import org.management.observations.processing.tuples.{SemanticObservation,RoutedObservation}
import org.management.observations.processing.bolts.routing.InjectRoutingInfo

// System KVP properties and time representations
import java.util.Properties
import org.management.observations.processing.ProjectConfiguration
import scala.collection.JavaConversions._

/**
  * ObservationRoute:
  *
  * - There are a number of different QC checks and derived data products
  *     to be used or created.  Not all observations have the same checks
  *     applied, nor are all observations used to create certain derived
  *     data.  This job uses a flatMap bolt to look up the registry and
  *     create observations with routing information to ensure they go to
  *     the correct place.
  *
  */

object ObservationRoute {


  def main(args: Array[String]) {

    // Create the environment
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


    /**
      * Connect to the observation queue, read the SemanticObservation
      * objects into observationStream
      */
    //
    val observationStream: DataStream[SemanticObservation] = env
      .addSource(new FlinkKafkaConsumer09[SemanticObservation](
        params.get("kafka-ingest-routing-assignment"),
        semanticTypeSchema,
        kafkaProp)
      )

    /**
      * Call the routing bolt to create the routing objects
      */
    val routingObservations: DataStream[RoutedObservation] = observationStream
      .flatMap(new InjectRoutingInfo)

    /**
      * Using the routing information the following filters use only the job
      * information - the model and key information is used within each job.
      *
      * The observations are sent to the queues for their respective job.
      */

    // Create kafka serializer
    val routeType: TypeInformation[RoutedObservation] = TypeExtractor.createTypeInfo(classOf[RoutedObservation])
    val routeSchema = new TypeInformationSerializationSchema[RoutedObservation](routeType, new ExecutionConfig())

    // QC Threshold Job routing
    routingObservations
      .filter(_.routes.map(_.job).contains(params.get("routing-qc-block-threshold")))
      .addSink(new FlinkKafkaProducer09[RoutedObservation](
        params.get("kafka-producer"),
        params.get("kafka-produce-qc-threshold"),
        routeSchema)
      )

    // Derived Data Job routing
    routingObservations
      .filter(_.routes.map(_.job).contains(params.get("routing-derived")))
      .addSink(new FlinkKafkaProducer09[RoutedObservation](
        params.get("kafka-producer"),
        params.get("kafka-produce-derived-data"),
        routeSchema)
      )

    env.execute("Observation Route")
  }

}
