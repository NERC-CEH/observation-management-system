// Unit testing libraries
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer09
import org.apache.flink.streaming.util.serialization.SimpleStringSchema
import org.scalatest.FunSuite
import org.junit.runner.RunWith
import org.management.observations.processing.ProjectConfiguration
import org.management.observations.processing.bolts.qc.block.threshold.QCBlockThresholdRangeCheck
import org.management.observations.processing.bolts.routing.InjectRoutingInfo
import org.management.observations.processing.tuples.RoutedObservation
import org.scalatest.junit.JUnitRunner

// Core Flink related libraries
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.scala.DataStream
import org.apache.flink.contrib.streaming.DataStreamUtils

// Project tuples and bolts tested
import org.management.observations.processing.bolts.transform.RawCSVToMetaRecord
import org.management.observations.processing.tuples.SemanticObservation
import org.management.observations.processing.tuples.{MetaDataObservation, MetaOutcomeQualitative}
import org.management.observations.processing.bolts.qc.block.logic.QCBlockLogicTimeseries
import org.management.observations.processing.bolts.qc.block.meta.QCBlockMetaValueRangeCheck
import org.management.observations.processing.bolts.transform.{RawCSVToObservation, RawToSemanticObservation}
import org.management.observations.processing.tuples.{MetaOutcomeQuantitative, QCOutcomeQuantitative}

import scala.collection.JavaConversions._

// Used to read in the CSVObservation.csv file with the observation data
import scala.io.Source._
import scala.collection.JavaConverters.asScalaIteratorConverter

/**
  * This bolt checks the correct operation of the qcBlockMetaValueCheck flatMap
  *
  */
@RunWith(classOf[JUnitRunner])
class QCBlockThreshold_Range extends FunSuite {

  def runCode: Iterator[QCOutcomeQuantitative] = {
    val env = StreamExecutionEnvironment.createLocalEnvironment(1)
    env.setParallelism(1)

    // Read the parameter configuration file
    val params: ParameterTool = ParameterTool.fromMap(mapAsJavaMap(ProjectConfiguration.configMap))

    // Read in the test data
    val observationStream: DataStream[RoutedObservation] = env.fromCollection(
      fromFile("/home/dciar86/GitHub/observation-management-system/observation-processing/src/test/resources/CSVObservations_QCBlock_Threshold_Range.csv")
        .getLines().toSeq
    )
      .map(new RawCSVToObservation)
      .filter(_.parseOK == true)
      .map(new RawToSemanticObservation)
      .flatMap(new InjectRoutingInfo)

    // Perform the bolt
    val rangeStream: DataStream[QCOutcomeQuantitative] = observationStream
      .filter(_.routes.map(_.model).contains(params.get("routing-qc-block-threshold-range")))
      .map(_.observation)
      .flatMap(new QCBlockThresholdRangeCheck())

    // Collect and return the program output
    val rangeStreamOutput: Iterator[QCOutcomeQuantitative] = DataStreamUtils.collect(rangeStream.javaStream).asScala
    rangeStreamOutput
  }

  // Run Flink code and create a sequence of returned objects
  val obs = runCode
  val dataset = obs.toIndexedSeq

  dataset.foreach(x => println(x.qualifier))
  test("Is the dataset the correct size?"){
    assert(dataset.size == 142)
  }

