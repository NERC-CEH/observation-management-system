// Unit testing libraries
import org.scalatest.FunSuite
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

// Core Flink related libraries
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.scala.DataStream
import org.apache.flink.contrib.streaming.DataStreamUtils

// Project tuples and bolts tested
import org.management.observations.processing.bolts.transform.RawCSVToMetaRecord
import org.management.observations.processing.tuples.MetaDataObservation

// Used to read in the CSVObservation.csv file with the observation data
import scala.io.Source._
import scala.collection.JavaConverters.asScalaIteratorConverter



/**
  * This bolt checks the correct operation of the RawCSVToMetaRecord map
  */
@RunWith(classOf[JUnitRunner])
class QCBlockMeta_CSVToMeta extends FunSuite {


  def runCode: Iterator[MetaDataObservation] = {
    val env = StreamExecutionEnvironment.createLocalEnvironment(1)
    env.setParallelism(1)

    // Read in the test data and perform the bolt
    val observationStream: DataStream[MetaDataObservation] = env.fromCollection(
      fromFile("/home/dciar86/GitHub/observation-management-system/code/src/test/resources/CSVMetaObservations_QCBlock_Meta.csv")
        .getLines().toSeq
    )
      .map(new RawCSVToMetaRecord)

    // Collect and return the program output
    val metaStreamOutput: Iterator[MetaDataObservation] = DataStreamUtils.collect(observationStream.javaStream).asScala
    metaStreamOutput
  }

  // Run Flink code and create a sequence of returned objects
  val obs = runCode
  val dataset = obs.toIndexedSeq

  test("Is the dataset the correct size?"){
    assert(dataset.size == 16)
  }

  test("Are there the correct number of pass entries?"){
    assert(dataset.filter(_.parseOK == true).size == 9)
  }

  test("Are there the correct number of fail entries?"){
    assert(dataset.filter(_.parseOK == false).size == 7)
  }
}


