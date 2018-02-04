package org.codefeedr.Core.Library.Metastore

import org.codefeedr.Core.Library.Internal.Zookeeper.{ZkStateNode, ZkNode, ZkNodeBase}
import org.codefeedr.Model.Zookeeper.{Consumer, Producer}

import scala.async.Async.{async, await}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.ClassTag

class ProducerNode(name: String, parent: ZkNodeBase)
    extends ZkNode[Producer](name, parent)
    with ZkStateNode[Producer, Boolean] {


  override def TypeT(): ClassTag[Boolean] = ClassTag(classOf[Boolean])
  override def InitialState(): Boolean = true

  override def SetState(state: Boolean): Future[Unit] = async {
    await(super.SetState(state))
    parent.Parent().asInstanceOf[QuerySinkNode].UpdateState()
  }

}
