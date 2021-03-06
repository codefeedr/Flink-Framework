package org.codefeedr.core.library.internal.kafka.sink

import com.typesafe.scalalogging.LazyLogging
import org.codefeedr.core.library.metastore.{Epoch, EpochNode}
import org.codefeedr.model.zookeeper.{EpochCollection, Partition}
import org.codefeedr.util.Stopwatch

import scala.async.Async.{async, await}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait EpochStateManagerComponent {

  val epochStateManager: EpochStateManager

}

/**
  * Managing the zookeeper state of an epochstate of a sink
  */
class EpochStateManager extends Serializable with LazyLogging {

  /**
    * Precommits the current epoch state
    * Creates the relevant nodes in zookeeper
    */
  def preCommit(epochState: EpochState): Future[Unit] = async {
    val epochNode = await(guaranteeEpochNode(epochState))
    await(epochNode.asyncWriteLock(() =>
      async {
        //await(epochNode.sync())
        //Create all partition offsets of the current transaction

        logger.debug(
          s"precommitting epoch ${epochState.transactionState.checkPointId}: ${epochState.transactionState.offsetMap}")

        await(
          Future.sequence(
            epochState.transactionState.offsetMap.map(a =>
              async {
                val partition = a._1
                val offset = a._2
                val partitionNode = epochState.epochNode
                  .getPartitions()
                  .getChild(partition.toString)

                //If the node already exists (because some other worker created it), update it if its own offset is higher
                /*  if(await(partitionNode.exists()))
            {
            val oldOffset = await(partitionNode.getData()).get.offset
              if(offset > oldOffset) {
                await(partitionNode.setData(Partition(partition, offset)))
              }
              //Otherwise, create it with the new offset
            } else {*/
                logger.debug(
                  s"precommitting ${Partition(partition, offset)} on epoch {${epochState.epochNode.name} (${partitionNode.name})}")
                await(partitionNode.create(Partition(partition, offset)))
                /*}*/
            })
          ))
    }))
  }

  /**
    * Perform the actual commit
    * Flags the node as committed
    */
  def commit(epochState: EpochState): Future[Unit] = async {
    //Perform await to convert return type to unit
    await(
      Future.sequence(
        epochState.transactionState.offsetMap.map(a => {
          val partition = a._1
          epochState.epochNode.getPartitions().getChild(partition.toString).setState(true)
        })
      ))

    //If this task is the last one, we can complete the epoch in parallel
    await(completeEpoch(epochState))
  }

  //TODO: Perform operaiton under lock on the epoch
  /** Checks if the epoch can be completed, and if so, completes it */
  def completeEpoch(epochState: EpochState): Future[Unit] = async {
    if (await(epochState.epochNode.getPartitions().getState())) {
      await(epochState.epochNode.asyncWriteLock(() =>
        async {
          if (!await(epochState.epochNode.getState()).get) { //Validate we don't perform the complete operation twice
            logger.debug(
              s"Completing epoch ${epochState.epochNode.getEpoch()}(${epochState.transactionState.checkPointId}) for subject ${epochState.epochCollectionNode.parent().name}")

            //Calculate all combined partitions
            val epoch = Epoch(epochState.transactionState.checkPointId,
                              await(epochState.epochNode.getPartitionData()).map(o => o.nr -> o.offset).toMap)
            //Update the epochNode itself
            await(epochState.epochNode.setData(epoch))
            //Flag the epoch as completed
            await(epochState.epochNode.setState(true))
            //Mark the epoch as latest epoch
            await(
              epochState.epochCollectionNode.setData(
                EpochCollection(epochState.transactionState.checkPointId)))
            logger.debug(
              s"Completed epoch ${epochState.epochNode.getEpoch()}(${epochState.transactionState.checkPointId}) for subject ${epochState.epochCollectionNode.parent().name}")
          }
      }))
    }
  }

  /**
    * Creates the epochnode if it does not exist yet
    * @return
    */
  private def guaranteeEpochNode(epochState: EpochState): Future[EpochNode] = async {
    if (!await(epochState.epochNode.exists())) {
      await(epochState.epochCollectionNode.asyncWriteLock(() => createEpochNode(epochState)))
    }
    epochState.epochNode
  }

  /**
    * Creates the epochNode and its dependencies
    * Should be called from within a writelock on the epochCollectionNode
    * @return
    */
  private def createEpochNode(epochState: EpochState): Future[Unit] = async {
    if (!await(epochState.epochNode.exists())) {
      await(epochState.epochNode.create())
    }
  }

}
