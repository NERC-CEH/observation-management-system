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
import org.management.observations.processing.bolts.qc.block.logic.QCBlockLogicDefaultMetaIdentity
import org.management.observations.processing.tuples.{QCOutcomeQualitative, SemanticObservation}

// Used to read in the CSVObservation.csv file with the observation data
import scala.io.Source._
import scala.collection.JavaConverters.asScalaIteratorConverter


/**
  * This bolt checks the correct operation of the QCBlockLogicDefaultMetaIdentity flatMap
  */
@RunWith(classOf[JUnitRunner])
class QCBlockLogic_MetaIdentity extends FunSuite {


  def runCode: Iterator[QCOutcomeQualitative] = {
    val env = StreamExecutionEnvironment.createLocalEnvironment(1)

    // Read in the test data
    val observationStream: DataStream[SemanticObservation] = env.fromCollection(
      fromFile("/home/dciar86/GitHub/observation-management-system/observation-processing/src/test/resources/CSVObservations_QCBlock_Logic_Identity.csv")
        .getLines().toSeq
    )
      .map(new RawCSVToObservation)
      .filter(_.parseOK == true)
      .map(new RawToSemanticObservation)

    // Perform the bolt
    val identityMetaStream: DataStream[QCOutcomeQualitative] = observationStream
      .flatMap(new QCBlockLogicDefaultMetaIdentity())

    // Collect and return the program output
    val identityOutput: Iterator[QCOutcomeQualitative] = DataStreamUtils.collect(identityMetaStream.javaStream).asScala
    identityOutput
  }

  // Run Flink code and create a sequence of returned objects
  val obs = runCode
  val dataset = obs.toIndexedSeq


  test("Is the dataset the correct size?"){
    assert(dataset.size == 6)
  }

  test("Are there two of the cleaning test type?"){
    assert(
      dataset
        .count(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/meta/identity/cleaning") == 2)
  }

  test("Are there two of the maintenance test type?"){
    assert(
      dataset
        .count(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/meta/identity/maintenance") == 2)
  }

  test("Are there two of the internal reset test type?"){
    assert(
      dataset
        .count(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/meta/identity/reset") == 2)
  }

  test("Are there the correct number of SBAS records?"){
    assert(
      dataset
        .count(_.feature == "southbasin") == 6
    )
  }

  test("Do all the records pass?"){
    assert(
      dataset
        .count(_.qualitative == "pass") == 6
    )
  }
}