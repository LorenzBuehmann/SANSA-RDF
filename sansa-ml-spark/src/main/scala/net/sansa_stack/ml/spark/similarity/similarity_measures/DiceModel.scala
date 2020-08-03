package net.sansa_stack.ml.spark.similarity.similarity_measures

import org.apache.spark.ml.linalg.Vector
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions.{col, lit, typedLit, udf}

class DiceModel extends GenericSimilarityEstimatorModel {

  protected val dice = udf( (a: Vector, b: Vector) => {
    val feature_indices_a = a.toSparse.indices
    val feature_indices_b = b.toSparse.indices
    val f_set_a = feature_indices_a.toSet
    val f_set_b = feature_indices_b.toSet
    val dice = (2 * f_set_a.intersect(f_set_b).size.toDouble) / (f_set_a.size.toDouble + f_set_b.size.toDouble)
    dice
  })

  override val estimatorName: String = "DiceSimilarityEstimator"
  override val estimatorMeasureType: String = "similarity"

  override val similarityEstimation = dice

  override def similarityJoin(dfA: DataFrame, dfB: DataFrame, threshold: Double = -1.0, valueColumn: String = "dice_similarity"): DataFrame = {

    val cross_join_df = createCrossJoinDF(dfA: DataFrame, dfB: DataFrame)

    setSimilarityEstimationColumnName(valueColumn)

    val join_df: DataFrame = cross_join_df.withColumn(
      _similarityEstimationColumnName,
      similarityEstimation(col(_featuresColumnNameDfA), col(_featuresColumnNameDfB))
    )
    reduceJoinDf(join_df, threshold)
  }

  override def nearestNeighbors(dfA: DataFrame, key: Vector, k: Int, keyUri: String = "unknown", valueColumn: String = "dice_similarity", keepKeyUriColumn: Boolean = false): DataFrame = {

    setSimilarityEstimationColumnName(valueColumn)

    val nn_setup_df = createNnDF(dfA, key, keyUri)

    val nn_df = nn_setup_df
      .withColumn(
        _similarityEstimationColumnName,
        similarityEstimation(col(_featuresColumnNameDfB), col(_featuresColumnNameDfA)))

    reduceNnDf(nn_df, k, keepKeyUriColumn)
  }
}