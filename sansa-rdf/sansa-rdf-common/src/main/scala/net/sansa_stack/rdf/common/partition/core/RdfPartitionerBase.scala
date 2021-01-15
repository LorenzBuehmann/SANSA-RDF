package net.sansa_stack.rdf.common.partition.core

import net.sansa_stack.rdf.common.partition.layout.{TripleLayout, TripleLayoutDouble, TripleLayoutLong, TripleLayoutString, TripleLayoutStringDate, TripleLayoutStringLang}
import org.apache.jena.datatypes.TypeMapper
import org.apache.jena.datatypes.xsd.XSDDatatype
import org.apache.jena.graph.{Node, Triple}
import org.apache.jena.vocabulary.{RDF, XSD}

/**
 *
 *
 * @param distinguishStringLiterals if `true`, literals of type `xsd:string` and `rdf:langString` will be handled differently (e.g.
 *                                  different partitions will be generated for each), otherwise all string literals will be handled
 *                                  the same way
 * @param partitionPerLangTag Whether to create individual partition for each language tag
 */
abstract class RdfPartitionerBase(distinguishStringLiterals: Boolean = false,
                                  partitionPerLangTag: Boolean = false)
  extends RdfPartitioner[RdfPartitionStateDefault]
    with Serializable {

  /** Ensure to use the same layouter for all partitions generated by this partitioner;
   * public only for testing */
  val layouter = this.determineLayout _

  def getUriOrBNodeString(node: Node): String = {
    val termType = getRdfTermType(node)
    termType match {
      case 0 => node.getBlankNodeId.getLabelString
      case 1 => node.getURI
      case _ => throw new RuntimeException("Neither Uri nor blank node: " + node)
    }
  }

  def getRdfTermType(node: Node): Byte = {
    val result =
      if (node.isURI) 1.toByte else if (node.isLiteral) 2.toByte else if (node.isBlank) 0.toByte else {
        throw new RuntimeException("Unknown RDF term type: " + node)
      } // -1
    result
  }

  def isPlainLiteralDatatype(dtypeIri: String): Boolean = {
    val result = dtypeIri == null || dtypeIri == "" || dtypeIri == XSD.xstring.getURI || dtypeIri == RDF.langString.getURI
    result
  }

  def isPlainLiteral(node: Node): Boolean = {
    val result = node.isLiteral && isPlainLiteralDatatype(node.getLiteralDatatypeURI) // NodeUtils.isSimpleString(node) || NodeUtils.isLangString(node))
    result
  }

  def isTypedLiteral(node: Node): Boolean = {
    val result = node.isLiteral && !isPlainLiteral(node)
    result
  }

  def fromTriple(t: Triple): RdfPartitionStateDefault = {
    val s = t.getSubject
    val o = t.getObject

    val subjectType = getRdfTermType(s)
    val objectType = getRdfTermType(o)
    // val predicateType =

    val predicate = t.getPredicate.getURI

    // In the case of plain literals, we replace the datatype langString with string
    // in order to group them all into the same partition

    // TODO Following commented out lines left for reference; delete once everything is working
    // val datatype = if (o.isLiteral()) (if (isPlainLiteral(o)) XSD.xstring.getURI else o.getLiteralDatatypeURI) else ""
    // val langTagPresent = isPlainLiteral(o)

    val datatype = if (o.isLiteral) {
      if (!distinguishStringLiterals && isPlainLiteral(o)) XSD.xstring.getURI else o.getLiteralDatatypeURI
    } else {
      ""
    }

    val langTagPresent = o.isLiteral &&
      ((distinguishStringLiterals && o.getLiteralDatatypeURI == RDF.langString.getURI && o.getLiteralLanguage.trim().nonEmpty)
        || (!distinguishStringLiterals && isPlainLiteral(o)))


    val lang = if (langTagPresent && partitionPerLangTag && o.getLiteralLanguage.nonEmpty) Set(o.getLiteralLanguage) else Set.empty[String]

    RdfPartitionStateDefault(subjectType, predicate, objectType, datatype, langTagPresent, lang)
  }

  /**
   * Lay a triple out based on the partition
   * Does not (re-)check the matches condition
   */
  def determineLayout(t: RdfPartitionStateDefault): TripleLayout = {
    val oType = t.objectType

    val layout = oType match {
      case 0 | 1 => TripleLayoutString // URI or bnode
      case 2 => if (distinguishStringLiterals) {
        if (t.datatype == RDF.langString.getURI) {
          TripleLayoutStringLang
        } else if (t.datatype == XSD.xstring.getURI) {
          TripleLayoutString
        } else {
          determineLayoutDatatype(t.datatype)
        }
      } else {
        if (isPlainLiteralDatatype(t.datatype)) {
          TripleLayoutStringLang
        } else {
          determineLayoutDatatype(t.datatype)
        }
      }
      case _ => throw new RuntimeException(s"Unsupported object type: $t")
    }
    layout
  }

  override def aggregate(partitions: Seq[RdfPartitionStateDefault]): Seq[RdfPartitionStateDefault] = {
    partitions
      .filter(_.languages.nonEmpty)
      .map(p => (p.predicate, p.subjectType) -> p)
      .groupBy(_._1)
      .map { case (k, v) =>
        val states = v.map(_._2)
        val languages = states.flatMap(_.languages).toSet
        val p = states.head
        RdfPartitionStateDefault(p.subjectType, p.predicate, p.objectType, p.datatype, p.langTagPresent, languages)
      }.toSeq ++ partitions.filter(_.languages.isEmpty)
  }

    /*
  def determineLayout(t: RdfPartitionDefault): TripleLayout = {
    val oType = t.objectType

    val layout = oType match {
      case 0 => TripleLayoutString
      case 1 => TripleLayoutString
      case 2 => if (isPlainLiteralDatatype(t.datatype)) TripleLayoutStringLang else determineLayoutDatatype(t.datatype)
      // if(!t.langTagPresent)
      // TripleLayoutString else TripleLayoutStringLang
      case _ => throw new RuntimeException("Unsupported object type: " + t)
    }
    layout
  }
  */

  protected val intDTypeURIs: Set[String] = Set(XSDDatatype.XSDnegativeInteger, XSDDatatype.XSDpositiveInteger,
    XSDDatatype.XSDnonNegativeInteger, XSDDatatype.XSDnonPositiveInteger,
    XSDDatatype.XSDinteger, XSDDatatype.XSDint)
    .map(_.getURI)

  def determineLayoutDatatype(dtypeIri: String): TripleLayout
}
