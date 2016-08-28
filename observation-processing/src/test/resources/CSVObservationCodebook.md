# CSV Observation Data

The test observations take the form of:

* Feature
* Procedure
* PhenomenonTimeStart (in milliseconds since origin format)
* Value

And the observations are held in separate CSV files for (nearly) each processing job tested, there is some overlap where possible.

The metadata observations take a differing form of:

* Feature 
* dataType
* startTime
* endTime
* value
* parseOK
* parseMessage

The details of this file are in conjunction with the RedisBootstrap.md which holds the necessary lookup values to deal with the observations below.

## Semantic Stamp

### RawCSVToObservation

This bolt reads in CSV tuples, checks they correspond to the expected format, inserts semantic information from the registry and extracts some from the provided data, before emitting SemanticObservation objects.  The check needs to test:

* Missing field values, e.g. no sensor, feature, time, or value.
* Missing fields or extra fields, e.g. missing or extra commas in the tuple
* Registry lookup failures
* Timestamp parse errors
* Null values

Missing field values:
```
,PRT1,1471219200000,34.3
SBAS,,1471219200000,34.3
SBAS,PRT1,,34.3
SBAS,PRT1,1471219200000,
```

Missing or extra fields:
```
SBAS,PRT1,1471219200000,34.3,123
SBAS,PRT1,1471219200000
```

Registry lookup failures:
```
SBAS,PRT123,1471219200000,34.3
NBAS,PRT1,1471219200000,34.3
```

Timestamp parse errors:
```
SBAS,PRT1,2007-01-01T00:00:00,34.3
```

Null numeric values:
```
SBAS,PRT1,1471219200000,NotAValue
SBAS,PRT1,1471219200000,NotAValue
```

OK numerical values:
```
SBAS,PRT1,1471219200000,22.45
SBAS,PRT1,1471219200000,22.41
SBAS,PRT1,1471219200000,22.42
SBAS,PRT1,1471219200000,22.43
```

OK categorical values:
```
SBAS,PRT1,1471219200000,Blue Moth
```

The above should provide the following output for SemanticStamp_CSVToRaw:

* 16 total entries
* 7 OK entries (OK and Null values)
* 6 numerical entries
* 1 categorical entry
* 1 timestamp parse error
* 2 registry lookup failures
* 6 malformed tuples (2 with extra/less columns, 4 with missing field value tuples)

### RawToSemanticObservation

This section uses the same data as the **RawCSVToObservation** section, however with different output checked for.

* 6 numerical entries
* 1 categorical entry
* year equal to 2016
* month equal to 8
* phenomenontimeend equal to phenomenontimestart

## QC Block Logic

There are five sub-sections to the testing of this job, each testing a specific bolt's functionality, or the correct routing of observations based on their value as follows.

### Time Order

Are the observations presented to the bolt in ascending time order, and if not, are they flagged as problems acccordingly.

The data used is made up of ordered and out of order data as follows:

Correct flow of data:
```
SBAS,PRT1,1471219200000,22.45
SBAS,PRT1,1471219300000,22.45
SBAS,PRT1,1471219400000,22.45
SBAS,PRT1,1471219500000,22.45
SBAS,PRT1,1471219600000,22.45
SBAS,PRT1,1471219700000,22.45
SBAS,PRT1,1471219800000,22.45
```
Following from the above data, the next three observations are in descending order, followed by an observation that arrives later than all before it, making for eight OK values and three failed values:
```
SBAS,PRT1,1471219400000,22.45
SBAS,PRT1,1471219300000,22.45
SBAS,PRT1,1471219200000,22.45
SBAS,PRT1,1471219900000,22.45
```

Output to test for:

* 11 total records
* 8 ordered OK
* 3 out of order
* 3 quantitative values signifying the positive distance (400,500,600) between the state held time position and the out of order time position
* 7 quantitative values signifying the negative distance (-100) between the state held time position and the ordered time position
* 1 quantitative value set to 0 for the first observation past through the bolt

### Time Spacing

The time spacing check involves looking up the registry to check whether the particular feature/procedure/observedproperty combination has an expected spacing attribute.  If so the current spacing is checked, if not nothing is output.

