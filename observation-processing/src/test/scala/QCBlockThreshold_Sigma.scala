// Unit testing libraries
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer09
import org.apache.flink.streaming.util.serialization.SimpleStringSchema
import org.scalatest.FunSuite
import org.junit.runner.RunWith
import org.management.observations.processing.bolts.qc.block.threshold.{QCBlockThresholdDeltaSpikeCheck, QCBlockThresholdDeltaStepCheck, QCBlockThresholdRangeCheck, QCBlockThresholdSigmaCheck}
import org.scalatest.junit.JUnitRunner

// Core Flink related libraries
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.scala.DataStream
import org.apache.flink.contrib.streaming.DataStreamUtils

// Project tuples and bolts tested
import org.management.observations.processing.bolts.transform.RawCSVToMetaRecord
import org.management.observations.processing.tuples.SemanticObservation
import org.management.observations.processing.tuples.{MetaDataObservation, MetaOutcomeQualitative}
import org.management.observations.processing.bolts.qc.block.logic.QCBlockLogicTimeseries
import org.management.observations.processing.bolts.qc.block.meta.QCBlockMetaValueRangeCheck
import org.management.observations.processing.bolts.transform.{RawCSVToObservation, RawToSemanticObservation}
import org.management.observations.processing.tuples.{MetaOutcomeQuantitative, QCOutcomeQuantitative}

// Used to read in the CSVObservation.csv file with the observation data
import scala.io.Source._
import scala.collection.JavaConverters.asScalaIteratorConverter

/**
  * This bolt checks the correct operation of the qcBlockMetaValueCheck flatMap
  *
  */
@RunWith(classOf[JUnitRunner])
class QCBlockThreshold_Sigma extends FunSuite {

  def runCode: Iterator[QCOutcomeQuantitative] = {
    val env = StreamExecutionEnvironment.createLocalEnvironment(1)
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
    env.setParallelism(1)

    // Read in the test data
    val observationStream: DataStream[SemanticObservation] = env.fromCollection(
      fromFile("/home/dciar86/GitHub/observation-management-system/observation-processing/src/test/resources/CSVObservations_QCBlock_Threshold_Sigma.csv")
        .getLines().toSeq
    )
      .map(new RawCSVToObservation)
      .filter(_.parseOK == true)
      .map(new RawToSemanticObservation)

    val observationTimeStream = observationStream
      .assignAscendingTimestamps(_.phenomenontimestart)

    // Perform the bolt
    val sigmaConcurrentStream1h: DataStream[QCOutcomeQuantitative] = observationTimeStream
      .keyBy("feature", "procedure", "observableproperty")
      .timeWindow(Time.hours(1))
      .apply(new QCBlockThresholdSigmaCheck())

    val sigmaConcurrentStream12h: DataStream[QCOutcomeQuantitative] = observationTimeStream
      .keyBy("feature", "procedure", "observableproperty")
      .timeWindow(Time.hours(12))
      .apply(new QCBlockThresholdSigmaCheck())

    val sigmaConcurrentStream24h: DataStream[QCOutcomeQuantitative] = observationTimeStream
      .keyBy("feature", "procedure", "observableproperty")
      .timeWindow(Time.hours(24))
      .apply(new QCBlockThresholdSigmaCheck())

    val sigmaStream = sigmaConcurrentStream1h
      .union(sigmaConcurrentStream12h, sigmaConcurrentStream24h)

    // Collect and return the program output
    val rangeStreamOutput: Iterator[QCOutcomeQuantitative] = DataStreamUtils.collect(sigmaStream.javaStream).asScala
    rangeStreamOutput
  }

  // Run Flink code and create a sequence of returned objects
  val obs = runCode
  val dataset = obs.toIndexedSeq

  test("Is the dataset the correct size?") {
//    assert(dataset.size == 60 + (28 + 96))
    assert(dataset.size == 192)
  }

