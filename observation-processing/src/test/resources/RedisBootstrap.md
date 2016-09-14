# Redis Setup for the unit testing suites

## SEMANTIC STAMP

Data necessary for the SemanticStamp based suite, where the code looks up the non-logger hardware feature, procedure and observed properties.  Plain English used here, in production these will be links to the catalogue and vocabulary

```
SET SBAS::PRT1::feature southbasin
SET SBAS::PRT1::procedure prtone
SET SBAS::PRT1::observableproperty temperature
SET SBAS::PRT1::observationtype numeric

SET SBAS::PRT2::feature southbasin
SET SBAS::PRT2::procedure prttwo
SET SBAS::PRT2::observableproperty temperature
```

## QC BLOCK LOGIC

Data necessary for the logic based QC checks, including:

* Intended spacing: for only one of the sensors to test that when not found default behaviour is OK
* Meta-identity check: to simulate default QC pass outcomes for meta-data who's presence in the datastream means a failed observation, e.g. metadata relating to a maintenance or cleaning window, we create the following entries that describe per feature the expected metadata types.
* Meta-value check: similar to identity, except that as a value is used for comparision, it is treated like an observation range check


```
SET southbasin::prtone::temperature::intendedspacing 240000
 
SET southbasin::meta::identity maintenance::cleaning::reset
SET southbasin::meta::identity::maintenance southbasin,prtone,temperature::southbasin,prttwo,temperature
 
SET southbasin::meta::value battery::wifi::network::sdcard::cabling
```

## QC BLOCK META

The QC meta block does not need to lookup any values when parsing the CSV (such as the URI for battery checks, or the URI for maintenance checks), as these are expected to be known in advance, as most observations will be created by a user rather than from a logger and as such we can control this.  If this changes, a lookup can be added.

The identity check looks up the registry to identify the unique feature, procedure, observable property combinations that the meta-observation effects.  Once it has these, it emits a failing outcome for each with a time range that will identify the observations to be updated within the databse.  These lookups are the same as in QC Block Logic.

The value check is a threshold check, which may have minimum and/or maximum bounds.  For each check there is the registry entry of effected feature, procedure, observable property combinations, followed by an entry listing the different implementations of the test.  Each of these implementations can have the minimum and maximum bound values set once, or for each hour, day, or month resolution.

The entry below creates a set of all different bounds possible for the battery entry, with a single min and max entry for each.  The network and sdcard entries are added to check the behaviour of half-entered sets.
```
SET southbasin::meta::value::battery southbasin,prtone,temperature::southbasin,prttwo,temperature

SET southbasin::meta::value::battery::thresholds::range static::hourlybattery::dailybattery::monthlybattery
SET southbasin::meta::value::battery::thresholds::range::static single
SET southbasin::meta::value::battery::thresholds::range::hourlybattery hour
SET southbasin::meta::value::battery::thresholds::range::dailybattery day
SET southbasin::meta::value::battery::thresholds::range::monthlybattery month

SET southbasin::meta::value::battery::thresholds::range::static::min 10
SET southbasin::meta::value::battery::thresholds::range::static::max 14

SET southbasin::meta::value::battery::thresholds::range::hourlybattery::min::2016-08-01T12 14
SET southbasin::meta::value::battery::thresholds::range::hourlybattery::max::2016-08-01T12 31

SET southbasin::meta::value::battery::thresholds::range::dailybattery::min::2016-08-01 14
SET southbasin::meta::value::battery::thresholds::range::dailybattery::max::2016-08-01 31

SET southbasin::meta::value::battery::thresholds::range::monthlybattery::min::2016-08 14
SET southbasin::meta::value::battery::thresholds::range::monthlybattery::max::2016-08 31

SET southbasin::meta::value::network southbasin,prtone,temperature

SET southbasin::meta::value::sdcard southbasin,prtone,temperature

SET southbasin::meta::value::cabling southbasin,prtone,temperature
SET southbasin::meta::value::cabling::thresholds::range static
SET southbasin::meta::value::cabling::thresholds::range::static single
SET southbasin::meta::value::cabling::thresholds::range::static::min 12
```

## QC BLOCK THRESHOLD

Similarly to the QC Meta Block ranges for battery, are those for observation, delta, and sigma values.  The range-based check has all the different temporal resolutions, while delta and sigma have only the static single entries, as the code to test the range is the same for the other two.  For each range based test resolution, we set a pair of min and max entries.

Range-based checks:
```
SET southbasin::prtone::temperature::thresholds::range static::hourlyTL::dailyTL::monthlyTL

SET southbasin::prtone::temperature::thresholds::range::static single
SET southbasin::prtone::temperature::thresholds::range::hourlyTL hour
SET southbasin::prtone::temperature::thresholds::range::dailyTL day
SET southbasin::prtone::temperature::thresholds::range::monthlyTL month

SET southbasin::prtone::temperature::thresholds::range::static::min 10
SET southbasin::prtone::temperature::thresholds::range::static::max 120

SET southbasin::prtone::temperature::thresholds::range::hourlyTL::min::2016-08-02T09 10
SET southbasin::prtone::temperature::thresholds::range::hourlyTL::max::2016-08-02T09 30

SET southbasin::prtone::temperature::thresholds::range::dailyTL::min::2016-08-03 45
SET southbasin::prtone::temperature::thresholds::range::dailyTL::max::2016-08-03 45

SET southbasin::prtone::temperature::thresholds::range::monthlyTL::min::2016-08 5
SET southbasin::prtone::temperature::thresholds::range::monthlyTL::max::2016-08 120
```

Sigma-based checks:
```
SET southbasin::prtone::temperature::thresholds::sigma static

SET southbasin::prtone::temperature::thresholds::sigma::1h::static single
SET southbasin::prtone::temperature::thresholds::sigma::12h::static single
SET southbasin::prtone::temperature::thresholds::sigma::24h::static single

SET southbasin::prtone::temperature::thresholds::sigma::1h::static::min 0
SET southbasin::prtone::temperature::thresholds::sigma::1h::static::max 1

SET southbasin::prtone::temperature::thresholds::sigma::12h::static::min 1
SET southbasin::prtone::temperature::thresholds::sigma::12h::static::max 2

SET southbasin::prtone::temperature::thresholds::sigma::24h::static::min 2
SET southbasin::prtone::temperature::thresholds::sigma::24h::static::max 4
```
Delta-based checks:
```
SET southbasin::prtone::temperature::thresholds::delta::step static
SET southbasin::prtone::temperature::thresholds::delta::spike static
SET southbasin::prtone::temperature::thresholds::delta::step::static single
SET southbasin::prtone::temperature::thresholds::delta::spike::static single

SET southbasin::prtone::temperature::thresholds::delta::step::static::min 0
SET southbasin::prtone::temperature::thresholds::delta::step::static::max 10

SET southbasin::prtone::temperature::thresholds::delta::spike::static::min 0
SET southbasin::prtone::temperature::thresholds::delta::spike::static::max 5
```
