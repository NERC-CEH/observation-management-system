package org.management.observations.processing.jobs

// Execution environment
import org.apache.flink.streaming.api.scala._
import org.apache.flink.api.java.utils.ParameterTool

// Window libraries
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.windowing.time.Time

// Used to provide serializer/deserializer information for user defined objects
// in the Kafka queue
import org.apache.flink.streaming.util.serialization._

// Kafka connection for source and sink
import org.apache.flink.streaming.connectors.kafka._

// The tuple types used within this job
import org.management.observations.processing.tuples.{QCEvent, QCOutcomeQualitative, QCOutcomeQuantitative, SemanticObservationFlow}

// The bolts used within this job
import org.management.observations.processing.bolts.qc.block.event.{QCBlockEventNullAggregateCheck, QCBlockEventNullConsecutiveCheck}

// System KVP properties and time representations
import java.util.Properties
import org.management.observations.processing.ProjectConfiguration
import scala.collection.JavaConversions._


/**
  * Created by dciar86 on 10/08/16.
  */
object QCBlockEvent extends SemanticObservationFlow{

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

 // Read in the qualitative QC outcomes
  val qualitativeQCOutcomes: DataStream[QCOutcomeQualitative] = env
    .addSource(new FlinkKafkaConsumer09[String](
      params.get("kafka-ingest-qc-event-qualitative"),
     new SimpleStringSchema,
      kafkaProp)
    )
   .map(x => createQCOutcomeQualitative(x))

 // Read in the quantitative QC outcomes
 val quantitativeQCOutcomes: DataStream[QCOutcomeQuantitative] = env
   .addSource(new FlinkKafkaConsumer09[String](
    params.get("kafka-ingest-qc-event-quantitative"),
    new SimpleStringSchema,
    kafkaProp)
   )
   .map(x => createQCOutcomeQuantitative(x))

 /**
   * Null Observations
   *
   * The following section deals with null observations within the datastream.
   *
   * Both the consecutive null and windowed null checks require
   * observations arranged by time.  The following creates
   * a stream with timestamps that also act as watermarks, requiring
   * a monotonic stream of data, silently dropping observations
   * that do not comply.
   */

 val nullStreamTimed = qualitativeQCOutcomes
   .filter(_.qualifier == params.get("qc-logic-null"))
   .filter(_.qualitative == params.get("qc-outcome-fail"))
   .assignAscendingTimestamps(_.phenomenontimestart)

 /**
    * Consecutive null values
    *
    * There is a threshold of the number of contiguous nulls that can be
    * accepted, the window below keeps track of the current
    * number of consecutive nulls, and generates an event when the threshold
    * is matched or exceeded.  It will not generate another event until the
    * ongoing consecutive null stream ends and a new one reaches threshold.
    */

 val consecutiveNullEvents = nullStreamTimed
    .keyBy("feature","procedure","observableproperty")
    .countWindow(1,1)
    .apply(new QCBlockEventNullConsecutiveCheck)
    .map(_.toString)
    .addSink(
      new FlinkKafkaProducer09[String](
        params.get("kafka-producer"),
        params.get("kafka-produce-event"),
        new SimpleStringSchema
      )
    )

 /**
    * Temporal windowed null values
    *
    * The number of null observations that occur over differing temporal periods
    * is of interest, as this can be indicative of an issue with a sensor.
    * Such events are not recorded as QC entries against any particular observation,
    * but are instead registered as a QC event.
    *
    * Nulls per hour, per twelve hours, and per twenty four hours are the constraints.
    *
    * The slide moves half the duration of the window to stop too many events being
    * triggered during periods with multiple null values.
    */

 val nullQCEvents1h = nullStreamTimed
    .keyBy("feature","procedure","observableproperty")
    .window(SlidingEventTimeWindows.of(Time.hours(1), Time.minutes(30)))
    .apply(new QCBlockEventNullAggregateCheck())
    .map(_.toString)
    .addSink(new FlinkKafkaProducer09[String](
      params.get("kafka-producer"),
      params.get("kafka-produce-event"),
      new SimpleStringSchema)
    )

 val nullQCEvents12h = nullStreamTimed
    .keyBy("feature","procedure","observableproperty")
    .window(SlidingEventTimeWindows.of(Time.hours(12), Time.hours(6)))
    .apply(new QCBlockEventNullAggregateCheck())
    .map(_.toString)
    .addSink(new FlinkKafkaProducer09[String](
      params.get("kafka-producer"),
      params.get("kafka-produce-event"),
      new SimpleStringSchema)
    )

 val nullQCEvents24h = nullStreamTimed
    .keyBy("feature","procedure","observableproperty")
    .window(SlidingEventTimeWindows.of(Time.hours(24), Time.hours(12)))
    .apply(new QCBlockEventNullAggregateCheck())
    .map(_.toString)
    .addSink(new FlinkKafkaProducer09[String](
      params.get("kafka-producer"),
      params.get("kafka-produce-event"),
      new SimpleStringSchema)
    )


  /**
    * Out of temporal order events
    *
    * When an observation arrives after another that had a higher timestamp
    * an out of order QC check fails, here we generate an event when these
    * failed events are witnessed.  Once testing complete, this will be
    * revisted to identify whether a windowed, or event to timeframe ratio
    * is a better way to generate these.
    *
    **/


  val outOfOrderEvents = quantitativeQCOutcomes
      .filter(_.qualifier == params.get("qc-logic-order"))
      .filter(_.qualitative == params.get("qc-outcome-fail"))
      .map(x =>
        QCEvent(
          x.feature,
          x.procedure,
          x.observableproperty,
          params.get("qc-event-order"),
          x.phenomenontimestart,
          x.phenomenontimestart)
      )
      .map(_.toString)
      .addSink(new FlinkKafkaProducer09[String]("localhost:9092", "event_persist", new SimpleStringSchema))


  /**
    * Observation spacing exceeded
    *
    * If an observation has exceeded the expected time between observations, we
    * generate an event.  Similar to the out of temporal order events, it may be
    * better to use a time/event ratio rather than eventing on every single qc fail
    */

  val longSpacingEvents = quantitativeQCOutcomes
    .filter(_.qualifier == params.get("qc-logic-spacing"))
    .filter(_.qualitative == params.get("qc-outcome-fail"))
    .map(x =>
      QCEvent(
        x.feature,
        x.procedure,
        x.observableproperty,
        params.get("qc-event-spacing"),
        x.phenomenontimestart,
        x.phenomenontimestart)
    )
    .map(_.toString)
    .addSink(new FlinkKafkaProducer09[String]("localhost:9092", "event_persist", new SimpleStringSchema))

}

