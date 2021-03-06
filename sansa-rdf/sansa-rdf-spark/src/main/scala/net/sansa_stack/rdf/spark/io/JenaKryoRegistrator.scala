package net.sansa_stack.rdf.spark.io

import com.esotericsoftware.kryo.Kryo
import de.javakaffee.kryoserializers.guava.HashMultimapSerializer
import net.sansa_stack.rdf.common.kryo.jena.JenaKryoSerializers._
import net.sansa_stack.rdf.spark.kryo.jena.KryoRegistratorRDFNode
import org.apache.jena.query.Query
import org.apache.jena.rdf.model.impl.ModelCom
import org.apache.jena.sparql.core.DatasetImpl
import org.apache.spark.serializer.KryoRegistrator

/**
 * Created by nilesh on 01/06/2016.
 */
class JenaKryoRegistrator extends KryoRegistrator {
  override def registerClasses(kryo: Kryo) {

    HashMultimapSerializer.registerSerializers(kryo);

    // Partitioning
    kryo.register(classOf[net.sansa_stack.rdf.common.partition.core.RdfPartitionDefault])
    kryo.register(classOf[Array[net.sansa_stack.rdf.common.partition.core.RdfPartitionDefault]])

    kryo.register(classOf[org.apache.jena.graph.Node], new NodeSerializer)
    kryo.register(classOf[Array[org.apache.jena.graph.Node]], new NodeArraySerializer)
    kryo.register(classOf[org.apache.jena.sparql.core.Var], new VarSerializer)
    kryo.register(classOf[org.apache.jena.sparql.expr.Expr], new ExprSerializer)
    kryo.register(classOf[org.apache.jena.graph.Node_Variable], new VariableNodeSerializer)
    kryo.register(classOf[org.apache.jena.graph.Node_Blank], new NodeSerializer)
    kryo.register(classOf[org.apache.jena.graph.Node_ANY], new ANYNodeSerializer)
    kryo.register(classOf[org.apache.jena.graph.Node_URI], new NodeSerializer)
    kryo.register(classOf[org.apache.jena.graph.Node_Literal], new NodeSerializer)
    kryo.register(classOf[org.apache.jena.graph.Triple], new TripleSerializer)
    kryo.register(classOf[Array[org.apache.jena.graph.Triple]])
    kryo.register(classOf[scala.collection.mutable.WrappedArray.ofRef[_]])

    // Jena's Query class
    kryo.register(classOf[Query], new QuerySerializer)


    kryo.register(classOf[ModelCom], new ModelSerializer)
    kryo.register(classOf[DatasetImpl], new DatasetSerializer)

    KryoRegistratorRDFNode.registerClassesActual(kryo)
  }
}
