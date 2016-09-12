// Unit testing libraries
import org.scalatest.FunSuite
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

// Core Flink related libraries
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.scala.DataStream
import org.apache.flink.contrib.streaming.DataStreamUtils

// Project tuples and bolts tested
import org.management.observations.processing.bolts.transform.RawCSVToObservation
import org.management.observations.processing.bolts.transform.RawToSemanticObservation
import org.management.observations.processing.bolts.qc.block.logic.QCBlockLogicNull
import org.management.observations.processing.tuples.{QCOutcomeQualitative, SemanticObservation}

// Used to read in the CSVObservation.csv file with the observation data
import scala.io.Source._
import scala.collection.JavaConverters.asScalaIteratorConverter


/**
  * This bolt checks the correct operation of the QCBlockLogicNull
  */
@RunWith(classOf[JUnitRunner])
class QCBlockLogic_NullStream extends FunSuite {


  def runCode: Iterator[QCOutcomeQualitative] = {
    val env = StreamExecutionEnvironment.createLocalEnvironment(1)

    // Read in the test data
    val observationStream: DataStream[SemanticObservation] = env.fromCollection(
      fromFile("/home/dciar86/GitHub/observation-management-system/observation-processing/src/test/resources/CSVObservations_QCBlock_Logic_NullStream.csv")
        .getLines().toSeq
    )
      .map(new RawCSVToObservation)
      .filter(_.parseOK == true)
      .map(new RawToSemanticObservation)

    // Perform the bolt
    val passedNullQCStream: DataStream[QCOutcomeQualitative] = observationStream
      .map(new QCBlockLogicNull())

    // Collect and return the program output
    val passedNullStreamOutput: Iterator[QCOutcomeQualitative] = DataStreamUtils.collect(passedNullQCStream.javaStream).asScala
    passedNullStreamOutput
  }

  // Run Flink code and create a sequence of returned objects
  val obs = runCode
  val dataset = obs.toIndexedSeq

  test("Is the dataset the correct size?"){
    assert(dataset.size == 6)
  }

  test("Does the dataset contain the correct number of pass values?"){
    assert(
      dataset
        .filter(_.qualitative == "pass")
        .size == 3
    )
  }

  test("Does the dataset contain the correct number of fail values?"){
    assert(
      dataset
        .filter(_.qualitative == "fail")
        .size == 3
    )
  }
}
