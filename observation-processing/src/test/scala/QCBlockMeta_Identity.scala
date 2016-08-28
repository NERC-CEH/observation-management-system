// Unit testing libraries
import org.scalatest.FunSuite
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

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
class QCBlockMeta_Identity extends FunSuite {


  def runCode: Iterator[MetaOutcomeQualitative] = {
    val env = StreamExecutionEnvironment.createLocalEnvironment(1)
    env.setParallelism(1)

    // Read in the test data
    val observationStream: DataStream[MetaDataObservation] = env.fromCollection(
      fromFile("/home/dciar86/GitHub/observation-management-system/code/src/test/resources/CSVMetaObservations_QCBlock_Meta.csv")
        .getLines().toSeq
    )
      .map(new RawCSVToMetaRecord)

    // Perform the bolt
    val identityBasedStream = observationStream
      .filter(_.parseOK == true)
      .filter(_.value == "NotAValue")
      .flatMap(new QCBlockMetaIdentityCheck())

    // Collect and return the program output
    val rawStreamOutput: Iterator[MetaOutcomeQualitative] = DataStreamUtils.collect(identityBasedStream.javaStream).asScala
    rawStreamOutput
  }

  // Run Flink code and create a sequence of returned objects
  val obs = runCode
  val dataset = obs.toIndexedSeq

  test("Is the dataset the correct size?"){
    assert(dataset.size == 2)
  }

  test("Does the dataset contain the correct assignment of feature, observableproperty for prtone?"){
    assert(
      dataset
        .filter(_.qualitative == "fail")
        .filter(_.procedure == "prtone")
        .filter(_.feature == "southbasin")
        .filter(_.observableproperty == "temperature")
        .size == 1
    )
  }

  test("Does the dataset contain the correct assignment of feature, observableproperty for prttwo?"){
    assert(
      dataset
        .filter(_.qualitative == "fail")
        .filter(_.procedure == "prttwo")
        .filter(_.feature == "southbasin")
        .filter(_.observableproperty == "temperature")
        .size == 1
    )
  }
}


