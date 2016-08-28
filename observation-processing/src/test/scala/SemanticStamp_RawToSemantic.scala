// Unit testing libraries
import org.scalatest.FunSuite
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

// Core Flink related libraries
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.scala.DataStream
import org.apache.flink.contrib.streaming.DataStreamUtils

// Project tuples and bolts tested
import org.management.observations.processing.bolts.transform.{RawCSVToObservation, RawToSemanticObservation}
import org.management.observations.processing.tuples.{SemanticObservation}

// Used to read in the CSVObservation.csv file with the observation data
import scala.io.Source._
import scala.collection.JavaConverters.asScalaIteratorConverter

/**
  * This bolt checks the correct operation of the RawToSemanticObservation map
  */
@RunWith(classOf[JUnitRunner])
class SemanticStamp_RawToSemantic extends FunSuite {

  def runCode: Iterator[SemanticObservation] = {
    val env = StreamExecutionEnvironment.createLocalEnvironment(1)

    // Read in the test data
    val observationStream: DataStream[String] = env.fromCollection(
      fromFile("/home/dciar86/GitHub/observation-management-system/code/src/test/resources/CSVObservations_SemanticStamp.csv")
        .getLines().toSeq
    )

    // Perform the bolt
    val semanticObjects: DataStream[SemanticObservation] = observationStream
      .map(new RawCSVToObservation())
      .filter(_.parseOK == true)
      .map(new RawToSemanticObservation())

    // Collect and return the program output
    val semanticStreamOutput: Iterator[SemanticObservation] = DataStreamUtils.collect(semanticObjects.javaStream).asScala
    semanticStreamOutput
  }

  // Run Flink code and create a sequence of returned objects
  val obs = runCode
  val dataset = obs.toIndexedSeq

  test("Does the dataset contain the correct number of observations?"){
    assert(dataset.size == 7)
  }

  test("Does the dataset contain the correct number of numeric observations?"){
    assert(
      dataset
        .filter(_.observationType == "Numerical")
        .filter(x => x.categoricalObservation.isEmpty)
        .size == 6
    )
  }

  test("Does the dataset contain the correct number of categorical observations?"){
    assert(
      dataset
        .filter(_.observationType == "Categorical")
        .filter(_.categoricalObservation.isDefined)
        .size == 1
    )
  }

  test("Has the year parsed successfully?"){
    assert(
      dataset
        .filter(_.year == 2016)
        .size == 7
    )
  }

  test("has the month parsed successfully?"){
    assert(
      dataset
        .filter(_.month == 8)
        .size == 7
    )
  }

  test("Has the phenomenontimeend been assigned properly?"){
    assert(
      dataset
        .filter(x => x.phenomenontimestart == x.phenomenontimeend)
        .size == 7
    )
  }
}
