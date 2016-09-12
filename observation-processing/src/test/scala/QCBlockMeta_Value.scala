// Unit testing libraries
import org.scalatest.FunSuite
import org.junit.runner.RunWith
import org.management.observations.processing.bolts.qc.block.meta.QCBlockMetaValueRangeCheck
import org.management.observations.processing.tuples.MetaOutcomeQuantitative
import org.scalatest.junit.JUnitRunner

// Core Flink related libraries
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.scala.DataStream
import org.apache.flink.contrib.streaming.DataStreamUtils

// Project tuples and bolts tested
import org.management.observations.processing.bolts.transform.RawCSVToMetaRecord
import org.management.observations.processing.tuples.{MetaDataObservation, MetaOutcomeQualitative}

// Used to read in the CSVObservation.csv file with the observation data
import scala.io.Source._
import scala.collection.JavaConverters.asScalaIteratorConverter

/**
  * This bolt checks the correct operation of the qcBlockMetaValueCheck flatMap
  *
  */
@RunWith(classOf[JUnitRunner])
class QCBlockMeta_Value extends FunSuite {

  def runCode: Iterator[MetaOutcomeQuantitative] = {
    val env = StreamExecutionEnvironment.createLocalEnvironment(1)
    env.setParallelism(1)

    // Read in the test data
    val observationStream: DataStream[MetaDataObservation] = env.fromCollection(
      fromFile("/home/dciar86/GitHub/observation-management-system/observation-processing/src/test/resources/CSVMetaObservations_QCBlock_Meta.csv")
        .getLines().toSeq
    )
      .map(new RawCSVToMetaRecord)

    // Perform the bolt
    val valueBasedStream = observationStream
      .filter(_.parseOK == true)
      .filter(_.value != "NotAValue")
      .flatMap(new QCBlockMetaValueRangeCheck())

    // Collect and return the program output
    val rawStreamOutput: Iterator[MetaOutcomeQuantitative] = DataStreamUtils.collect(valueBasedStream.javaStream).asScala
    rawStreamOutput
  }

  // Run Flink code and create a sequence of returned objects
  val obs = runCode
  val dataset = obs.toIndexedSeq


  test("Is the dataset the correct size?"){
    assert(dataset.size == 17)
  }

  test("Does the dataset contain the correct number of static min battery entries?"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/meta/value/range/battery/static/min")
        .size == 2
    )
  }

  test("Does the dataset contain the correct number of static min battery entries for prtone?"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/meta/value/range/battery/static/min")
        .filter(_.procedure == "prtone")
        .size == 1
    )
  }

  test("Does the dataset contain the correct number of static max battery entries?"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/meta/value/range/battery/static/max")
        .size == 2
    )
  }

  test("Does the dataset contain the correct number of static max battery entries for prtone?"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/meta/value/range/battery/static/max")
        .filter(_.procedure == "prtone")
        .size == 1
    )
  }

  test("Does the dataset contain the correct number of hourly min battery entries?"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/meta/value/range/battery/hourlybattery/min")
        .size == 4
    )
  }

  test("Does the dataset contain the correct number of hourly min battery entries for prtone?"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/meta/value/range/battery/hourlybattery/min")
        .filter(_.procedure == "prtone")
        .size == 2
    )
  }

  test("Does the dataset contain the correct number of hourly min battery entries for prttwo?"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/meta/value/range/battery/hourlybattery/min")
        .filter(_.procedure == "prtone")
        .size == 2
    )
  }

  test("Does the dataset contain the correct number of hourly max battery entries?"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/meta/value/range/battery/hourlybattery/max")
        .size == 0
    )
  }

  test("Does the dataset contain the correct number of daily min test failures for all sensors?") {
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/meta/value/range/battery/dailybattery/min")
        .size == 4
    )
  }

  test("Does the dataset contain the correct number of daily min test failures for prtone?") {
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/meta/value/range/battery/dailybattery/min")
        .filter(_.procedure == "prtone")
        .size == 2
    )
  }

  test("Does the dataset contain the correct number of daily min test failures for prttwo?") {
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/meta/value/range/battery/dailybattery/min")
        .filter(_.procedure == "prttwo")
        .size == 2
    )
  }

  test("Does the dataset contain the correct number of monthly min test failures for all sensors?") {
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/meta/value/range/battery/monthlybattery/min")
        .size == 4
    )
  }

  test("Does the dataset contain the correct number of monthly min test failures for prtone?") {
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/meta/value/range/battery/monthlybattery/min")
        .filter(_.procedure == "prtone")
        .size == 2
    )
  }

  test("Does the dataset contain the correct number of monthly min test failures for prttwo?") {
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/meta/value/range/battery/monthlybattery/min")
        .filter(_.procedure == "prttwo")
        .size == 2
    )
  }

  test("Does the dataset contain the correct static quantitative values?"){
    assert(
      dataset
        .filter(_.quantitative == 1)
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/meta/value/range/battery/static/min")
        .size == 2
      &&
      dataset
        .filter(_.quantitative == 1)
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/meta/value/range/battery/static/max")
        .size == 2
    )
  }

  test("Does the dataset contain the correct non-static quantitative values?"){
    assert(
      dataset
        .filter(_.quantitative == 3.5)
        .size == 6
        &&
        dataset
          .filter(_.quantitative == 5)
          .size == 6
    )
  }
}


