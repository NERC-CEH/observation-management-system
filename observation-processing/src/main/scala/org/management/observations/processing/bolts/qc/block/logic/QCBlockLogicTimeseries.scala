package org.management.observations.processing.bolts.qc.block.logic

// Used for connecting to the Redis registry
import com.redis.RedisClient
import org.apache.flink.api.java.utils.ParameterTool

// Allows the use of the state variables
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}

// Used as base class to represent the window key and the state variable
import org.apache.flink.api.java.tuple.Tuple

// Used for passing parameters to the open() function
import org.apache.flink.configuration.Configuration

// The function being extended
import org.apache.flink.streaming.api.scala.function.RichWindowFunction

// The meta-data holder for the window information
import org.apache.flink.streaming.api.windowing.windows.GlobalWindow

// The collector for objects to return into the datastream
import org.apache.flink.util.Collector

// The tuples used within this bolt
import org.management.observations.processing.tuples.{QCOutcomeQuantitative, SemanticObservationFlow, SemanticObservation}

// Used for returning the absolute value
import java.lang.Math.abs

// System KVP properties
import org.management.observations.processing.ProjectConfiguration
import scala.collection.JavaConversions._

/**
  * QCBlockLogicTimeseries
  *
  *   - Checks that the observations are correctly in ascending time order,
  *     creates a QCOutcomeQuantitative object to register the pass or fail.
  *
  *   - Checks that the gap between observations is not greater than the
  *     expected amount, creates a QCOutcomeQuantitative object to register
  *     the pass or fail.  If an intended spacing entry is not held in the
  *     registry, do not emit, but do update the state.
  *
  *   - Window function used over RichFlatMap for state/keyed stream, documentation
  *     gives conflicting advice - first says that state only works on keyed
  *     streams, while the docs say FlatMap does not take a keyed stream input,
  *     then gives an example of state within a FlatMap.
  */
class QCBlockLogicTimeseries extends RichWindowFunction[SemanticObservation, QCOutcomeQuantitative, Tuple, GlobalWindow] with SemanticObservationFlow {

  // Register the state and redis connection variables, where
  // the state variable holds only the last observation's timestamp
  var state: ValueState[Tuple1[Long]] = null

  @transient var params: ParameterTool = ParameterTool.fromMap(mapAsJavaMap(ProjectConfiguration.configMap))
  @transient var redisCon: RedisClient = new RedisClient(params.get("redis-conn-ip"),params.get("redis-conn-port").toInt)


  override def open(parameters: Configuration) = {
    this.params = ParameterTool.fromMap(mapAsJavaMap(ProjectConfiguration.configMap))
    this.redisCon = new RedisClient(params.get("redis-conn-ip"),params.get("redis-conn-port").toInt)

    state = getRuntimeContext.getState(new ValueStateDescriptor("QCTimestampVerification",classOf[Tuple1[Long]],null))
  }

  def apply(key: Tuple, window: GlobalWindow, input: Iterable[SemanticObservation], out: Collector[QCOutcomeQuantitative]): Unit = {

    // Set  QC identifiers
    val orderCheck: String = params.get("qc-logic-order")
    val spacingCheck: String = params.get("qc-logic-spacing")

    // Check if intendedspacing is held in the registry
    val spacingLookup: Option[String] = try {
      this.redisCon.get(input.head.feature + "::" + input.head.procedure + "::" + input.head.observableproperty + "::intendedspacing")
    }catch {
      case e: Exception => None
    }

    // Check the current timestamp is greater than the previous
    val inOrder: Boolean = {
      if(state.value() == null)
        true
      else if((input.head.phenomenontimestart - state.value()._1) > 0)
        true
      else
        false
    }

    // Calculate the difference between the current and previous timestamps
    val orderDifference: Long = {
      if (state.value() == null)
        0
      else
        abs(state.value()._1 - input.head.phenomenontimestart)
    }

    /**
      * Output the observation order outcome, change
      * the orderDifference to negative if a pass
      */
    if(inOrder) {
      out.collect(createQCOutcomeQuantitative(input.head,
        orderCheck,
        params.get("qc-outcome-pass"),
        -orderDifference
      ))
    }else {
      out.collect(createQCOutcomeQuantitative(input.head,
        orderCheck,
        params.get("qc-outcome-fail"),
        orderDifference
      ))
    }

    /**
      * We use the absolute order difference as the inOrder
      * check provides an out of order warning, the intended
      * spacing is purely for distance between observations.
      */
    if(spacingLookup.isDefined) {
      if(state.value() == null) {
        out.collect(createQCOutcomeQuantitative(input.head,
          spacingCheck,
          params.get("qc-outcome-pass"),
          0
        ))
      }else if(orderDifference <= spacingLookup.get.toLong) {
          out.collect(createQCOutcomeQuantitative(input.head,
            spacingCheck,
            params.get("qc-outcome-pass"),
            -orderDifference
          ))
      }else {
        out.collect(createQCOutcomeQuantitative(input.head,
          spacingCheck,
          params.get("qc-outcome-fail"),
          orderDifference
        ))
      }
    }

    /**
      * If the observations were in order, update the state to
      * the new timestamp
      */
    if(inOrder)
        state.update(Tuple1(input.head.phenomenontimestart))
  }
}