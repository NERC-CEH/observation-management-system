package org.management.observations.processing.jobs

// Execution environment
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.java.utils.ParameterTool
import org.management.observations.processing.tuples.{BasicNumericObservation, RoutedObservation}

// Used to provide serializer/deserializer information for user defined objects
// to place onto the Kafka queue
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.apache.flink.streaming.util.serialization._
import org.apache.flink.api.common.typeinfo.TypeInformation

// Necessary for the serialization of objects to the Kafka queue, without an error is thrown
// regarding implicit type identification (taken from the mailing list).
import org.apache.flink.streaming.api.scala._

// Window and time libraries
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.windowing.time.Time

// Kafka connection for source and sink
import org.apache.flink.streaming.connectors.kafka._

// The tuple types used within this job
import org.management.observations.processing.tuples.QCOutcomeQuantitative
import org.management.observations.processing.tuples.SemanticObservation

// The range, sigma, and delta check bolts
import org.management.observations.processing.bolts.qc.block.threshold._

// System KVP properties and time representations
import java.util.Properties
import org.management.observations.processing.ProjectConfiguration
import scala.collection.JavaConversions._

/**
  * QCBlockThreshold:
  *
  * - perform the most basic of QC checks on a datastream, applicable to all
  *   observation streams where a physical sensor is used, these include:
  *
  *   - Range check: is the observation within min/max bounds
  *   - Sigma check: is the variance over time within bounds
  *   - Delta check: is the change between two data points within bounds
  *
  *   For each of the checks above there can be zero to many different
  *   sets of bounds, each of which corresponds to a different qualifier
  *   within the WML2.0 terminology.
  *
  *   With all the above threshold checks, the values used can be based
  *   on different approaches, for example a range could be based on:
  *
  *   - Physical sensing capabilities of the hardware
  *   - Extreme event values that would only be exceeded in extreme circumstance,
  *     whether based on subjective information, historical values etc
  *   - Seasonal, diurnal, or biological cycle based ranges
  *   - Forecast predicted values
  *
  *    As there can be many sources of threshold value, each of the checks
  *    below have a flatMap relationship to the observation datastream, meaning
  *    that for every observation past into the flatMap or window function,
  *    zero or more QCOutcomeQuantitative objects are returned.  This is to
  *    allow all sensor observations to be past in, but only those that have
  *    corresponding entries in the registry to have QC output, and it also
  *    allows for multiple outputs on the same type of test.  In example:
  *
  *    Sensor A may have no range check thresholds specified, and so emits
  *    no QC output, while Sensor B has three different range check thresholds,
  *    and so emits six (3*min/max pair) QC outputs.
  *
  *   For all the windowed apply methods there are two options for aligning
  *   windows with the reference thresholds in the registry, these being:
  *
  *     - extract the temporal point (hour, day, month) in the data stream
  *       and use this as an extra KeyBy field
  *     - find the middle position within the window and find the closest
  *       reference point to this middle position
  *
  *       Both of the above have their downsides, with the latter's being that
  *       we may reach a situation where window median values end up in the near-middle
  *       between two reference points, potentially effecting output accuracy.  However
  *       it does mean that the data stream is kept simple, and the work is
  *       performed within the bolt, which can use internal and external (from
  *       the register) logic to choose the best action.
  */

object QCBlockThreshold {

  def main(args: Array[String]) {

    // Create the environment, with EventTime as used by the Sigma check
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

    // Read semantic observations into the stream
    val observationStream: DataStream[RoutedObservation] = env
      .addSource(new FlinkKafkaConsumer09[RoutedObservation](
        params.get("kafka-ingest-qc-threshold"),
        observationSchema,
        kafkaProp)
      )

    /**
      * Observation range thresholds:
      *
      * The range thresholds are applied to each single observation,
      * but may produce zero to many QC outcomes
      */
    val rangeStream: DataStream[QCOutcomeQuantitative] = observationStream
      .filter(_.routes.map(_.model).contains(params.get("routing-qc-block-threshold-range")))
      .map(_.observation)
      .flatMap(new QCBlockThresholdRangeCheck())

    val quantitativeType: TypeInformation[QCOutcomeQuantitative] = TypeExtractor.createTypeInfo(classOf[QCOutcomeQuantitative])
    val quantitativeTypeSchema = new TypeInformationSerializationSchema[QCOutcomeQuantitative](quantitativeType, new ExecutionConfig())

    rangeStream
      .map(_.toString)
      .addSink(new FlinkKafkaProducer09[String](
        params.get("kafka-producer"),
        params.get("kafka-produce-qc-quantitative"),
        new SimpleStringSchema)
      )

    /**
      * Windowed observation sigma (variance) range thresholds:
      *
      * The range thresholds are applied to groups of observations,
      * but may produce zero to many QC outcomes
      */
    // TODO: move to iterative fold then apply technique

    val sigmaObservationTimeStream: DataStream[BasicNumericObservation] = observationStream
      .filter(_.routes.map(_.model).contains(params.get("routing-qc-block-threshold-sigma")))
      .map(_.observation)
      .assignAscendingTimestamps(_.phenomenontimestart)

    val sigmaConcurrentStream1h: DataStream[QCOutcomeQuantitative] = sigmaObservationTimeStream
        .keyBy("feature","procedure","observableproperty")
        .timeWindow(Time.hours(1))
        .apply(new QCBlockThresholdSigmaCheck())

    val sigmaConcurrentStream12h: DataStream[QCOutcomeQuantitative] = sigmaObservationTimeStream
      .keyBy("feature","procedure","observableproperty")
      .timeWindow(Time.hours(12))
      .apply(new QCBlockThresholdSigmaCheck())

    val sigmaConcurrentStream24h: DataStream[QCOutcomeQuantitative] = sigmaObservationTimeStream
      .keyBy("feature","procedure","observableproperty")
      .timeWindow(Time.hours(24))
      .apply(new QCBlockThresholdSigmaCheck())

    val sigmaStream = sigmaConcurrentStream1h
      .union(sigmaConcurrentStream12h, sigmaConcurrentStream24h)

    sigmaStream
      .map(_.toString)
      .addSink(new FlinkKafkaProducer09[String](
        params.get("kafka-producer"),
        params.get("kafka-produce-qc-quantitative"),
        new SimpleStringSchema)
      )

    /**
      * Windowed observation delta (rate of change) thresholds:
      *
      * The delta between two and three consecutive observations are
      * compared to rate of change thresholds to look for unrealistic
      * change.
      */

    val deltaObservationTimeStream: DataStream[BasicNumericObservation] = observationStream
      .filter(_.routes.map(_.model).contains(params.get("routing-qc-block-threshold-delta")))
      .map(_.observation)
      .assignAscendingTimestamps(_.phenomenontimestart)

    val deltaStep: DataStream[QCOutcomeQuantitative] = deltaObservationTimeStream
      .keyBy("feature","procedure","observableproperty")
      .countWindow(2,1)
      .apply(new QCBlockThresholdDeltaStepCheck())

    val deltaSpike: DataStream[QCOutcomeQuantitative] = deltaObservationTimeStream
      .keyBy("feature","procedure","observableproperty")
      .countWindow(3,1)
      .apply(new QCBlockThresholdDeltaSpikeCheck())

    val deltaStream = deltaSpike.union(deltaStep)

    deltaStream
      .map(_.toString)
      .addSink(new FlinkKafkaProducer09[String](
        params.get("kafka-producer"),
        params.get("kafka-produce-qc-quantitative"),
        new SimpleStringSchema)
      )

    env.execute("QC Block Threshold")
  }
}
