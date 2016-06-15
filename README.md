# Observation Management System

## Introduction

The purpose of this project is to provide a management system that can work with observations generated by any of the CEH internal sensor networks, or other observation generating processes such as sampling, chemical and biological analysis, or model output.  The goals that fall under this main purpose include:

* storing observations with the semantic data necessary to support the OGC O&M standard
* advanced real-time quality-control (QC) checks to generate uncertainty meta-data for every observation
* real-time model execution to create derived analytical data and forecasts as the observations necessary for input arrive
* real-time alerts and warnings based on observations, model output, and forecasts reaching pre-defined criteria

This system, while designed with the O&M in mind, will not produce the functionality necessary to support SOS calls for observation data.  A catalogue will take care of that side of things, and wrap access to this system.

## Technologies

There are three main technologies this project builds upon:

* Apache Kafka
* Apache Flink
* Apache Cassandra

Kafka is the message-queue software that is used to logically store data in differing levels of processing, making it available for the next topology in the workflow.  Cassandra is the persistent storage used to store the RAW observation data and the processed data.  Flink is the processing framework that was chosen over Apache Storm and Apache Spark; this was due to the support for different windowing methods Flink supports which will be used in certain QC checks.  While at present Spark appears to have better distributed ML libraries, there are many third-party libraries that can make up this deficit.

## Types of Data

### Raw Observation Data 

Raw observation data, in the context of this system includes: sensor data, abstract procedure generated data such as chemical analysis of a sample, manual measurements and samples, and data of a similar nature.  It also includes derived observations if the creation of the observation was not carried out by this processing system.  For example, the HOBO temperature and relative humidity sensor on the Lake Observation Platforms generate observations for dew point temperature, which is derived from the sensed temperature and relative humidity observations.  As this is not generated within the management system, it is classed as raw observation data and not derived data.

### Derived Data

Derived data in this context is any observation or data generated by the management system.  This can take the form of derived observation data, such as the [thermocline depth](https://github.com/GLEON/rLakeAnalyzer/blob/master/R/thermo.depth.R) observation which is generated from input of observations sensed by the stratified PRT chain suspended below the platform.  It can also take the form of process output such as QC checks, forecasts, and the aggregation of observation data to hourly and daily mean observations.

The distinction between observation data and derived data is important in the rationale behind the persistence and backup choices on different Kafka queues.  A distinction is also made between short-lived and long-lived derived data, where short-lived data has a TTS value set and long-lived data is held indefinitely.

#### Long-Lived

Derived data products such as the hourly and daily observation aggregates, and their extended interpolated representations are examples of long-lived derived data.  These are examples of derived data which would be of use to users wishing to work on a higher temporal aggregation than the raw observations allow, or who may need a full series of observations (interpolated) rather than the original which may have missing values.  Another example of long-lived derived data is that of the QC check observations.  These observations are of interest for analysis of potential issues of a sensor, and allow users of the data to better understand the context of an observation.

#### Short-Lived

Short-lived data refers to derived data which has a short time-frame of interest, such as forecasts or certain model outputs.  For example, a forecast generated on a Monday for the following Tuesday to Friday becomes less interesting by the Saturday, and the need to keep the output past the period of interest becomes questionable when it can be reconstructed at will.  If there is any criteria or checks on the forecast, it is conceivable that these may be better to keep.  For short-lived data a TTL value is set within Cassandra.

## Data Flow

TBC.

### Semantic Annotation, Data Persist

TBC.

### QC

TBC.

### Aggregation

TBC.