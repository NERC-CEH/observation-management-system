// Unit testing libraries
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer09
import org.apache.flink.streaming.util.serialization.SimpleStringSchema
import org.scalatest.FunSuite
import org.junit.runner.RunWith
import org.management.observations.processing.bolts.qc.block.threshold.{QCBlockThresholdDeltaSpikeCheck, QCBlockThresholdDeltaStepCheck, QCBlockThresholdRangeCheck}
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
  class QCBlockThreshold_Delta extends FunSuite {

  def runCode: Iterator[QCOutcomeQuantitative] = {
    val env = StreamExecutionEnvironment.createLocalEnvironment(1)
    env.setParallelism(1)

    // Read in the test data
    val observationStream: DataStream[SemanticObservation] = env.fromCollection(
      fromFile("/home/dciar86/GitHub/observation-management-system/code/src/test/resources/CSVObservations_QCBlock_Threshold_Delta.csv")
        .getLines().toSeq
    )
      .map(new RawCSVToObservation)
      .filter(_.parseOK == true)
      .map(new RawToSemanticObservation)

    // Perform the bolt
    val deltaStep: DataStream[QCOutcomeQuantitative] = observationStream
      .keyBy("feature","procedure","observableproperty")
      .countWindow(2,1)
      .apply(new QCBlockThresholdDeltaStepCheck())

    val deltaSpike: DataStream[QCOutcomeQuantitative] = observationStream
      .keyBy("feature","procedure","observableproperty")
      .countWindow(3,1)
      .apply(new QCBlockThresholdDeltaSpikeCheck())

    val deltaStream = deltaSpike.union(deltaStep)


    // Collect and return the program output
    val rangeStreamOutput: Iterator[QCOutcomeQuantitative] = DataStreamUtils.collect(deltaStream.javaStream).asScala
    rangeStreamOutput
  }

  // Run Flink code and create a sequence of returned objects
  val obs = runCode
  val dataset = obs.toIndexedSeq

  test("Is the dataset the correct size?"){
    assert(dataset.size == 60+(28+96))
  }

  test("Does the dataset contain the correct number of spike min tests?"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/delta/spike/static/min")
        .size == 30
    )
  }

  test("Does the dataset contain the correct number of spike max tests?"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/delta/spike/static/max")
        .size == 30
    )
  }

  test("Does the dataset contain the correct number of passed spike min tests?"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/delta/spike/static/min")
        .filter(_.qualitative == "pass")
        .size == 30
    )
  }

    test("Does the dataset contain the correct number of passed spike max tests?"){
      assert(
        dataset
          .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/delta/spike/static/max")
          .filter(_.qualitative == "pass")
          .size == 21
      )
    }

  test("Does the dataset contain the correct number of failed spike max tests?"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/delta/spike/static/max")
        .filter(_.qualitative == "fail")
        .size == 9
    )
  }

    // STEP TESTS


    test("Does the dataset contain the correct number of step min tests?"){
      assert(
        dataset
          .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/delta/step/static/min")
          .size == 62
      )
    }

    test("Does the dataset contain the correct number of step max tests?"){
      assert(
        dataset
          .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/delta/step/static/max")
          .size == 62
      )
    }

    test("Does the dataset contain the correct number of passed step min tests?"){
      assert(
        dataset
          .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/delta/step/static/min")
          .filter(_.qualitative == "pass")
          .size == 62
      )
    }

    test("Does the dataset contain the correct number of passed step max tests?"){
      assert(
        dataset
          .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/delta/step/static/max")
          .filter(_.qualitative == "pass")
          .size == 48
      )
    }

    test("Does the dataset contain the correct number of failed step max tests?"){
      assert(
        dataset
          .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/delta/step/static/max")
          .filter(_.qualitative == "fail")
          .size == 14
      )
    }

    test("Does the dataset contain the correct quantitative step values?"){
      assert(
        dataset
          .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/delta/step/static/max")
          .filter(_.quantitative == 1)
          .filter(_.qualitative == "fail")
          .size == 6
        &&
        dataset
          .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/delta/step/static/max")
          .filter(_.quantitative == 19)
          .filter(_.qualitative == "fail")
          .size == 2
        &&
        dataset
          .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/delta/step/static/max")
          .filter(_.quantitative == 17)
          .filter(_.qualitative == "fail")
          .size == 2
        &&
        dataset
          .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/delta/step/static/max")
          .filter(_.quantitative == 61)
          .filter(_.qualitative == "fail")
          .size == 2
        &&
        dataset
          .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/delta/step/static/max")
          .filter(_.quantitative == 59)
          .filter(_.qualitative == "fail")
          .size == 2
      )
    }

    test("Does the dataset contain the correct quantitative spike values?"){
      assert(
        dataset
          .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/delta/spike/static/max")
          .filter(_.quantitative == 13)
          .filter(_.qualitative == "fail")
          .size == 4
          &&
          dataset
            .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/delta/spike/static/max")
            .filter(_.quantitative == 7)
            .filter(_.qualitative == "fail")
            .size == 1
          &&
          dataset
            .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/delta/spike/static/max")
            .filter(_.quantitative == 49)
            .filter(_.qualitative == "fail")
            .size == 1
          &&
          dataset
            .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/delta/spike/static/max")
            .filter(_.quantitative == 9)
            .filter(_.qualitative == "fail")
            .size == 1
          &&
          dataset
            .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/delta/spike/static/max")
            .filter(_.quantitative == 133)
            .filter(_.qualitative == "fail")
            .size == 1
      )
    }
}
