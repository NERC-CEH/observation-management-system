# Metadata Modelling

The purpose of the metadata within the processing framework is twofold, first it ensures that every recorded observation has some context for its generation, and to allow the discovery of observations based on metadata searches.

## Vocabulary of Observation Processing Concepts

The system contains many different types of data that can be related, or hierarchically defined.  For instance the flag to indicate that a QC check has failed is related to the flag that indicates a QC check has passed.  Regarding QC check implementations, univariate single-observation range-based checks are conceptually different from multivariate multi-observation range-based checks, however they can both be defined as concepts under an umbrella range-based check category.  It is these types of relationship that the vocabulary is created to represent, modelled using the SKOS standard.

## Ontology of Sensors and their Deployments


Ontology providing instances of sensors, the meta-data around their deployment including time in/out, maintenance, etc.


