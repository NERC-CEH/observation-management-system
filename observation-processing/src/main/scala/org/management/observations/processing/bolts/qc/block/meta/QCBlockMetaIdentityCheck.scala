package org.management.observations.processing.bolts.qc.block.meta

// Used to connect to the registry
import com.redis.RedisClient

// The function being extended
import org.apache.flink.api.common.functions.RichFlatMapFunction

// The configuration for the Rich open() function
import org.apache.flink.configuration.Configuration

// The collector that gathers the objects to emit as a new stream
import org.apache.flink.util.Collector

// The data tuples used within the bolt
import org.management.observations.processing.tuples.{MetaDataObservation, MetaOutcomeQualitative}

/**
  * QCBlockMetaIdentityCheck
  *
  *   - For every meta-data record of this type, look up the registry
  *       to identify the feature/procedure/observedproperty UID's
  *       affected and output the update records.
  *
  *   - Each identity record means that for the record's duration, any
  *     affected observations within that duration are marked as failing
  *     the test related to the identity.
  */
class QCBlockMetaIdentityCheck extends RichFlatMapFunction[MetaDataObservation, MetaOutcomeQualitative]{

  // Create the connection to the registry
  @transient var redisCon: RedisClient = new RedisClient("localhost", 6379)

  override def open(parameters: Configuration) = {
    this.redisCon = new RedisClient("localhost", 6379)
  }

  def flatMap(in: MetaDataObservation, out: Collector[MetaOutcomeQualitative]): Unit = {

    // Retrieve the procedure/observableproperty combinations that this dataType/feature
    //  combination effect
    val affectedCombinations: Option[String] = try {
      this.redisCon.get(in.feature + "::meta::identity::" + in.dataType)
    }catch{
      case e: Exception => None
    }

    // If values were retrieved, create a list of the entries and process each
    if(affectedCombinations.isDefined){

        affectedCombinations.get.split("::").foreach(x => {
        if(x.split(",").size == 3){
          val tmpEntry: Array[String] = x.split(",")
          val feature: String = tmpEntry(0)
          val procedure: String = tmpEntry(1)
          val observableproperty: String = tmpEntry(2)

          val qualifier: String = "http://placeholder.catalogue.ceh.ac.uk/qc/meta/identity/" + in.dataType
          val qualitative: String = "fail"

          out.collect(new MetaOutcomeQualitative(
            feature,
            procedure,
            observableproperty,
            in.startTime.toLong,
            in.endTime.toLong,
            qualifier,
            qualitative
          ))
        }
      })
    }
  }
}
