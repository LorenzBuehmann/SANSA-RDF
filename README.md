# SANSA RDF
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/net.sansa-stack/sansa-rdf-parent_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/net.sansa-stack/sansa-rdf-parent_2.11)
[![Build Status](https://ci.aksw.org/jenkins/job/SANSA%20RDF/job/develop/badge/icon)](https://ci.aksw.org/jenkins/job/SANSA%20RDF/job/develop/)
[![Coverage Status](https://coveralls.io/repos/github/SANSA-Stack/SANSA-RDF/badge.svg?branch=develop)](https://coveralls.io/github/SANSA-Stack/SANSA-RDF?branch=develop)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Twitter](https://img.shields.io/twitter/follow/SANSA_Stack.svg?style=social)](https://twitter.com/SANSA_Stack)

## Description
SANSA RDF is a library to read RDF files into [Spark](https://spark.apache.org) or [Flink](https://flink.apache.org). It allows files to reside in HDFS as well as in a local file system and distributes them across Spark RDDs/Datasets or Flink DataSets.


SANSA uses the RDF data model for representing graphs consisting of triples with subject, predicate and object. RDF datasets may contains multiple RDF graphs and record information about each graph, allowing any of the upper layers of sansa (Querying and ML) to make queries that involve information from more than one graph. Instead of directly dealing with RDF datasets, the target RDF datasets need to be converted into an RDD/DataSets of triples. We name such an RDD/DataSets a main dataset. The main dataset is based on an RDD/DataSets data structure, which is a basic building block of the Spark/Flink framework. RDDs/DataSets are in-memory collections of records that can be operated on in parallel on large clusters.

## Usage

The following Scala code shows how to read an RDF file in N-Triples syntax (be it a local file or a file residing in HDFS) into a Spark RDD:
```scala
import net.sansa_stack.rdf.spark.io.rdf._
import org.apache.jena.riot.Lang

val spark: SparkSession = ...

val lang = Lang.NTRIPLES
val triples = spark.rdf(lang)(path)

triples.take(5).foreach(println(_))
```

### N-Triples loading options
```
NTripleReader.load(...)
```
Loads N-Triples data from a file or directory into an RDD.
The path can also contain multiple paths
and even wildcards, e.g.
`"/my/dir1,/my/paths/part-00[0-5],/another/dir,/a/specific/file"`

#### Handling of errors

By default, it stops once a parse error occurs, i.e. a `org.apache.jena.riot.RiotException` will be thrown
generated by the underlying parser.

The following options exist:
- STOP the whole data loading process will be stopped and a `org.apache.jena.riot.RiotException` will be thrown
- SKIP the line will be skipped but the data loading process will continue, an error message will be logged

#### Handling of warnings

If the additional checking of RDF terms is enabled, warnings during parsing can occur. For example,
a wrong lexical form of a literal w.r.t. to its datatype will lead to a warning.

The following can be done with those warnings:
- IGNORE the warning will just be logged to the configured logger
- STOP similar to the error handling mode, the whole data loading process will be stopped and a
`org.apache.jena.riot.RiotException` will be thrown
- SKIP similar to the error handling mode, the line will be skipped but the data loading process will continue. 
In additon, an error message will be logged


#### Checking of RDF terms
Set whether to perform checking of NTriples - defaults to no checking.

Checking adds warnings over and above basic syntax errors.
This can also be used to turn warnings into exceptions if the option `stopOnWarnings` is set to STOP or SKIP.

- IRIs - whether IRIs confirm to all the rules of the IRI scheme
- Literals: whether the lexical form conforms to the rules for the datatype.
- Triples: check slots have a valid kind of RDF term (parsers usually make this a syntax error anyway).


See also the optional `errorLog` argument to control the output. The default is to log.

---
An overview is given in the [FAQ section of the SANSA project page](http://sansa-stack.net/faq/#rdf-processing). 
Further documentation about the builder objects can also be found on the [ScalaDoc page](http://sansa-stack.net/scaladocs/).
