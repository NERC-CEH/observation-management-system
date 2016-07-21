# Observation Quality Control

With the volume of data being collected by both sensors and manual sampling, automated quality control (QC) is increasingly necessary to highlight potential issues with the data.  By automating checks it allows for more targeted analysis by data managers, with less time spent eyeballing plots searching for issues.  As well as the automated QC, there is the generation of data-products as a result of the QC process, however this section focuses solely on the tests that can be used for judging data quality and the associated meta-data to represent the test outcomes.

## Flag Systems

Starting with the literature, below are two examples of suggested flag schemes to use when performing QC checks, that indicate the state of the observation.  The first is taken from Campbell et al. (2013), providing both internal and external flags.  

* Internal Flags
    + Missing Value
    + Low Battery
    + Calibration Due
    + Calibration Expired
    + Invalid Chronology
    + Persistent Value
    + Above Range
    + Below Range
    + Slope Exceedance
    + Spatial Inconsistency
    + Internal Inconsistency
    + Detection Limit
* External Flags
    + Pass
    + Estimated
    + Missing
    + Uncertainty

Each internal flag above explicitly describes the check that has failed, and it is assumed that the system follows a specific order of QC checks, and as such when one fails, the rest are not performed so that the flag indicates the issue and position in the QC flow where the observation failed.  Another possibility is that the flags have a hierarchical ranking, and so the flag with the highest ranking is the one displayed in the case of multiple failures.  This approach is not desired, as it is trying to fit too much information into a single variable.  A preferred approach is to store the pass/fail value for each individual check, and to use a value from the external list to summarise the overall flag.  A system like this is provided by Morello et al. (2014):

* 0: No QC performed
* 1: Good data
* 2: Probably good data
* 3: Bad data that are potentially correctable
* 4: Bad data
* 5: Value changed
* 6: Below detection limit
* 7: In excess of quoted value
* 8: Interpolated value
* 9: Missing value
* A: Incomplete information

This provides a higher-level of flag than the individual check type of Campbell et al. (2013), providing more information than the basic external flags, including granular confidence values.  It is also closer to the WaterML2 TimeSeries set of recommended flag values of:

* Good
* Suspect
* Estimate
* Poor
* Unchecked
* Missing

The Morello et al. (2014) system would appear to be the optimal solution for an overall flag, and mapping between it and the WaterML2 set would be straightforward if using that data representation.  One aspect that WaterML2 makes provision for is censuring observation values, however as there is no corresponding flag for this it is assumed that the flags 'Poor' or 'Suspect' are used for this case, e.g. a 'Suspect' observation has been censured.

As well as an overall flag value, each observation will have multiple individual QC check flags, who's values would be either: a boolean pass/fail value recorded, a categorical value referenced in a vocabulary under the QC check, or a distance measure between the observed value and a reference value when using data-driven or prediction based checks.

The determination of the overall flag value is interesting problem, as there have recently been machine learning and fuzzy logic examples of this ultimate decision, and it is one that would hopefully use such techniques, rather than inflexible static rules.  An example of not using static rules would be if a set of observations are recorded when the battery voltage is lower than can be guaranteed to generate correct observations, but the signal behaves as expected based on observations before and after the low battery period, based on forecasts and expected behaviour, and show no other issues, then these could be marked as 'good' rather than say 'suspect'.

## Database Representation

Within the database, the [representation](../observation-sensor-representation/OGCStandardsBasedDesign.md) of individual QC checks includes columns for both a quantitative and qualitative value, and a comment on the particular check.  By storing a comment for every test, it becomes possible to qualify tests that are dependent on data who's arrival can be delayed by weeks, as well as providing a space for comments left by the techniques used, or to be overwritten by a human operator.

Regarding checks that depend on late arriving data, such as whether a sensor was undergoing maintenance or was not cleaned for a period, to create a complete QC record, these checks could have the qualitative value of 'pass', and a comment such as 'Default: No Information'.  This could also be used for the battery voltage check, as there can be a lag between observations being generated and the hourly battery summary.

The qualitative and quantitative columns allow for a richer recording of QC check outcomes.  For example, the qualitative value allows recording the individual 'pass' or 'fail' status of each check, allowing for finer grained later analysis.  The quantitative column allows for a distance metric to be recorded for checks where there are boundary, optimal, or acceptable regions of value.  For instance, if a check has a boundary threshold which an observations exceeds, the distance between the threshold and the observation can be recorded.  For observations that pass, the distance within the threshold is also recorded, as this data makes for an interesting feature, and provides the potential for later QC checks to use these features for more informed analysis.


