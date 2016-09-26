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
import org.management.observations.processing.tuples.RawObservation

// Used to read in the CSVObservation.csv file with the observation data
import scala.io.Source._
import scala.collection.JavaConverters.asScalaIteratorConverter

/**
  * This bolt checks the correct operation of the RAWCSVToObservation map
  */
@RunWith(classOf[JUnitRunner])
class SemanticStamp_CSVToRaw extends FunSuite {

  def runCode: Iterator[RawObservation] = {
    val env = StreamExecutionEnvironment.createLocalEnvironment(1)
    env.setParallelism(1)

    // Read in the test data
    val observationStream: DataStream[String] = env.fromCollection(
      fromFile("/home/dciar86/GitHub/observation-management-system/observation-processing/src/test/resources/CSVObservations_SemanticStamp.csv")
        .getLines().toSeq
    )

    // Perform the bolt
    val rawObservations: DataStream[RawObservation] = observationStream
      .map(new RawCSVToObservation())

    // Collect and return the program output
    val rawStreamOutput: Iterator[RawObservation] = DataStreamUtils.collect(rawObservations.javaStream).asScala
    rawStreamOutput
  }

  // Run Flink code and create a sequence of returned objects
  val obs = runCode
  val dataset = obs.toIndexedSeq

  test("Does the dataset contain the correct number of observations?"){
    assert(dataset.size == 16)
  }

  test("Does the dataset contain the correct number of OK observations?"){
    assert(
      dataset
        .filter(_.parseOK)
        .size == 6
    )
  }

  test("Does the dataset contain the correct number of OK numeric observations?"){
    assert(
      dataset
        .filter(_.observationType == "numeric")
        .filter(_.parseOK == true)
        .size == 6
    )
  }

  test("Does the dataset contain the correct number of OK categorical observations"){
    assert(
      dataset
        .filter(_.observationType == "category")
        .filter(_.parseOK == true)
        .size == 0
    )
  }

  test("Does the dataset contain the correct number of failed by typenumerical observations?"){
    assert(
      dataset
        .filter(_.parseMessage == "Observation type from registry not matched.")
        .filter(_.parseOK == false)
        .size == 1
    )
  }
  test("Does the dataset contain the correct number of timestamp parsing errors?"){
    assert(
      dataset
        .filter(_.parseMessage == "Incorrect time representation.")
        .size == 1
    )
  }

  test("Does the dataset contain the correct number of registry lookup failures?"){
    assert(
      dataset
        .filter(_.parseMessage == "Registry lookup failed.")
        .size == 2
    )
  }

  test("Does the dataset contain the correct number of malformed parsing errors?"){
    assert(
      dataset
        .filter(_.parseMessage == "Malformed observation tuple.")
        .size == 5
    )
  }

  test("Does the dataset contain the correct number of metadata parsing errors?"){
    assert(
      dataset
        .filter(_.parseMessage == "Metadata is not correctly formatted.")
        .size == 1
    )
  }
}


