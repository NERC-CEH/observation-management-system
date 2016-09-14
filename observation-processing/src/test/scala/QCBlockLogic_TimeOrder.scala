// Unit testing libraries
import org.scalatest.FunSuite
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

// Core Flink related libraries
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.scala.DataStream
import org.apache.flink.contrib.streaming.DataStreamUtils

// Project tuples and bolts tested
import org.management.observations.processing.bolts.qc.block.logic.QCBlockLogicTimeseries
import org.management.observations.processing.bolts.transform.{RawCSVToObservation, RawToSemanticObservation}
import org.management.observations.processing.tuples.{QCOutcomeQuantitative, SemanticObservation}

// Used to read in the CSVObservation.csv file with the observation data
import scala.io.Source._
import scala.collection.JavaConverters.asScalaIteratorConverter

/**
  * This bolt checks the correct operation of the QCBlockLogicTimeseries map
  */
@RunWith(classOf[JUnitRunner])
class QCBlockLogic_TimeOrder extends FunSuite {


  def runCode: Iterator[QCOutcomeQuantitative] = {
    val env = StreamExecutionEnvironment.createLocalEnvironment(1)

    // Read in the test data
    val observationStream: DataStream[SemanticObservation] = env.fromCollection(
      fromFile("/home/dciar86/GitHub/observation-management-system/observation-processing/src/test/resources/CSVObservations_QCBlock_Logic_Order.csv")
        .getLines().toSeq
    )
      .map(new RawCSVToObservation)
      .filter(_.parseOK == true)
      .map(new RawToSemanticObservation)

    // Perform the bolt
    val timeQC: DataStream[QCOutcomeQuantitative] = observationStream
      .keyBy("feature","procedure","observableproperty")
      .countWindow(1,1)
      .apply(new QCBlockLogicTimeseries())

    // Filter based on the single type of QC check we are testing
    val timeOrder = timeQC
      .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/timing/order")

    // Collect and return the program output
    val timeOrderOutput: Iterator[QCOutcomeQuantitative] = DataStreamUtils.collect(timeOrder.javaStream).asScala
    timeOrderOutput
  }

  // Run Flink code and create a sequence of returned objects
  val obs = runCode
  val dataset = obs.toIndexedSeq


  test("Does the dataset contain the correct number of observations?"){
    assert(dataset.size == 11)
  }

  test("Does the dataset contain the correct number of OK observations?"){
    assert(
      dataset
        .filter(_.qualitative == "http://placeholder.catalogue.ceh.ac.uk/pass")
        .size == 8)
  }

  test("Does the dataset contain the correct number of out of order observations?"){
    assert(
      dataset
        .filter(_.qualitative == "http://placeholder.catalogue.ceh.ac.uk/fail")
        .size == 3)
  }

  test("Does the dataset contain the correct quantitative output for the ordered observations?"){
    assert(
      dataset
        .filter(_.qualitative == "http://placeholder.catalogue.ceh.ac.uk/pass")
        .count(x => x.quantitative == -100000) == 7
    )
  }

  test("Does the dataset contain the correct quantitative output for the first observation?"){
    assert(
      dataset
        .filter(_.qualitative == "http://placeholder.catalogue.ceh.ac.uk/pass")
        .exists(_.quantitative == 0)
    )
  }

  test("Does the dataset contain the correct quantitative output for the out of order observations?"){
    assert(
      dataset
        .filter(_.qualitative == "http://placeholder.catalogue.ceh.ac.uk/fail")
        .exists(_.quantitative == 400000) &&
      dataset
        .filter(_.qualitative == "http://placeholder.catalogue.ceh.ac.uk/fail")
        .exists(_.quantitative == 500000) &&
      dataset
        .filter(_.qualitative == "http://placeholder.catalogue.ceh.ac.uk/fail")
        .exists(_.quantitative == 600000)
    )
  }
}