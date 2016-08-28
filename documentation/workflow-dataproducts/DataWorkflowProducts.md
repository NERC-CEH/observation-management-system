# System Workflow

## Data ingestion

For the sensor networks involved in this project, the data arrives on-site from a range of different data loggers.  The data files produced by the different loggers are in relatively similar, but not the same, format.  To bridge these data files to the processing system a set of scripts micro-batch the data onto the initial queue.  From this point the data is represented with semantic information throughout the system until being made persistent in the database.

![Basic Workflow Overlay](graphics/HighLevelWorkflow.png?raw=true "High Level Dataflow")

The diagram above describes the general flow for all numerical observations, where data is placed onto the data queue, semantically annotated, processed and persisted.  The generic processing node can stand for the QC, forecasting, or any data processing.  The link back to the data queue is for derived data that is treated like a raw observation that needs to QC applied and potentially more processing.  Events are persisted along with observation data as they need to be managed and potentially mined.

With the ingestion of the data, there is a need for the loaders to keep a record of the last time stamp added, so as not to load an observation more than once.  While this wouldn't cause an issue with the end result, it would mean unnecessary extra processing.

### Automatically Downloaded Sensor Data

Sensor data that streams in from the in-situ loggers are held on-site in XML and CSV formatted files, updated throughout the day.  A script running as a service periodically searches for and processes new data automatically, providing a near-continuous stream of observations.

### Manually Downloaded Sensor Data

Sensor data that is manually downloaded from in-situ loggers is periodically added into a data folder with a relatively predictable cycle.  Similar to the automatically downloaded sensor data this can searched by a script service searching for and processing new data as it appears.

### Manually Sampled and Specimen Analysis Data

Currently manually sampled data and specimen analysis data, such as fish biological information, the chemical make-up of water samples, or plankton counts, are sporadic in processing time, and there is no single format for all data.

Unlike the sensor data, which arrives in a readable format, the sampled and specimen data can be represented as CSV, XML, or spreadsheet, and potentially other software specific formats too.  There is a need to either create an adapter for each data source, or require data producers to format the data into a standardised format.

## Numerical Observation Workflow

The basic observation workflow for numeric observations is shown below, where the rectangles represent Kafka queues, and the circles represent processing bolts.

![Basic Workflow Overlay](graphics/KafkaWorkflow.png?raw=true "Kafka Workflow")


### Initial Processing

All 

## Routing and Semantic Information

For the routing of data through the system, including the QC checks applied, real-time forecasting, real-time model data, and semantic annotation, a registry is needed.  The information held will mostly come from the catalogue or observation database, however for the high-volume of reads and necessary fast access, caching this data on a daily basis in some form of registry is the approach taken.  The type of information expected to be held includes:

* Full semantic data
    + When persisting sensor, model, forecast, or QC data, it is necessary to save the specific observation with all required semantic data.  The registry must provide this data when looked up with the site, sensor key
* QC check groups applied to sensors/procedures
    + to ensure that each sensor/procedure has the correct QC checks applied, without hard-coding the flow, the registry must provide the information to allow routing of observations through the system to reach the correct checks and queues
* Forecast ranges for sensors/procedures
    + when a sensor/procedure has a seasonal, static forecast to be compared against, the daily values need to be kept in the registry to allow fast lookup for comparison to the actual value
* Current state parameters for models
    + with the use of recursive techniques for QC, model state is necessary to be recorded between observations.  This may be handled by the registry, or it may be handled by the framework
* Model parameters
    + observations to be used as parameters in another observation's QC, forecast, or model output, need to be routed correctly and documented that they are the necessary input parameters.
    
The creation of much of the above registry will be automatically generated through inference over the catalogue or linked triple store.  The reason for this is to allow for simple inference rules to provide dynamic application of QC check techniques, and validation data from new sources as it becomes available.  For example, a query to identify all the atmospheric temperature sensors can combine with a query that identifies the checks associated with atmospheric temperature.  If one of those checks specifies that it should be applied only if solar radiation is being sensed within a one kilometre radius, the search should identify if there is such a sensor, and if so, create an entry in the registry for that check to be applied to the atmospheric data stream with the relevant solar radiation stream as an attribute to the check.

The example above highlights the reason why it is necessary to use the phenomena rather than individual sensors for rules where possible.  It provides flexibility, and the automated use of new data sources as they are added to the system.  While it is an attempt at a generic solution, it is known that in many instances such automated rules of QC and comparison will be too generic, or lacking in knowledge about outlier stations or unique within our collected observation points, and this will have to be addressed once the preliminary case-study has commenced.



The diagram above describes the general flow for all numerical observations, 

### Registry Design

TBD.

## Processing Steps

### Overall Processing Flow


### RAW Observations

Semantic stamp into Cassandra based on site:sensor lookup to catalogue for URI's.

## QC Check Observations

For QC checks that depend on other observations or meta-data that can be added later than the observations, how is this coordinated

## Interpolation of Nil Observations

## Derived Model Observations

model input parameters served/sent as JSON objects, all encoded together rather than sporadic?  Unless it's lake analyzer?


## Open Questions

* How to automate data-flow and QC checks based on phenomena rather than individual sensors.
    + this stands for both allowing new data sources to be added and have their data-flow automatically arranged, but also to know to use the data for other observations QC checks.  For instance if a source of air temperature were to be added at a site that already had air temperature recorded, the system would automatically compare the correlation and apply similar tests to the new from the existing procedure.  Such an example leads to the task of automatically determining thresholds.
* Related to the above, the combination of high and low frequency data in such a scenario to allow automated use of arbitrarily added data sources.
    + for instance, comparing the high-frequency sensor data to manually sampled observations