PRT1 will have eight records in total, two of which will fail the intended spacing check of four minutes (240000 milliseconds).  PRT2 should have no output.
```
SBAS,PRT1,1452427440000,22.45 
SBAS,PRT1,1452427680000,22.45

SBAS,PRT1,1452428160000,22.45

SBAS,PRT1,1452428640000,22.45
SBAS,PRT1,1452428880000,22.45
SBAS,PRT1,1452429120000,22.45
SBAS,PRT1,1452429360000,22.45
SBAS,PRT1,1452429600000,22.45
SBAS,PRT2,1452429840000,22.45

SBAS,PRT2,1452430320000,22.45
```

### Meta-Identity

To simulate default QC pass outcomes for meta-data who's presence in the datastream means a failed observation, e.g. metadata relating to a maintenance or cleaning window, we create the following entries who's features do or do not cause registry lookups to find any records.
```
SBAS,PRT1,1452427440000,22.45
SBAS,PRT1,1452427440000,22.45
NBAS,PRT1,1452427440000,22.45
```

This will create a test for:

* 6 total records
* 2 maintenance records
* 2 cleaning records
* 2 internal reset records
* No north basin records

### Meta-Value

Similar to meta-identity, except this time there are default pass outputs for minimum and maximum test thresholds, using the same CSV data as meta-identity, providing the following output:

* 4 total records
* 4 south basin records
* 0 north basin records
* 2 min battery records
* 2 max battery records

### Null Value Filtering

Checks that null values are identified, and filtered correctly using the following data:
```
SBAS,PRT1,1452427440000,22.45
SBAS,PRT1,1452427440000,NotAValue
SBAS,PRT1,1452427440000,22.45
SBAS,PRT1,1452427440000,22.45
SBAS,PRT1,1452427440000,NotAValue
SBAS,PRT1,1452427440000,NotAValue
SBAS,PRT1,1452427440000,Blue Moth
SBAS,PRT1,1452427440000,Red Moth
```

This will create a test for:

* 8 total records
* 5 pass records
* 3 fail, records with null value

## QC Block Meta

### CSV to Meta-Observation

Check that well-formed and malformed CSV entries parse correctly.

* Feature 
* dataType: the type of meta-data record, what it relates to, e.g. battery, maintenance, cleaning etc.
* startTime
* endTime
* value: only present when not an identity dataType
* parseOK
* parseMessage

The entries below test for well-formed and malformed parsing.

```
southbasin,battery,1470050400000,1470057600000,15
,battery,1470050400000,1470057600000,10.5
southbasin,,1452427440000,1470057600000,10.5
southbasin,battery,,1470057600000,10.5
southbasin,battery,1470050400000,,10.5
southbasin,battery,1470050400000,1470057600000,
southbasin,battery,1470050400000,1470057600000,10.5,10.2
southbasin,battery,1470050400000,1470057600000
southbasin,battery,1470050400000,1470057600000,10.5
southbasin,maintenance,1470050400000,1470057600000,NotAValue
southbasin,maintenance_no_affected,1470050400000,1470057600000,NotAValue
southbasin,battery,1470050400000,1470057600000,10.5
southbasin,wifi,1470050400000,1470057600000,10.5
southbasin,network,1470050400000,1470057600000,10.5
southbasin,sdcard,1470050400000,1470057600000,10.5
southbasin,cabling,1470050400000,1470057600000,10.5
```

* 16 total records
* 2 parsed identity record
* 7 parsed value records
* 7 failed to parse records

### Meta-Identity 

Using the entries from the above CSV to Meta-Observation, we would expect to see fail records for both prtone and prttwo:

* 1 QC fail for prtone, southbasin, temperature
* 1 QC fail for prttwo, southbasin, temperature

### Meta-Value

Using the entries from the above CSV to Meta-Observation, we would expect behaviour in-line with the following logic:

* battery: has two affected sets of procedures, it has single, hour, day, month min/max values in the registry
* wifi: does not have any affected sets of procedures
* network: has affected sets but no tests defined
* sdcard: has affected sets and tests, but no test type
* cabling: has all entries for a min static check

