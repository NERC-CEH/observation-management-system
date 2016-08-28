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
import org.management.observations.processing.bolts.qc.block.logic.QCBlockLogicDefaultMetaValue
import org.management.observations.processing.tuples.{QCOutcomeQuantitative, SemanticObservation}

// Used to read in the CSVObservation.csv file with the observation data
import scala.io.Source._
import scala.collection.JavaConverters.asScalaIteratorConverter


/**
  * This bolt checks the correct operation of the QCBlockLogicDefaultMetaValue flatMap
  */
@RunWith(classOf[JUnitRunner])
class QCBlockLogic_MetaValue extends FunSuite {


  def runCode: Iterator[QCOutcomeQuantitative] = {
    val env = StreamExecutionEnvironment.createLocalEnvironment(1)

    // Read in the test data
    val observationStream: DataStream[SemanticObservation] = env.fromCollection(
      fromFile("/home/dciar86/GitHub/observation-management-system/code/src/test/resources/CSVObservations_QCBlock_Logic_Identity.csv")
        .getLines().toSeq
    )
      .map(new RawCSVToObservation)
      .filter(_.parseOK == true)
      .map(new RawToSemanticObservation)

    // Perform the bolt
    val valueMetaStream: DataStream[QCOutcomeQuantitative] = observationStream
      .flatMap(new QCBlockLogicDefaultMetaValue())

    // Filter based on the single type of QC check we are testing
    val valueOutput: Iterator[QCOutcomeQuantitative] = DataStreamUtils.collect(valueMetaStream.javaStream).asScala
    valueOutput
  }

  // Run Flink code and create a sequence of returned objects
  val obs = runCode
  val dataset = obs.toIndexedSeq

  test("Is the dataset the correct size?"){
    assert(dataset.size == 20)
  }

  test("Does the dataset have the correct number of battery static min values?"){
    assert(
      dataset
        .filter(_.feature == "southbasin")
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/meta/value/battery/static/min")
        .size == 2
    )
  }

  test("Does the dataset have the correct number of battery static max values?"){
    assert(
      dataset
        .filter(_.feature == "southbasin")
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/meta/value/battery/static/max")
        .size == 2
    )
  }

  test("Does the dataset have the correct number of battery hourly min values?"){
    assert(
      dataset
        .filter(_.feature == "southbasin")
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/meta/value/battery/hourlybattery/min")
        .size == 2
    )
  }

  test("Does the dataset have the correct number of battery hourly max values?"){
    assert(
      dataset
        .filter(_.feature == "southbasin")
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/meta/value/battery/hourlybattery/max")
        .size == 2
    )
  }

  test("Does the dataset have the correct number of battery daily min values?"){
    assert(
      dataset
        .filter(_.feature == "southbasin")
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/meta/value/battery/dailybattery/min")
        .size == 2
    )
  }

  test("Does the dataset have the correct number of battery daily max values?"){
    assert(
      dataset
        .filter(_.feature == "southbasin")
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/meta/value/battery/dailybattery/max")
        .size == 2
    )
  }

  test("Does the dataset have the correct number of battery monthly min values?"){
    assert(
      dataset
        .filter(_.feature == "southbasin")
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/meta/value/battery/monthlybattery/min")
        .size == 2
    )
  }

  test("Does the dataset have the correct number of battery monthly max values?"){
    assert(
      dataset
        .filter(_.feature == "southbasin")
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/meta/value/battery/monthlybattery/max")
        .size == 2
    )
  }

  test("Does the dataset have the correct number of cabling static min values?"){
    assert(
      dataset
        .filter(_.feature == "southbasin")
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/meta/value/cabling/static/min")
        .size == 2
    )
  }

  test("Does the dataset have the correct number of cabling static max values?"){
    assert(
      dataset
        .filter(_.feature == "southbasin")
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/meta/value/cabling/static/max")
        .size == 2
    )
  }
}