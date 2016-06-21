# Observation Quality Control

With the volume of data being collected by both sensors and manual sampling, automated quality control (QC) is increasingly necessary to highlight potential issues with the data.  By automating the checks it allows for more targeted checking of suspect data, with less time spent eyeballing data plots searching for issues.  As well as the automated QC, there is the generation of data-products as a result of the QC process, however this section focuses solely on the tests that can be used for judging data quality and the associated meta-data to represent the test outcomes.

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

This provides a higher-level of flag than the individual check type of Campbell et al. (2013), while still providing more information than the basic external flags.  It is also closer to the WaterML2 TimeSeries set of recommended flag values of:

* Good
* Suspect
* Estimate
* Poor
* Unchecked
* Missing

The combination of these latter two sets would appear to be the optimal solution, with each individual QC check having one of the following: a boolean pass/fail value recorded, a categorical value referenced in a vocabulary under the QC check, or a distance measure between the observed value and a reference value when using data-driven or prediction based checks.  The interesting part of the overall flag is the determination of its value, as there are machine learning and fuzzy logic examples of this ultimate decision.  Of passing note is that WaterML2 makes provision for censuring observation values but does not provide a flag for this, so it is assumed that the flags 'Poor' or 'Suspect' are used in this case, e.g. a 'Suspect' observation has been censured.

## Standard QC Checks

QC Type | Description | Found In
------- | ----------- | ---------
**Range Check** | A minimum and maximum value boundary, whether a global range of the most extreme values realistically possible, or more data-driven methods to provide regional or seasonal bound estimates.  | Cabanes et al. (2013), Campbell et al. (2013), Estevez et al. (2011), Fiebrich et al. (2010), Fredericks et al. (2009), Hasu & Aaltonen (2011), Taylor & Loescher (2013)
**Variance Check** | A minimum and maximum value boundary for variance over differing length observation windows.  Also known as the **Stationary** or **Persistence** check. | Cabanes et al. (2013), Campbell et al. (2013), Estevez et al. (2011), Fiebrich et al. (2010), Fredericks et al. (2009), Morello et al. (2014), Taylor and Loescher (2013)
**Step Check** or **Acceleration Check** |  A minimum and maximum value boundary for the difference between successive data points, seen as a type of variance check that is described separately in the literature.  The acceleration check is a similar test, only applied to a larger window of successive data points.  | Cabanes et al. (2013), Campbell et al. (2013), Estevez et al. (2011), Fiebrich et al. (2010), Fredericks et al. (2009), Taylor and Loescher (2013)
**Spike Check** | With a window size of three observations, check that the middle observation is not greatly different from the surrounding observations.  It was noted in the literature that spikes are a better check than steps. | Morello et al. (2014)
**Null and Gap Check** | Rather than only flag every null value, identify the amount of null values that are expected within a windowed time-frame and use this to drive a maximum limit, and do a similar task for contiguous null values. | Fredericks et al. (2009), Taylor & Loescher (2013)
**Mean Shift Check** | Testing for a shift in the mean value over time, also known in other literature as change point detection. | Fredericks et al. (2009)
**Internal/External Consistency Check** | A large range of simple tests (not model driven) that can be defined as checking the behaviour based on expected behavior. | Boden et al. (2013), Campbell et al. (2013), Estevez et al. (2011), Fiebrich et al. (2010), Morello et al. (2014), Taylor & Loescher (2013)

The contents of the table above will be explored in more detail below, paper by paper, summarising the approaches described when not having been covered by a preceeding paper.  The first of these subsections hwoever will describe the existing checks that were already implemented in the original system.  These include range and internal consistency based checks.

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

If The value recorded is missing/NA/Null, flag as an issue.

## Overall Flag Allocation

Currently there has been a lot of research into applying machine learning (ML) techniques such as clustering, classification, and fuzzy logic to the task of overall observation QC flag assignment.  The general approach has been to use the output from QC checks, whether binary, categorical, or distance based, as the features in ML techniques.  With the WaterML2 specification providing both a qualitative overall quality flag, and an quantitative overall accuraccy flag, both of these would appear to be best set using these new techniques.


# References

Cabanes, C., Grouazel, A., Schuckmann, K.V., Hamon, M., Turpin, V., Coatanoan, C., Paris, F., Guinehut, S., Boone, C., Ferry, N. and Boyer Montégut, C.D., 2013. The CORA dataset: validation and diagnostics of in-situ ocean temperature and salinity measurements. Ocean Science, 9(1), pp.1-18.

Campbell, J.L., Rustad, L.E., Porter, J.H., Taylor, J.R., Dereszynski, E.W., Shanley, J.B., Gries, C., Henshaw, D.L., Martin, M.E., Sheldon, W.M. and Boose, E.R., 2013. Quantity is nothing without quality: Automated QA/QC for streaming environmental sensor data. BioScience, 63(7), pp.574-585.

Estévez, J., Gavilán, P. and Giráldez, J.V., 2011. Guidelines on validation procedures for meteorological data from automatic weather stations. Journal of Hydrology, 402(1), pp.144-154.

Fiebrich, C.A., Morgan, C.R., McCombs, A.G., Hall Jr, P.K. and McPherson, R.A., 2010. Quality assurance procedures for mesoscale meteorological data. Journal of Atmospheric and Oceanic Technology, 27(10), pp.1565-1582.

Fredericks, J., Botts, M., Bermudez, L., Bosch, J., Bogden, P., Bridger, E., Cook, T., Delory, E., Graybeal, J., Haines, S. and Holford, A., 2009. Integrating Quality Assurance and Quality Control into Open GeoSpatial Consortion Sensor Web Enablement. Proceedings of OceanObs, 9.

Hasu, V. and Aaltonen, A., 2011. Automatic minimum and maximum alarm thresholds for quality control. Journal of Atmospheric and Oceanic Technology, 28(1), pp.74-84.

Morello, E.B., Galibert, G., Smith, D., Ridgway, K.R., Howell, B., Slawinski, D., Timms, G.P., Evans, K. and Lynch, T.P., 2014. Quality control (QC) procedures for Australia's National Reference Station's sensor data-comparing semi-autonomous systems to an expert oceanographer. Methods in Oceanography, 9, pp.17-33.

Taylor, J.R. and Loescher, H.L., 2013. Automated quality control methods for sensor data: a novel observatory approach. Biogeosciences, 10(7), pp.4957-4971.