In total, battery has four types of test, all with min/max bounds, cabling has one test with only a min bound, the rest are incomplete.  This should lead to the following output:

* 2 static battery min value entries, one for each prt sensor
* 2 static battery max value entries, one for each prt sensor
* 4 hourly battery min value entries, two for each prt sensor
* 4 daily battery min value entries, two for each prt sensor
* 4 monthly battery min value entries, two for each prt sensor
* 2 static battery min value entries with quantitative value 1
* 2 static battery max value entries with quantitative value 1
* 6 non-static battery value entries with quantitative value 3.5
* 6 non-static battery value entries with quantitative value 5


## QC Block Threshold

There are three sets of bolt for this task, checking the range, delta, and sigma of individual, consecutive, and windowed observations respectively.  Each uses the same function to process the test types, and so while they all support checks that have a single, hourly, daily, and monthly resolution reference value, these were all tested for the range component, and for delta and sigma, only the single static reference value was used.

### Range

The range values were created automatically using a time increment of four hours, and a value increment of 4:

```
SBAS,PRT1,1470056400000,1
SBAS,PRT1,1470070800000,5
SBAS,PRT1,1470085200000,9
SBAS,PRT1,1470099600000,13
SBAS,PRT1,1470114000000,17
SBAS,PRT1,1470128400000,31
SBAS,PRT1,1470142800000,25
SBAS,PRT1,1470157200000,29
SBAS,PRT1,1470171600000,33
SBAS,PRT1,1470186000000,37
SBAS,PRT1,1470200400000,41
SBAS,PRT1,1470214800000,45
SBAS,PRT1,1470229200000,49
SBAS,PRT1,1470243600000,53
SBAS,PRT1,1470258000000,57
SBAS,PRT1,1470272400000,61
SBAS,PRT1,1470286800000,65
SBAS,PRT1,1470301200000,69
SBAS,PRT1,1470315600000,73
SBAS,PRT1,1470330000000,77
SBAS,PRT1,1470344400000,81
SBAS,PRT1,1470358800000,85
SBAS,PRT1,1470373200000,89
SBAS,PRT1,1470387600000,93
SBAS,PRT1,1470402000000,97
SBAS,PRT1,1470416400000,101
SBAS,PRT1,1470430800000,105
SBAS,PRT1,1470445200000,109
SBAS,PRT1,1470459600000,113
SBAS,PRT1,1470474000000,117
SBAS,PRT1,1470488400000,121
SBAS,PRT1,1470502800000,125
```

Combined with the lookup values in the registry the following outcomes should be observed:

* 142 output values
* 32 static min outcomes
* 32 static max outcomes
* 29 static min pass outcomes
* 3 static min fail outcomes
* 30 static max pass outcomes
* 2 static max fail outcomes
* 1 hourly min outcome, pass
* 1 hourly max outcome, fail
* 6 daily min outcomes
* 6 daily max outcomes
* 4 daily min pass outcomes
* 2 daily min fail outcomes
* 3 daily max pass outcomes
* 3 daily max fail outcomes
* 32 monthly min outcomes
* 32 monthly max outcomes
* 31 monthly min pass outcomes
* 1 monthly min fail outcome
* 30 monthly max pass outcomes
* 2 monthly max fail outcome
* 1 quantitative value of value 9
* 3 quantitative outcomes of value 5
* 4 quantitative outcomes of 1


### Delta

Delta includes step and spike tests, which behave differently regarding the number of outcomes generated.  Step tests generate pass or fail for both observations in the comparison, however, the second of the observations becomes the first observation in the next check, and so can have a pass value generated for it.  The expected behaviour is that a genuine step is located by the initial of the two observations being flagged.

For the spike test, only the middle observation causing the spike has an outcome generated, and so each observation only has one outcome generated.

For both step and spike, the following data was used, in conjunction with a static single min/max reference set.