  test("Does the dataset contain the correct number of static min test outcomes"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/range/static/min")
        .size == 32
    )
  }

  test("Does the dataset contain the correct number of static max test outcomes"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/range/static/max")
        .size == 32
    )
  }

  test("Does the dataset contain the correct number of static min pass outcomes"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/range/static/min")
        .filter(_.qualitative == "http://placeholder.catalogue.ceh.ac.uk/pass")
        .size == 29
    )
  }

  test("Does the dataset contain the correct number of static max pass outcomes"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/range/static/max")
        .filter(_.qualitative == "http://placeholder.catalogue.ceh.ac.uk/pass")
        .size == 30
    )
  }

  test("Does the dataset contain the correct number of static min fail outcomes"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/range/static/min")
        .filter(_.qualitative == "http://placeholder.catalogue.ceh.ac.uk/fail")
        .size == 3
    )
  }

  test("Does the dataset contain the correct number of static max fail outcomes"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/range/static/max")
        .filter(_.qualitative == "http://placeholder.catalogue.ceh.ac.uk/fail")
        .size == 2
    )
  }

  test("Does the dataset contain the correct number of min hourly test outcomes?"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/range/hourlyTL/min")
        .size ==1
    )
  }

  test("Does the dataset contain the correct number of max hourly test outcomes?"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/range/hourlyTL/max")
        .size ==1
    )
  }

  test("Does the dataset contain the correct number of min hourly test pass outcomes?"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/range/hourlyTL/min")
          .filter(_.qualitative == "http://placeholder.catalogue.ceh.ac.uk/pass")
        .size == 1
    )
  }

  test("Does the dataset contain the correct number of max hourly test fail outcomes?"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/range/hourlyTL/max")
        .filter(_.qualitative == "http://placeholder.catalogue.ceh.ac.uk/fail")
        .size == 1
    )
  }

  test("Does the dataset contain the correct number of min daily test outcomes?"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/range/dailyTL/min")
        .size == 6
    )
  }

  test("Does the dataset contain the correct number of max daily test outcomes?"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/range/dailyTL/max")
        .size == 6
    )
  }

  test("Does the dataset contain the correct number of min daily test pass outcomes?"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/range/dailyTL/min")
        .filter(_.qualitative == "http://placeholder.catalogue.ceh.ac.uk/pass")
        .size == 4
    )
  }

  test("Does the dataset contain the correct number of min daily test fail outcomes?"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/range/dailyTL/min")
        .filter(_.qualitative == "http://placeholder.catalogue.ceh.ac.uk/fail")
        .size == 2
    )
  }

  test("Does the dataset contain the correct number of max daily test pass outcomes?"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/range/dailyTL/max")
        .filter(_.qualitative == "http://placeholder.catalogue.ceh.ac.uk/pass")
        .size == 3
    )
  }

  test("Does the dataset contain the correct number of max daily test fail outcomes?"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/range/dailyTL/max")
        .filter(_.qualitative == "http://placeholder.catalogue.ceh.ac.uk/fail")
        .size == 3
    )
  }

  test("Does the dataset contain the correct number of min monthly test outcomes?"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/range/monthlyTL/min")
        .size == 32
    )
  }

  test("Does the dataset contain the correct number of max monthly test outcomes?"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/range/monthlyTL/max")
        .size == 32
    )
  }

  test("Does the dataset contain the correct number of min monthly test pass outcomes?"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/range/monthlyTL/min")
        .filter(_.qualitative == "http://placeholder.catalogue.ceh.ac.uk/pass")
        .size == 31
    )
  }

  test("Does the dataset contain the correct number of min monthly test fail outcomes?"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/range/monthlyTL/min")
        .filter(_.qualitative == "http://placeholder.catalogue.ceh.ac.uk/fail")
        .size == 1
    )
  }

  test("Does the dataset contain the correct number of max monthly test pass outcomes?"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/range/monthlyTL/max")
        .filter(_.qualitative == "http://placeholder.catalogue.ceh.ac.uk/pass")
        .size == 30
    )
  }

  test("Does the dataset contain the correct number of max monthly test fail outcomes?"){
    assert(
      dataset
        .filter(_.qualifier == "http://placeholder.catalogue.ceh.ac.uk/qc/range/monthlyTL/max")
        .filter(_.qualitative == "http://placeholder.catalogue.ceh.ac.uk/fail")
        .size == 2
    )
  }

  test("Does the dataset contain the correct quantitative values?"){
    assert(
      dataset
        .filter(_.quantitative == 9)
        .size == 1
      &&
        dataset
          .filter(_.quantitative == 5)
          .size == 3
        &&
        dataset
          .filter(_.quantitative == 1)
          .size == 4

    )
  }
}


