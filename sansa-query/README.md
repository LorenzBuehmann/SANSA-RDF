# SANSA Query
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/net.sansa-stack/sansa-query-parent_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/net.sansa-stack/sansa-query-parent_2.11)
[![Build Status](https://ci.aksw.org/jenkins/job/SANSA-Query/job/develop/badge/icon)](https://ci.aksw.org/jenkins/job/SANSA-Query/job/develop/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Twitter](https://img.shields.io/twitter/follow/SANSA_Stack.svg?style=social)](https://twitter.com/SANSA_Stack)

## Description
SANSA Query is a library to perform SPARQL queries over RDF data using big data engines [Spark](https://spark.apache.org) and [Flink](https://flink.apache.org). It allows to query RDF data that resides both in HDFS and in a local file system. Queries are executed distributed and in parallel across Spark RDDs/DataFrames or Flink DataSets. Further, SANSA-Query can query non-RDF data stored in databases e.g., MongoDB, Cassandra, MySQL or file format Parquet, using Spark.

For RDF data, SANSA uses vertical partitioning (VP) approach and is designed to support extensible partitioning of RDF data.
Instead of dealing with a single triple table (s, p, o), data is partitioned into multiple tables based on the used RDF predicates, RDF term types and literal datatypes.
The first column of these tables is always a string representing the subject.
The second column always represents the literal value as a Scala/Java datatype.
Tables for storing literals with language tags have an additional third string column for the language tag.
It supports [Sparqlify](https://github.com/AKSW/Sparqlify) or [Ontop](https://ontop-vkg.org/) as scalable SPARQL-to-SQL rewriters.

For heterogeneous data sources (data lake), SANSA uses virtual property tables (PT) partitioning, whereby data relevant to a query is loaded _on the fly_ into Spark DataFrames composed of attributes corresponding to the properties of the query. 

### SANSA Query SPARK - RDF
On SANSA Query Spark for RDF the method for partitioning an `RDD[Triple]` is located in [RdfPartitionUtilsSpark](https://github.com/SANSA-Stack/SANSA-RDF/blob/develop/sansa-rdf-spark/src/main/scala/net/sansa_stack/rdf/spark/partition/core/RdfPartitionUtilsSpark.scala). It uses an [RdfPartitioner](https://github.com/SANSA-Stack/SANSA-RDF/blob/develop/sansa-rdf-common/src/main/scala/net/sansa_stack/rdf/common/partition/core/RdfPartitioner.scala) which maps a Triple to a single [RdfPartition](https://github.com/SANSA-Stack/SANSA-RDF/blob/develop/sansa-rdf-common/src/main/scala/net/sansa_stack/rdf/common/partition/core/RdfPartition.scala) instance.

* [RdfPartition](https://github.com/SANSA-Stack/SANSA-RDF/blob/develop/sansa-rdf-common/src/main/scala/net/sansa_stack/rdf/common/partition/core/RdfPartition.scala) - as the name suggests, represents a partition of the RDF data and defines two methods:
  * `matches(Triple): Boolean`: This method is used to test whether a triple fits into a partition.
  * `layout: TripleLayout`: This method returns the [TripleLayout](https://github.com/SANSA-Stack/SANSA-RDF/blob/develop/sansa-rdf-common/src/main/scala/net/sansa_stack/rdf/common/partition/layout/TripleLayout.scala) associated with the partition, as explained below.
  * Furthermore, RdfPartitions are expected to be serializable, and to define equals and hash code.
* TripleLayout instances are used to obtain framework-agnostic compact tabular representations of triples according to a partition. For this purpose it defines the two methods:
  * `fromTriple(triple: Triple): Product`: This method must, for a given triple, return its representation as a [Product](https://www.scala-lang.org/files/archive/api/2.11.8/index.html#scala.Product) (this is the super class of all Scala tuples)
  * `schema: Type`: This method must return the exact Scala type of the objects returned by `fromTriple`, such as `typeOf[Tuple2[String,Double]]`. Hence, layouts are expected to only yield instances of one specific type.

See the [available layouts](https://github.com/SANSA-Stack/SANSA-RDF/tree/develop/sansa-rdf-common/src/main/scala/net/sansa_stack/rdf/common/partition/layout) for details.

### SANSA Query SPARK - Heterogeneous Data Sources
SANSA Query Spark for heterogeneous data sources (data data) is composed of three main components:

* [Analyser](https://github.com/SANSA-Stack/SANSA-DataLake/tree/develop/sansa-datalake-spark/src/main/scala/net/sansa_stack/datalake/spark): it extracts SPARQL triple patters and groups them by subject, it also extracts any operation on subjects like filters, group by, order by, distinct, limit.
* ِ[Planner](https://github.com/SANSA-Stack/SANSA-DataLake/blob/develop/sansa-datalake-spark/src/main/scala/net/sansa_stack/datalake/spark/Planner.scala): it extracts joins between subject-based triple patter groups and generates join plan accordingly. The join order followed is left-deep. 
* [Mapper](https://github.com/SANSA-Stack/SANSA-DataLake/blob/develop/sansa-datalake-spark/src/main/scala/net/sansa_stack/datalake/spark/Mapper.scala): it access (RML) mappings and matches properties of a subject-based triples patter group against the attributes of individual data sources. If a match exists of every property of the triple pattern, the respective data source is declared _relavant_ and loaded into Spark DataFrame. The loading into DataFrame is performed using [Spark Connectors](https://spark-packages.org/).
* [Executor](https://github.com/SANSA-Stack/SANSA-DataLake/blob/develop/sansa-datalake-spark/src/main/scala/net/sansa_stack/datalake/spark/SparkExecutor.scala): it analyses SPARQL query and generates equivalent Spark SQL functions over DataFrames, for SELECT, WHERE, GROUP-BY, ORDER-BY, LIMIT. Connection between subject-based triple pattern groups are translated into JOINs between relevant Spark DataFrames. 

## Usage

### Requirements

We currently require a Spark 2.4.x with Scala 2.12 setup.

#### Release Version
Add the following Maven dependency to your project POM file:
```xml
<!-- SANSA Querying -->
<dependency>
   <groupId>net.sansa-stack</groupId>
   <artifactId>sansa-query-spark_2.12</artifactId>
   <version>$LATEST_RELEASE_VERSION$</version>
</dependency>
```

#### SNAPSHOT Version
While the release versions are available on Maven Central, latest SNAPSHOT versions have to be installed from source code:
```bash
git clone https://github.com/SANSA-Stack/SANSA-Stack.git
cd SANSA-Stack
mvn -am -DskipTests -pl :sansa-query-spark_2.12 clean install 
```
Alternatively, you can use the following Maven repository and addd it to your project POM file `repositories` section:
```xml
<repository>
   <id>maven.aksw.snapshots</id>
   <name>AKSW Snapshot Repository</name>
   <url>http://maven.aksw.org/archiva/repository/snapshots</url>
   <releases>
      <enabled>false</enabled>
   </releases>
   <snapshots>
      <enabled>true</enabled>
   </snapshots>
</repository>
```
Then do the same as for the release version and add the dependency:
```xml
<!-- SANSA Querying -->
<dependency>
   <groupId>net.sansa-stack</groupId>
   <artifactId>sansa-query-spark_2.12</artifactId>
   <version>$LATEST_SNAPSHOT_VERSION$</version>
</dependency>
```
### Running from code
The following Scala code shows how to query an RDF file with SPARQL (be it a local file or a file residing in HDFS):
#### Running SPARQL queries via Sparqlify engine

```scala

val spark: SparkSession = ...

val triples = spark.rdf(Lang.NTRIPLES)("path/to/rdf.nt")

val partitions = RdfPartitionUtilsSpark.partitionGraph(triples)
val rewriter = SparqlifyUtils3.createSparqlSqlRewriter(spark, partitions)

val qef = new QueryExecutionFactorySparqlifySpark(spark, rewriter)

val port = 7531
val server = FactoryBeanSparqlServer.newInstance.setSparqlServiceFactory(qef).setPort(port).create()
server.join()

```

#### Running SPARQL queries via Ontop engine
``` scala
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import net.sansa_stack.query.spark.ontop.OntopSPARQLEngine
import net.sansa_stack.rdf.spark.partition.core.RdfPartitionUtilsSpark
import net.sansa_stack.rdf.common.partition.core.RdfPartitionerComplex
import net.sansa_stack.rdf.spark.io._
import org.apache.jena.riot.Lang
import org.apache.jena.sparql.engine.binding.Binding

// SparkSession is needed
val spark = SparkSession.builder
      .appName(s"Ontop SPARQL example")
      .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer") // we need Kryo serialization enabled with some custom serializers
      .config("spark.kryo.registrator", String.join(
        ", ",
        "net.sansa_stack.rdf.spark.io.JenaKryoRegistrator",
        "net.sansa_stack.query.spark.sparqlify.KryoRegistratorSparqlify"))
      .config("spark.sql.crossJoin.enabled", true) // needs to be enabled if your SPARQL query does make use of cartesian product Note: in Spark 3.x it's enabled by default
      .getOrCreate()

// load an RDD of triples (from an N-Triples file here)
val data = spark.rdf(Lang.NTRIPLES)("path/to/rdf.nt")

// apply vertical partitioning which is necessary for the current Ontop integration
val partitions = RdfPartitionUtilsSpark.partitionGraph(data, partitioner = RdfPartitionerComplex(false))

// create the SPARQL engine
val ontopEngine = OntopSPARQLEngine(spark, partitions, ontology = None)

// run a SPARQL
// a) SELECT query and return an RDD of bindings
val result: RDD[Binding] = ontopEngine.execSelect("SELECT ... WHERE { ... }")

// b) ASK query and return a boolean value
val result: Boolean = ontopEngine.execAsk("ASK WHERE { ... }")

// c) CONSTRUCT query and return an RDD of triples
val result: RDD[Triple] = ontopEngine.execConstruct("CONSTRUCT { ... } WHERE { ... }")
```
An overview is given in the [FAQ section of the SANSA project page](http://sansa-stack.net/faq/#sparql-queries). Further documentation about the builder objects can also be found on the [ScalaDoc page](http://sansa-stack.net/scaladocs/).

For querying heterogeneous data sources, refer to the documentation of the dedicated [SANSA-DataLake](https://github.com/SANSA-Stack/SANSA-DataLake) component.

## How to Contribute
We always welcome new contributors to the project! Please see [our contribution guide](http://sansa-stack.net/contributing-to-sansa/) for more details on how to get started contributing to SANSA.