## Standard QC Checks

A brief list of the standard QC checks found in a variety of the literature is found below.

QC Type | Description | Found In
------- | ----------- | ---------
**Range Check** | A minimum and maximum value boundary, whether a global range of the most extreme values realistically possible, or more data-driven methods to provide regional or seasonal bound estimates.  | Cabanes et al. (2013), Campbell et al. (2013), Estevez et al. (2011), Fiebrich et al. (2010), Fredericks et al. (2009), Hasu & Aaltonen (2011), Taylor & Loescher (2013)
**Variance Check** | A minimum and maximum value boundary for variance over differing length observation windows.  Also known as the **Stationary** or **Persistence** check. | Cabanes et al. (2013), Campbell et al. (2013), Estevez et al. (2011), Fiebrich et al. (2010), Fredericks et al. (2009), Morello et al. (2014), Taylor and Loescher (2013)
**Step Check** or **Acceleration Check** |  A minimum and maximum value boundary for the difference between successive data points, seen as a type of variance check that is described separately in the literature.  The acceleration check is a similar test, only applied to a larger window of successive data points.  This check needs to be performed over a range of gap sizes, starting with successive points, then growing to a half-hour step, hour, two hours, four hours etc. | Cabanes et al. (2013), Campbell et al. (2013), Estevez et al. (2011), Fiebrich et al. (2010), Fredericks et al. (2009), Taylor and Loescher (2013)
**Spike Check** | With a window size of three observations, check that the middle observation is not greatly different from the surrounding observations.  It was noted in the literature that spikes are a better check than steps. | Morello et al. (2014)
**Null and Gap Check** | Rather than only flag every null value, identify the amount of null values that are expected within a windowed time-frame and use this to drive a maximum limit, and do a similar task for contiguous null values. | Fredericks et al. (2009), Taylor & Loescher (2013)
**Mean Shift Check** | Testing for a shift in the mean value over time, also known in other literature as change point detection. | Fredericks et al. (2009)
**Internal/External Consistency Check** | A large range of simple tests (not model driven) that can be defined as checking the behaviour based on expected behaviour. | Boden et al. (2013), Campbell et al. (2013), Estevez et al. (2011), Fiebrich et al. (2010), Morello et al. (2014), Taylor & Loescher (2013)

The contents of the table above will be explored in more detail below, paper by paper, summarising the approaches described when not having been covered by a preceding paper.  The first of these subsections however will describe the existing checks that were already implemented in the original system.  These include range and internal consistency based checks.

### Existing Checks

#### Static Min/Max Range

Every sensor has a minimum and maximum value that is static throughout the year, year on year, and provides a straightforward range check for the more extreme values at either end of the sensor's recording range.  The values for this check come from the catalogue's meta-data about the particular sensor.

#### Maintenance Period

When maintenance is carried out on a sensor platform, the temporal period is recorded and any observations that fall within the maintenance period are marked as such.  This check is executed after maintenance is carried out, as it is not possible ahead of time to know exactly when work will be carried out to the minute.  This makes cause to reprocess observations when maintenance periods are updated.

#### Missed Cleaning Period

Related to the above maintenance period, if due to circumstances it is not possible to carry out routine scheduled maintenance tasks such as cleaning optical sensors, the time between the missed maintenance period and the next successful one is recorded.  Any observations generated by sensors that require cleaning are marked as suspect due to this.  Similar to the maintenance period, some observations would need to be reprocessed depending on when the missed cleaning period was identified and added to the system.

#### Low Battery

There is a set voltage threshold that when readings drop below it, any observations generated by sensors connected to the system become suspect.

#### Missing/NA/Null Value

If the value recorded is missing/NA/Null, flag as an issue.

### The AmeriFlux data activity and data system: an evolving collection of data management techniques, tools, products and services (Boden et al. 2013)

#### Internal Consistency

* Nighttime radiation
    + Identify sunrise and sunset times (Weather API), and any values greater than a set threshold, flag.  Q: would moon cycles need to also be tracked, how sensitive are the instrumentation?
* Diurnal and seasonal cycles
    + Similar to nighttime radiation in that known behaviour is expected, compare nightly, weekly, monthly diurnal cycles with correlation between the given time period and mean cycles from previous years.
* Biological and meteorological inter-relationship
    + Similar measurements should be correlated, such as air temperatures connected to the same instrument platform, while some biological signals are dependent on certain physical phenomena.  For example, large changes in pH and an end to stratification can correlate with wind speed and other inputs.
