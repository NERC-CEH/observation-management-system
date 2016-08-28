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

import scala.io.Source._


/**
  * This bolt checks the correct operation of the QCBlockLogicTimeseries map
  */
@RunWith(classOf[JUnitRunner])
class QCBlockLogic_TimeSpacing extends FunSuite {


  def runCode: Iterator[QCOutcomeQuantitative] = {
    val env = StreamExecutionEnvironment.createLocalEnvironment(1)

    // Read in the test data
    val observationStream: DataStream[SemanticObservation] = env.fromCollection(
      fromFile("/home/dciar86/GitHub/observation-management-system/code/src/test/resources/CSVObservations_QCBlock_Logic_Spacing.csv")
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
      .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/timing/intendedspacing")

    // Collect and return the program output
    val timeOrderOutput: Iterator[QCOutcomeQuantitative] = DataStreamUtils.collect(timeOrder.javaStream).asScala

    timeOrderOutput
  }

  // Run Flink code and create a sequence of returned objects
  val obs = runCode
  val dataset = obs.toIndexedSeq

  test("Is the dataset the correct size?"){
    assert(dataset.size == 8)
  }

  test("Are the correct number of records marked as failing?"){
    assert(dataset.filter(_.qualitative == "fail").size == 2)
  }

  test("Are the correct number of records marked as passing?"){
    assert(dataset.filter(_.qualitative == "pass").size == 6)
  }

  test("Is the expected time spacing being reported for passing records before state is established?"){
    assert(
      dataset
        .filter(_.qualitative == "pass")
        .filter(_.quantitative == 0)
        .size == 1
    )
  }

  test("Is the expected time spacing being reported for passing records once state is established?"){
    assert(
      dataset
        .filter(_.qualitative == "pass")
        .filter(_.quantitative == -240000)
        .size == 5
    )
  }

  test("Is the expected time spacing being reported for failing records"){
    assert(
      dataset
        .filter(_.qualitative == "fail")
        .filter(_.quantitative == 480000)
        .size == 2
    )
  }
}