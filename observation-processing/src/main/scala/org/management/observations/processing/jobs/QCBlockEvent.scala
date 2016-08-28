package org.management.observations.processing.jobs

/**
  * Created by dciar86 on 10/08/16.
  */
class QCBlockEvent {

  /**




  /**
    * Timestamps and Watermarks
    *
    * Both the consecutive null and windowed null checks require
    * observations arranged by time.  The following creates
    * a stream with timestamps that also act as watermarks, requiring
    * a monotonic stream of data, silently dropping observations
    * that do not comply
    */
  val nullStreamTimed = observationStream
    .filter(_.observation.isEmpty)
    .assignAscendingTimestamps(_.phenomenontimestart)

  /**
    * Consecutive null values
    *
    * There is a threshold of the number of contiguous nulls that can be
    * accepted as permissible, the window below keeps track of the current
    * number of consecutive nulls, and generates an event when the threshold
    * is matched or exceeded.  It will not generate another event until the
    * ongoing consecutive null stream ends and a new one reaches threshold.
    */

  val consecutiveNullEvents = nullStreamTimed
    .keyBy("feature","procedure","observableproperty")
    .countWindow(1,1)
    .apply(new QCBlockEventNullConsecutiveCheck)
    .map(_.toString)

  consecutiveNullEvents.addSink(new FlinkKafkaProducer09[String]("localhost:9092", "event_persist", new SimpleStringSchema))


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

  val nullQCEvents12h = nullStreamTimed
    .keyBy("feature","procedure","observableproperty")
    .window(SlidingEventTimeWindows.of(Time.hours(12), Time.hours(6)))
    .apply(new QCBlockEventNullAggregateCheck())
    .map(_.toString)

  val nullQCEvents24h = nullStreamTimed
    .keyBy("feature","procedure","observableproperty")
    .window(SlidingEventTimeWindows.of(Time.hours(24), Time.hours(12)))
    .apply(new QCBlockEventNullAggregateCheck())
    .map(_.toString)

  nullQCEvents1h.addSink(new FlinkKafkaProducer09[String]("localhost:9092", "event_persist", new SimpleStringSchema))
  nullQCEvents12h.addSink(new FlinkKafkaProducer09[String]("localhost:9092", "event_persist", new SimpleStringSchema))
  nullQCEvents24h.addSink(new FlinkKafkaProducer09[String]("localhost:9092", "event_persist", new SimpleStringSchema))





  /**
    * Based on the time stream
    */
  // If an observation ordering QC check failed, create an event
  val outOfOrderEvents: DataStream[QCEvent] = timeQC
    .filter(x => x.quantitative.equals("fail") && x.qualifier.equals("http://placeholder.catalogue.ceh.ac.uk/qc/timing/order"))
    .map(x => createQCEvent(
      x,
      "Observation out of order",
      x.phenomenontimestart,
      x.phenomenontimestart)
    )

  // If an observation has exceeded the expected time between observations,
  //  create an event
  val longSpacingEvents: DataStream[QCEvent] = timeQC
    .filter(x => x.quantitative.equals("fail") && x.qualifier.equals("http://placeholder.catalogue.ceh.ac.uk/qc/timing/intendedspacing"))
    .map(x => createQCEvent(
      x,
      "Observation spacing exceeded",
      x.phenomenontimestart,
      x.phenomenontimestart)
    )

  outOfOrderEvents
    .map(_.toString)
    .addSink(new FlinkKafkaProducer09[String]("localhost:9092", "event_persist", new SimpleStringSchema))

  longSpacingEvents
    .map(_.toString)
    .addSink(new FlinkKafkaProducer09[String]("localhost:9092", "event_persist", new SimpleStringSchema))

    *
    */
}