* Discontinuity and inter-annual variation
    + checking for changes in trend between time spans looking for evidence of drift.

#### External Consistency

* Sites within 50 km distance and 100 m height delta with similar observations are used for correlation and comparison checks.

### Guidelines on validation procedures for meteorological data from automatic weather stations (Estevez et al. 2011)

Provides a number of checks aggregated from preceding literature, focusing on range, step, persistence, internal and spatial consistency checks.  The checks are a mixed set of explicit reference values such as temperature delta not being greater than four degrees in a half hour, or the air temperature not exceeding 50 degrees Celsius.  A large number are logic checks, such as the current observation cannot be greater than the highest observation in the current thirty minute window.


### Automated quality control methods for sensor data: a novel observatory approach (Taylor and Loescher 2013)

Providing range, sigma, delta, step, null, and gap tests, they discuss a data-driven method to infer realistic bounds from historical data.  At first sampling of data characteristics is discussed, such as sampling the minimum values for a given sensor over a window of N observations, before being replaced by an exponentially weighted moving average.

This appears to be an extension of the work in Hasu & Aaltonen (2011).


## Overall Flag Allocation

Currently there has been a lot of research into applying machine learning (ML) techniques such as clustering, classification, and fuzzy logic to the task of overall observation QC flag assignment.  The general approach has been to use the output from QC checks, whether binary, categorical, or distance based, as the features in ML techniques (Rahman et al. 2014).  With the WaterML2 specification providing both a qualitative and quantitative overall quality flag, it seems an approach that will be embraced in the future.


# References

Boden, T.A., Krassovski, M. and Yang, B., 2013. The AmeriFlux data activity and data system: an evolving collection of data management techniques, tools, products and services. Geoscientific Instrumentation, Methods and Data Systems, 2(1), pp.165-176.

Cabanes, C., Grouazel, A., Schuckmann, K.V., Hamon, M., Turpin, V., Coatanoan, C., Paris, F., Guinehut, S., Boone, C., Ferry, N. and Boyer Montegut, C.D., 2013. The CORA dataset: validation and diagnostics of in-situ ocean temperature and salinity measurements. Ocean Science, 9(1), pp.1-18.

Campbell, J.L., Rustad, L.E., Porter, J.H., Taylor, J.R., Dereszynski, E.W., Shanley, J.B., Gries, C., Henshaw, D.L., Martin, M.E., Sheldon, W.M. and Boose, E.R., 2013. Quantity is nothing without quality: Automated QA/QC for streaming environmental sensor data. BioScience, 63(7), pp.574-585.

Estevez, J., Gavilan, P. and Giraldez, J.V., 2011. Guidelines on validation procedures for meteorological data from automatic weather stations. Journal of Hydrology, 402(1), pp.144-154.

Fiebrich, C.A., Morgan, C.R., McCombs, A.G., Hall Jr, P.K. and McPherson, R.A., 2010. Quality assurance procedures for mesoscale meteorological data. Journal of Atmospheric and Oceanic Technology, 27(10), pp.1565-1582.

Fredericks, J., Botts, M., Bermudez, L., Bosch, J., Bogden, P., Bridger, E., Cook, T., Delory, E., Graybeal, J., Haines, S. and Holford, A., 2009. Integrating Quality Assurance and Quality Control into Open GeoSpatial Consortion Sensor Web Enablement. Proceedings of OceanObs, 9.

Hasu, V. and Aaltonen, A., 2011. Automatic minimum and maximum alarm thresholds for quality control. Journal of Atmospheric and Oceanic Technology, 28(1), pp.74-84.

Morello, E.B., Galibert, G., Smith, D., Ridgway, K.R., Howell, B., Slawinski, D., Timms, G.P., Evans, K. and Lynch, T.P., 2014. Quality control (QC) procedures for Australia's National Reference Station's sensor data-comparing semi-autonomous systems to an expert oceanographer. Methods in Oceanography, 9, pp.17-33.

Rahman, A., Shahriar, M.S., D’Este, C., Smith, G., McCulloch, J. and Timms, G., 2014. Time-series prediction of shellfish farm closure: A comparison of alternatives. Information Processing in Agriculture, 1(1), pp.42-50.

Rahman, A., Smith, D.V. and Timms, G., 2014. A novel machine learning approach toward quality assessment of sensor data. IEEE Sensors Journal, 14(4), pp.1035-1047.

Taylor, J.R. and Loescher, H.L., 2013. Automated quality control methods for sensor data: a novel observatory approach. Biogeosciences, 10(7), pp.4957-4971.