# System Workflow

## Data Ingestion

For the sensor networks involved in this project, the data arrives on-site from a range of different data loggers.  The data files produced by the different loggers are in relatively similar, but not the same, format.  To bridge these data files to the processing system Apache Nifi is used to place the data onto the initial queue as and when it becomes available.  From this point the data is represented with semantic information throughout the system until being made persistent in the database.

![Basic Workflow Overlay](graphics/HighLevelWorkflow.png?raw=true "High Level Dataflow")

The diagram above describes the general flow for all numerical observations, where data is placed onto the data queue, semantically annotated, processed and persisted.  The generic processing node can stand for the QC, forecasting, or any data processing.  The link back to the data queue is for derived data that is treated like a raw observation that needs to have QC applied and potentially more processing.  Events are persisted along with observation data as they need to be managed and potentially mined.

### Automatically Downloaded Sensor Data

Sensor data that streams in from the in-situ loggers are held on-site in XML and CSV formatted files, updated throughout the day.  

### Manually Downloaded Sensor Data

Sensor data that is manually downloaded from in-situ loggers is periodically added into a data folder with a relatively predictable cycle.

### Manually Sampled and Specimen Analysis Data

Currently manually sampled data and specimen analysis data, such as fish biological information, the chemical make-up of water samples, or plankton counts, are sporadic in processing time, and there is no single format for all data.

Unlike the sensor data, which arrives in a readable format, the sampled and specimen data can be represented as CSV, XML, or spreadsheet, and potentially other software specific formats too.  There is a need to either create an adapter for each data source, or require data producers to format the data into a standardised format.

## Numerical Observation Workflow

The basic observation workflow for numeric observations is shown below, where the rectangles represent Kafka queues, and the circles represent processing bolts.

![Basic Workflow Overlay](graphics/KafkaWorkflow.png?raw=true "Kafka Workflow")


### Initial Processing

For numeric observations, the data is first loaded into the `Semantic Stamp` node, which checks for correct formatting, and a corresponding entry in the registry for the observation.  If either of these fail, it is placed on the malformed queue, else it is enriched with the metadata from the registry and persisted to the datastore.  It is also placed onto the queue for the `QC Block Logic` node, which is used to check observations appear in the correct temporal order, that the temporal gap between observations is as expected, and that they are not null.  Following this, observations are placed onto the queue for the node that routes them to the nodes suited to the type of observation.

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

## Open Questions

* How to combine high-frequency and low-frequency data together for validation, model use.
    + for example, fortnightly lake temperature readings validated against high-frequency automated 4 minute resolution readings.