// Unit testing libraries
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer09
import org.apache.flink.streaming.util.serialization.SimpleStringSchema
import org.scalatest.FunSuite
import org.junit.runner.RunWith
import org.management.observations.processing.ProjectConfiguration
import org.management.observations.processing.bolts.qc.block.event.{QCBlockEventNullAggregateCheck, QCBlockEventNullConsecutiveCheck}
import org.management.observations.processing.bolts.qc.block.logic.{QCBlockLogicNull, QCBlockLogicTimeseries}
import org.management.observations.processing.bolts.transform.{RawCSVToObservation, RawToSemanticObservation}
import org.management.observations.processing.tuples.{QCEvent, QCOutcomeQuantitative}
import org.scalatest.junit.JUnitRunner

import scala.collection.JavaConversions._

// Core Flink related libraries
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.scala.DataStream
import org.apache.flink.contrib.streaming.DataStreamUtils

// Project tuples and bolts tested
import org.management.observations.processing.bolts.qc.block.meta.QCBlockMetaIdentityCheck
import org.management.observations.processing.bolts.transform.RawCSVToMetaRecord
import org.management.observations.processing.tuples.{MetaDataObservation, MetaOutcomeQualitative}

// Used to read in the CSVObservation.csv file with the observation data
import scala.io.Source._
import scala.collection.JavaConverters.asScalaIteratorConverter

/**
  * This bolt checks the correct operation of the qcBlockMetaIdentityCheck flatMap
  *
  */
@RunWith(classOf[JUnitRunner])
class QCBlockEvent_All extends FunSuite {


  def runCode: Iterator[QCEvent] = {
    val env = StreamExecutionEnvironment.createLocalEnvironment(1)
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
    env.setParallelism(1)

    // Read the parameter configuration file
    val params: ParameterTool = ParameterTool.fromMap(mapAsJavaMap(ProjectConfiguration.configMap))

    // Read in the test data
    val observationStream = env.fromCollection(
      fromFile("/home/dciar86/GitHub/observation-management-system/observation-processing/src/test/resources/CSVObservations_QCEvent_All.csv")
        .getLines().toSeq
    )
      .map(new RawCSVToObservation())
      .filter(_.parseOK == true)
      .map(new RawToSemanticObservation())

    val qualitativeStream = observationStream
      .map(new QCBlockLogicNull())

    val quantitativeStream: DataStream[QCOutcomeQuantitative] = observationStream
      .keyBy("feature","procedure","observableproperty")
      .countWindow(1,1)
      .apply(new QCBlockLogicTimeseries())

    val nullStreamTimed = qualitativeStream
      .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/null/value")
      .filter(_.qualitative == "http://placeholder.catalogue.ceh.ac.uk/fail")
      .assignAscendingTimestamps(_.phenomenontimestart)

    val consecutiveNullEvents = nullStreamTimed
      .keyBy("feature","procedure","observableproperty")
      .countWindow(1,1)
      .apply(new QCBlockEventNullConsecutiveCheck)

    val nullQCEvents1h = nullStreamTimed
      .keyBy("feature","procedure","observableproperty")
      .window(SlidingEventTimeWindows.of(Time.hours(1), Time.minutes(30)))
      .apply(new QCBlockEventNullAggregateCheck())


    val nullQCEvents12h = nullStreamTimed
      .keyBy("feature","procedure","observableproperty")
      .window(SlidingEventTimeWindows.of(Time.hours(12), Time.hours(6)))
      .apply(new QCBlockEventNullAggregateCheck())


    val nullQCEvents24h = nullStreamTimed
      .keyBy("feature","procedure","observableproperty")
      .window(SlidingEventTimeWindows.of(Time.hours(24), Time.hours(12)))
      .apply(new QCBlockEventNullAggregateCheck())


    val outOfOrderEvents = quantitativeStream
      .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/timing/order")
      .filter(_.qualitative == "http://placeholder.catalogue.ceh.ac.uk/fail")
      .map(x =>
        QCEvent(
          x.feature,
          x.procedure,
          x.observableproperty,
          params.get("qc-event-order"),
          x.phenomenontimestart,
          x.phenomenontimestart)
      )

    val longSpacingEvents = quantitativeStream
      .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/timing/intendedspacing")
      .filter(_.qualitative == "http://placeholder.catalogue.ceh.ac.uk/fail")
      .map(x =>
        QCEvent(
          x.feature,
          x.procedure,
          x.observableproperty,
          params.get("qc-event-spacing"),
          x.phenomenontimestart,
          x.phenomenontimestart)
      )

    val allStreams = consecutiveNullEvents
      .union(nullQCEvents1h, nullQCEvents12h, nullQCEvents24h, outOfOrderEvents, longSpacingEvents)

    // Collect and return the program output
    val rawStreamOutput: Iterator[QCEvent] = DataStreamUtils.collect(allStreams.javaStream).asScala
    rawStreamOutput
  }

  // Run Flink code and create a sequence of returned objects
  val obs = runCode
  val dataset = obs.toIndexedSeq

  test("Is the dataset the correct size?"){
    assert(dataset.size == 58)
  }

  test("Are the correct number of events marking observations outside the expected spacing duration?"){
    assert(
      dataset
        .filter(_.event == "http://placeholder.catalogue.ceh.ac.uk/event/timing/spacing")
        .size == 39
    )
  }

  test("Are the correct number of 1h aggregate null events generated?"){
    assert(
      dataset
        .filter(_.event == "http://placeholder.catalogue.ceh.ac.uk/event/null/aggregate/1h")
        .size == 2
    )
  }

  test("Are the correct number of 12h aggregate null events generated?"){
    assert(
      dataset
        .filter(_.event == "http://placeholder.catalogue.ceh.ac.uk/event/null/aggregate/12h")
        .size == 9
    )
  }

  test("Are the correct number of 24h aggregate null events generated?"){
    assert(
      dataset
        .filter(_.event == "http://placeholder.catalogue.ceh.ac.uk/event/null/aggregate/24h")
        .size == 5
    )
  }

  test("Are the correct number of events marking consecutive nulls generated?"){
    assert(
      dataset
        .filter(_.event == "http://placeholder.catalogue.ceh.ac.uk/event/null/consecutive")
        .size == 2
    )
  }

  test("Are the correct number of events marking out of order observations generated?"){
    assert(
      dataset
        .filter(_.event == "http://placeholder.catalogue.ceh.ac.uk/event/timing/order")
        .size == 1
    )
  }
}


