package net.sansa_stack.ml.spark.featureExtraction

import net.sansa_stack.query.spark.ontop.OntopSPARQLEngine
import net.sansa_stack.query.spark.sparqlify.{QueryExecutionFactorySparqlifySpark, QueryExecutionSparqlifySpark, SparqlifyUtils3}
import net.sansa_stack.rdf.common.partition.core.{RdfPartitionComplex, RdfPartitionerComplex}
import net.sansa_stack.rdf.spark.partition.core.RdfPartitionUtilsSpark
import org.apache.jena.graph.{Node, Triple}
import org.apache.jena.query.{Query, QueryFactory}
import org.apache.jena.sparql.core.Var
import org.apache.jena.sparql.engine.binding.Binding
import org.apache.jena.sparql.lang.ParserSPARQL11
import org.apache.spark.api.java.JavaRDD
import org.apache.spark.ml.Transformer
import org.apache.spark.ml.param.ParamMap
import org.apache.spark.ml.util.Identifiable
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.types.{BooleanType, DoubleType, FloatType, IntegerType, NullType, StringType, StructField, StructType}
import org.apache.spark.sql.{DataFrame, Dataset, Encoders, Row, SparkSession}

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer

class SparqlFrame extends Transformer{
  var _query: String = _
  var _queryExcecutionEngine: String = "sparqlify"

  override val uid: String = Identifiable.randomUID("sparqlFrame")

  protected val spark = SparkSession.builder().getOrCreate()

  override def copy(extra: ParamMap): Transformer = defaultCopy(extra)

  def setSparqlQuery(queryString: String): this.type = {
    _query = queryString
    this
  }

  def setQueryExcecutionEngine(queryExcecutionEngine: String): this.type = {
    if (queryExcecutionEngine == "ontop" | queryExcecutionEngine == "sparqlify") {
      _queryExcecutionEngine = queryExcecutionEngine
    }
    else {
      throw new Exception("This excecutor is not supported")
    }
    this
  }

  override def transformSchema(schema: StructType): StructType =
    throw new NotImplementedError()

  protected def getResultBindings(dataset: Dataset[_]): RDD[Binding] = {
    if (_queryExcecutionEngine == "ontop") {
      println("Usage of Ontop for Query Execution")
      implicit val tripleEncoder = Encoders.kryo(classOf[Triple])

      val partitions: Map[RdfPartitionComplex, RDD[Row]] =
        RdfPartitionUtilsSpark.partitionGraph(
          dataset.as[Triple].rdd,
          partitioner = RdfPartitionerComplex(false))

      val sparqlEngine = OntopSPARQLEngine(spark, partitions, Option.empty)
      val bindings: RDD[Binding] = sparqlEngine.execSelect(_query)

      bindings
    }
    else {
      println("Usage of Sparqlify for Query Execution")
      val query = QueryFactory.create(_query)
      implicit val tripleEncoder = Encoders.kryo(classOf[Triple])
      val partitions = RdfPartitionUtilsSpark.partitionGraph(dataset.as[Triple].rdd)
      val rewriter = SparqlifyUtils3.createSparqlSqlRewriter(spark, partitions)
      val qef: QueryExecutionFactorySparqlifySpark = new QueryExecutionFactorySparqlifySpark(spark, rewriter)
      val qe: QueryExecutionSparqlifySpark = qef.createQueryExecution(query)

      val sparkResultSet = qe.execSelectSpark() // SparkResultSet is a pair of result vars + rdd
      val resultVars : java.util.List[Var] = sparkResultSet.getResultVars
      val javaRdd: JavaRDD[Binding] = sparkResultSet.getRdd
      val scalaRdd : RDD[Binding] = javaRdd.rdd

      scalaRdd
    }

  }

  def transform(dataset: Dataset[_]): DataFrame = {
    val parser = new ParserSPARQL11()
    val projectionVars: Seq[Var] = parser.parse(new Query(), _query).getProjectVars.asScala
    val bindings: RDD[Binding] = getResultBindings(dataset)

    val features: RDD[Seq[Node]] = bindings.map(binding => {
      projectionVars.map(projectionVar => binding.get(projectionVar))
    })

    val columns: Seq[String] = projectionVars.map(_.toString().replace("?", ""))


    // the next block is for the edge case that no elements are in result
    if (features.count() == 0) {
      println(f"[WARNING] the resulting DataFrame is empty!")
      val emptyDf = spark.createDataFrame(
        spark.sparkContext.emptyRDD[Row],
        StructType(columns.map(colName => StructField(colName, StringType, nullable = true)))
      )
      return emptyDf
    }

    val types = features.map(seq => {
      seq.map(node => {
        if (node == null) {
          NullType
        }
        else if (node.isLiteral) {
          // TODO also include possiblity like Datetime and others
          if (node.getLiteralValue.isInstanceOf[Float]) FloatType
          else if (node.getLiteralValue.isInstanceOf[Double]) DoubleType
          else if (node.getLiteralValue.isInstanceOf[Boolean]) BooleanType
          else if (node.getLiteralValue.isInstanceOf[String]) StringType
          else if (node.getLiteralValue.isInstanceOf[Int]) IntegerType
          else StringType
        }
        else StringType
      })
    })

    var columnTypes = ListBuffer.empty[org.apache.spark.sql.types.DataType]
    val firstRow = types.take(1).toSeq
    val numberColumns = firstRow(0).toSeq.size
    // println(f"We have $numberColumns columns")
    for (i <- 0 to numberColumns - 1) {
      val allTypesOfColumn = types.map(_ (i)).filter(_ != NullType)
      if (allTypesOfColumn.distinct.collect().toSeq.size == 1) {
        val elements = allTypesOfColumn.take(1).toSeq
        val element = elements(0).asInstanceOf[org.apache.spark.sql.types.DataType] // .toString
        columnTypes.append(element)
      }
      else {
        val typeDistribution = allTypesOfColumn.groupBy(identity).mapValues(_.size).collect().toSeq
        println(
          f"""
            |[WARNING] the column type is not clear because different or only null types occured.
            |\t type distribution is:
            |\t ${typeDistribution.mkString(" ")}
            |\t this is why we fallback to StringType
            |\t """.stripMargin)
        val element = StringType.asInstanceOf[org.apache.spark.sql.types.DataType] // .toString
        columnTypes.append(element)
      }
    }

    val structTypesList = ListBuffer.empty[StructField]
    for (i <- 0 to numberColumns - 1) {
      structTypesList.append(
        StructField(columns(i), columnTypes(i), nullable = true)
      )
    }
    val schema = StructType(structTypesList.toSeq)

    val featuresNativeScalaDataTypes = features.map(seq => {
      seq.map(node => {
        if (node == null) null
        else {
          node.isLiteral match {
            case true => node.getLiteralValue
            case false => node.toString()
          }
        }
      })
    })

    spark.createDataFrame(featuresNativeScalaDataTypes.map(Row.fromSeq(_)), schema)
  }
}