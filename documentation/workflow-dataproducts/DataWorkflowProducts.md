# System Workflow

## Data ingestion

For the sensor networks involved in this project, the data arrives on-site from a range of different data loggers.  The data files produced by the different loggers are in relatively similar, but not the same, format.  To bridge these data files to the processing system a set of scripts micro-batch the data onto the initial queue.  From this point the data is represented in the same way throughout the system until being made persistent in the database.

![Basic Workflow Overlay](graphics/HighLevelWorkflow.png?raw=true "High Level Dataflow")

As shown in the above diagram, before persistence to the database, all data goes through the 'Semantic Annotate' node.  So as not to send data unnecessarily through the system, only the most basic information to identify each tuple is used in the processing stages.  However when it comes to persisting the tuple in the database, more than the basic information is necessary and this is provided by the annotation node.

### Manually Downloaded Sensor Data

TBD.

### Manually Sampled Data

TBD.

### Specimen Analysis Data

TBD.

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


### Registry Design

TBD.

## Processing Steps

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