```
SBAS,PRT1,1470056400000,1
SBAS,PRT1,1470070800000,2
SBAS,PRT1,1470085200000,3
SBAS,PRT1,1470099600000,14
SBAS,PRT1,1470114000000,5
SBAS,PRT1,1470128400000,6
SBAS,PRT1,1470142800000,7
SBAS,PRT1,1470157200000,8
SBAS,PRT1,1470171600000,9
SBAS,PRT1,1470186000000,20
SBAS,PRT1,1470200400000,11
SBAS,PRT1,1470214800000,12
SBAS,PRT1,1470229200000,13
SBAS,PRT1,1470243600000,14
SBAS,PRT1,1470258000000,15
SBAS,PRT1,1470272400000,26
SBAS,PRT1,1470286800000,17
SBAS,PRT1,1470301200000,25
SBAS,PRT1,1470315600000,19
SBAS,PRT1,1470330000000,20
SBAS,PRT1,1470344400000,21
SBAS,PRT1,1470358800000,50
SBAS,PRT1,1470373200000,23
SBAS,PRT1,1470387600000,32
SBAS,PRT1,1470402000000,25
SBAS,PRT1,1470416400000,26
SBAS,PRT1,1470430800000,27
SBAS,PRT1,1470445200000,28
SBAS,PRT1,1470459600000,29
SBAS,PRT1,1470474000000,100
SBAS,PRT1,1470488400000,31
SBAS,PRT1,1470502800000,32
```

* 184 total outcomes

Spike based outcomes:

* 30 min outcomes
* 30 max outcomes
* 30 min pass outcomes
* 21 max pass outcomes
* 9 max fail outcomes
* 1 max fail outcomes with quantitative value 7
* 1 max fail outcomes with quantitative value 9
* 4 max fail outcomes with quantitative value 13
* 1 max fail outcomes with quantitative value 49
* 1 max fail outcomes with quantitative value 133

Step based outcomes:

* 62 min outcomes
* 62 max outcomes
* 62 min pass outcomes
* 48 max pass outcomes
* 14 max fail outcomes
* 6 max fail outcomes with quantitative value 1
* 2 max fail outcomes with quantitative value 17
* 2 max fail outcomes with quantitative value 19
* 2 max fail outcomes with quantitative value 59
* 2 max fail outcomes with quantitative value 61


### Sigma


For both step and spike, the following data was used, in conjunction with a static single min/max reference set.

```
SBAS,PRT1,1470056400000,1
SBAS,PRT1,1470070800000,2
SBAS,PRT1,1470085200000,3
SBAS,PRT1,1470099600000,4
SBAS,PRT1,1470114000000,5
SBAS,PRT1,1470128400000,6
SBAS,PRT1,1470142800000,7
SBAS,PRT1,1470157200000,8
SBAS,PRT1,1470171600000,9
SBAS,PRT1,1470186000000,10
SBAS,PRT1,1470200400000,11
SBAS,PRT1,1470214800000,12
SBAS,PRT1,1470229200000,13
SBAS,PRT1,1470243600000,14
SBAS,PRT1,1470258000000,15
SBAS,PRT1,1470272400000,16
SBAS,PRT1,1470286800000,17
SBAS,PRT1,1470301200000,18
SBAS,PRT1,1470315600000,19
SBAS,PRT1,1470330000000,20
SBAS,PRT1,1470344400000,21
SBAS,PRT1,1470358800000,22
SBAS,PRT1,1470373200000,23
SBAS,PRT1,1470387600000,24
SBAS,PRT1,1470402000000,25
SBAS,PRT1,1470416400000,26
SBAS,PRT1,1470430800000,27
SBAS,PRT1,1470445200000,28
SBAS,PRT1,1470459600000,29
SBAS,PRT1,1470474000000,30
SBAS,PRT1,1470488400000,31
SBAS,PRT1,1470502800000,32
```

* 192 outcomes
* 32 daily min outcomes
* 32 daily max outcomes
* 32 half day min outcomes
* 32 half day max outcomes
* 32 one hour min outcomes
* 32 one hour max outcomes
* 29 daily min pass outcomes
* 3 daily min fail outcomes
* 32 daily max pass outcomes
* 30 half day min pass outcomes
* 2 half day min fail outcomes
* 32 half day max pass outcomes
* 32 one hour min pass outcomes
* 32 one hour max pass outcomes




TODO: Passing quantitative tests, only testing the fails currently.