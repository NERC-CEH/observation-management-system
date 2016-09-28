// Unit testing libraries
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer09
import org.apache.flink.streaming.util.serialization.SimpleStringSchema
import org.scalatest.FunSuite
import org.junit.runner.RunWith
import org.management.observations.processing.ProjectConfiguration
import org.management.observations.processing.bolts.derived.LakeAnalyzer
import org.management.observations.processing.bolts.qc.block.threshold.{QCBlockThresholdDeltaSpikeCheck, QCBlockThresholdDeltaStepCheck, QCBlockThresholdRangeCheck, QCBlockThresholdSigmaCheck}
import org.management.observations.processing.bolts.routing.InjectRoutingInfo
import org.management.observations.processing.timing.LakeAnalyzerEventTime
import org.management.observations.processing.tuples.{BasicNumericObservation, RoutedObservation}
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
class Derived_LakeAnalyzer_HighRes extends FunSuite {

  def runCode: Iterator[String] = {
    val env = StreamExecutionEnvironment.createLocalEnvironment(1)
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
    env.setParallelism(1)

    // Read the parameter configuration file
    val params: ParameterTool = ParameterTool.fromMap(mapAsJavaMap(ProjectConfiguration.configMap))

    // Read in the test data
    val observationStream: DataStream[RoutedObservation] = env.fromCollection(
      fromFile("/home/dciar86/GitHub/observation-management-system/observation-processing/src/test/resources/CSVObservations_Derived_LakeAnalyzer_HighRes.csv")
        .getLines().toSeq
    )
      .map(new RawCSVToObservation)
      .filter(_.parseOK == true)
      .map(new RawToSemanticObservation)
      .flatMap(new InjectRoutingInfo)


    val laHighTupleStream: DataStream[BasicNumericObservation] = observationStream
      .filter(_.routes.map(_.model).contains(params.get("routing-derived-lake-analyzer-high-res")))
      .map(x => x.observation
      )
      .assignTimestampsAndWatermarks(new LakeAnalyzerEventTime)


    val laDerivedStream: DataStream[String] = laHighTupleStream
      .keyBy("routingkey")
      .window(TumblingEventTimeWindows.of(Time.hours(2)))
      .apply(new LakeAnalyzer())

    // Collect and return the program output
    val rangeStreamOutput: Iterator[String] = DataStreamUtils.collect(laDerivedStream.javaStream).asScala
    rangeStreamOutput
  }

  // Run Flink code and create a sequence of returned objects
  val obs = runCode
  val dataset = obs.toIndexedSeq

  test("Is the dataset the correct size?") {
    assert(dataset.size == 10)
  }

  test("Are the buoyancy frequency values correct?"){
    assert(
      dataset
        .map(x => x.split(","))
        .filter(x => x(1) == "BUOYFREQ" )
        .filter(x => x(3).toDouble == 0.001384711 || x(3).toDouble == 0.001400761)
        .size == 2
    )
  }

  test("Are the centre buoyancy values correct?"){
    assert(
      dataset
        .map(x => x.split(","))
        .filter(x => x(1) == "CENTREBUOY" )
        .filter(x => x(3).toDouble == 9.499677 || x(3).toDouble == 9.532922)
        .size == 2
    )
  }

  test("Are the bottom metadepth values correct?"){
    assert(
      dataset
        .map(x => x.split(","))
        .filter(x => x(1) == "METADEPTHBOTTOM" )
        .filter(x => x(3).toDouble == 6.802273 || x(3).toDouble == 6.805234)
        .size == 2
    )
  }

  test("Are the top metadepth values correct?"){
    assert(
      dataset
        .map(x => x.split(","))
        .filter(x => x(1) == "METADEPTHTOP" )
        .filter(x => x(3).toDouble == 5.52431 || x(3).toDouble == 5.48905)
        .size == 2
    )
  }

  test("Are the thermocline depth values correct?"){
    assert(
      dataset
        .map(x => x.split(","))
        .filter(x => x(1) == "THERMODEPTH" )
        .filter(x => x(3).toDouble == 5.5)
        .size == 2
    )
  }
}