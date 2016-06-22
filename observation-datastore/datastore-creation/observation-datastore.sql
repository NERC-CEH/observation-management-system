-- ---------------------------------------------------------
-- OBSERVATION DATASTORE SETUP
-- ---------------------------------------------------------

-- Create keyspace for the observation related tables
CREATE KEYSPACE IF NOT EXISTS observation 
-- Simple strategy is used as currently only simple hosting is used with one datacentre,
--  while the replication factor is used to create two copies of any table entry across
--  the cluster
WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 2 }
-- Durable writes ensures that data received is written to the commit log
AND DURABLE_WRITES = true;


-- Create the observation table
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS observation.observations(
procedure text,
feature text,
observableproperty text,
year int,
month int,
phenomenontimestart timestamp,
phenomenontimeend timestamp,
value decimal,
quality text,
accuracy decimal,
status text,
processing text,
uncertml text,
comment text,
PRIMARY KEY ((procedure, feature, observableproperty, year, month), phenomenontimestart)
)
WITH CLUSTERING ORDER BY (phenomenontimestart)
-- Compaction and bloom filter settings go together.  LeveledCompaction was chosen
--  following on from the blog post below that seems to fit LeveledCompaction to our
--  use-case: http://www.datastax.com/dev/blog/when-to-use-leveled-compaction
--  The bloom filter is set to 0.1 as per the datastax instructions here:
--  https://docs.datastax.com/en/cql/3.3/cql/cql_reference/tabProp.html
AND COMPACTION = {'class' : 'LeveledCompactionStrategy',
                'tombstone_compaction_interval' : 86400 ,
                'tombstone_threshold' : 0.2}
AND bloom_filter_fp_chance = 0.1
-- Cache all row keys for lookup, and cache 100 rows per partition (this parameter needs performance testing)
AND caching = {'keys' : 'ALL', 'rows_per_partition' : '100'}
-- Compression is disabled
AND COMPRESSION = {'sstable_compression' : ''}
AND comment = 'The main table for storing observation data, holding the observed value and meta-data regarding the current state of observation processing and quality.';



-- Create the forecast observation table
-- ---------------------------------------------------------

-- CHECK: Does the composite column key make sense?  Is it possible to
--        search for the phenomenon time point of interest, with valid > x?
CREATE TABLE IF NOT EXISTS observation.forecast_observations(
procedure text,
feature text,
observableproperty text,
year int,
month int,
phenomenontimestart timestamp,
phenomenontimeend timestamp,
validtimestart timestamp,
validtimeend timestamp,
value decimal,
quality text,
accuracy decimal,
status text,
processing text,
uncertml text,
comment text,
PRIMARY KEY ((procedure, feature, observableproperty, year, month), phenomenontimestart, validtimeend)
)
WITH CLUSTERING ORDER BY (phenomenontimestart)
-- Compaction and bloom filter settings go together.  LeveledCompaction was chosen
--  following on from the blog post below that seems to fit LeveledCompaction to our
--  use-case: http://www.datastax.com/dev/blog/when-to-use-leveled-compaction
--  The bloom filter is set to 0.1 as per the datastax instructions here:
--  https://docs.datastax.com/en/cql/3.3/cql/cql_reference/tabProp.html
AND COMPACTION = {'class' : 'LeveledCompactionStrategy',
                'tombstone_compaction_interval' : 86400 ,
                'tombstone_threshold' : 0.2}
AND bloom_filter_fp_chance = 0.1
-- Cache all row keys for lookup, and cache 100 rows per partition (this parameter needs performance testing)
AND caching = {'keys' : 'ALL', 'rows_per_partition' : '100'}
-- Compression is disabled
AND COMPRESSION = {'sstable_compression' : ''}
AND comment = 'The main table for storing forecast observation data, holding the predicted values and meta-data regarding the current state of observation processing and quality.  Stored separately from observed data due to the extra valid time columns.';



-- Create the observation QC information table
-- ---------------------------------------------------------

CREATE TABLE IF NOT EXISTS observation.observations_qc(
procedure text,
feature text,
observableproperty text,
year int,
month int,
phenomenontimestart timestamp,
qualifier text,
value text,
comment text,
PRIMARY KEY ((procedure, feature, observableproperty, year, month), phenomenontimestart, qualifier)
)
WITH CLUSTERING ORDER BY (phenomenontimestart)
-- Compaction and bloom filter settings go together.  LeveledCompaction was chosen
--  following on from the blog post below that seems to fit LeveledCompaction to our
--  use-case: http://www.datastax.com/dev/blog/when-to-use-leveled-compaction
--  The bloom filter is set to 0.1 as per the datastax instructions here:
--  https://docs.datastax.com/en/cql/3.3/cql/cql_reference/tabProp.html
AND COMPACTION = {'class' : 'LeveledCompactionStrategy',
                'tombstone_compaction_interval' : 86400 ,
                'tombstone_threshold' : 0.2}
AND bloom_filter_fp_chance = 0.1
-- Cache all row keys for lookup, and cache 100 rows per partition (this parameter needs performance testing)
AND caching = {'keys' : 'ALL', 'rows_per_partition' : '100'}
-- Compression is disabled
AND COMPRESSION = {'sstable_compression' : ''}
AND comment = 'The QC table holds the detailed information regarding check outcomes for observations held in the "observations" table.  For each observation there is a qualifier holding the URI of the check, the outcome, and a comment regarding the QC output.';