  test("Does the dataset have the correct number of 24h tests?"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/sigma/24h/static/min")
        .size == 32
    )
  }

  test("Does the dataset have the correct number of 12h tests?"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/sigma/12h/static/min")
        .size == 32
    )
  }

  test("Does the dataset have the correct number of 1h tests?"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/sigma/1h/static/min")
        .size == 32
    )
  }

  test("Does the dataset have the correct number of 24h passed min tests?"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/sigma/24h/static/min")
        .filter(_.qualitative == "pass")
        .size == 29
    )
  }

  // For the three observations on the first day
  test("Does the dataset have the correct number of 24h failed min tests?"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/sigma/24h/static/min")
        .filter(_.qualitative == "fail")
        .size == 3
    )
  }

  test("Does the dataset have the correct number of 24h passed max tests?"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/sigma/24h/static/max")
        .filter(_.qualitative == "pass")
        .size == 32
    )
  }

  // 12 hour


  test("Does the dataset have the correct number of 12h passed min tests?"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/sigma/12h/static/min")
        .filter(_.qualitative == "pass")
        .size == 30
    )
  }

  // For the three observations on the first day
  test("Does the dataset have the correct number of 12h failed min tests?"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/sigma/12h/static/min")
        .filter(_.qualitative == "fail")
        .size == 2
    )
  }

  test("Does the dataset have the correct number of 12h passed max tests?"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/sigma/12h/static/max")
        .filter(_.qualitative == "pass")
        .size == 32
    )
  }


  // 1 hour


  test("Does the dataset have the correct number of 1h passed min tests?"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/sigma/1h/static/min")
        .filter(_.qualitative == "pass")
        .size == 32
    )
  }

  // For the three observations on the first day
  test("Does the dataset have the correct number of 1h failed min tests?"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/sigma/1h/static/min")
        .filter(_.qualitative == "fail")
        .size == 0
    )
  }

  test("Does the dataset have the correct number of 1h passed max tests?"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/sigma/1h/static/max")
        .filter(_.qualitative == "pass")
        .size == 32
    )
  }

  test("Does the correct test give the correct quantitative output?"){
    assert(
      dataset
        .filter(_.quantitative == 0)
        .filter(_.qualitative == "pass")
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/sigma/1h/static/min")
        .size == 32
      &&
      dataset
        .filter(_.quantitative == -1)
        .filter(_.qualitative == "pass")
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/sigma/1h/static/max")
        .size == 32
      &&
      dataset
        .filter(_.quantitative == 0)
        .filter(_.qualitative == "pass")
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/sigma/12h/static/min")
        .size == 30
      &&
      dataset
        .filter(_.quantitative == 0.5)
        .filter(_.qualitative == "fail")
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/sigma/12h/static/min")
        .size == 2
      &&
      dataset
        .filter(_.quantitative == -1)
        .filter(_.qualitative == "pass")
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/sigma/12h/static/max")
        .size == 30
      &&
      dataset
        .filter(_.quantitative == -1.5)
        .filter(_.qualitative == "pass")
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/sigma/12h/static/max")
        .size == 2
      &&
      dataset
        .filter(_.quantitative == -1.5)
        .filter(_.qualitative == "pass")
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/sigma/24h/static/min")
        .size == 24
      &&
      dataset
        .filter(_.quantitative == -0.5)
        .filter(_.qualitative == "pass")
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/sigma/24h/static/max")
        .size == 24
      &&
      dataset
        .filter(_.quantitative == -0.5)
        .filter(_.qualitative == "pass")
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/sigma/24h/static/min")
        .size == 5
      &&
      dataset
        .filter(_.quantitative == -1.5)
        .filter(_.qualitative == "pass")
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/sigma/24h/static/max")
        .size == 5
      &&
      dataset
        .filter(_.quantitative == 1)
        .filter(_.qualitative == "fail")
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/sigma/24h/static/min")
        .size == 3
    )
  }
}