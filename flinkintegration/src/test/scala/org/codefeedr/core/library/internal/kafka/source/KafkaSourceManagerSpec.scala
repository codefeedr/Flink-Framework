package org.codefeedr.core.library.internal.kafka.source

import org.codefeedr.core.MockedLibraryServices
import org.codefeedr.core.library.metastore._
import org.codefeedr.core.library.metastore.sourcecommand.SourceCommand
import org.codefeedr.model.zookeeper.{EpochCollection, Partition}
import org.codefeedr.util.MockitoExtensions
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{AsyncFlatSpec, BeforeAndAfterEach}
import rx.lang.scala.Subject

import scala.async.Async.{async, await}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}

class KafkaSourceManagerSpec  extends AsyncFlatSpec with MockitoSugar with BeforeAndAfterEach with MockedLibraryServices with MockitoExtensions {

  private var subjectNode: SubjectNode = _

  private var sourceCollectionNode : QuerySourceCollection= _
  private var sourceNode: QuerySourceNode = _
  private var commandNode: QuerySourceCommandNode = _


  private var sourceSyncStateNode: SourceSynchronizationStateNode = _
  private var sourceEpochCollectionNode: SourceEpochCollection = _

  private var jobNode: JobNode = _
  private var jobConsumerCollectionNode: JobConsumerCollectionNode = _
  private var jobConsumerNode: JobConsumerNode = _

  private var consumerCollection: ConsumerCollection = _
  private var consumerNode: ConsumerNode = _
  private var consumerSyncState: SourceSynchronizationStateNode = _

  private var otherConsumerNode: ConsumerNode = _
  private var otherConsumerSyncState:SourceSynchronizationStateNode = _

  private var epochCollection: EpochCollectionNode = _

  private var source: KafkaSource[String,String,Object] = _

  private var completePromise = Promise[Unit]()

  override def beforeEach(): Unit = {
    super.beforeEach()
    subjectNode = mock[SubjectNode]

    sourceCollectionNode = mock[QuerySourceCollection]
    sourceNode = mock[QuerySourceNode]
    sourceSyncStateNode = mock[SourceSynchronizationStateNode]
    sourceEpochCollectionNode = mock[SourceEpochCollection]
    commandNode = mock[QuerySourceCommandNode]

    jobNode = mock[JobNode]
    jobConsumerCollectionNode = mock[JobConsumerCollectionNode]
    jobConsumerNode = mock[JobConsumerNode]

    consumerCollection = mock[ConsumerCollection]
    consumerNode = mock[ConsumerNode]
    consumerSyncState = mock[SourceSynchronizationStateNode]

    otherConsumerNode = mock[ConsumerNode]
    otherConsumerSyncState = mock[SourceSynchronizationStateNode]

    epochCollection = mock[EpochCollectionNode]

    source = mock[KafkaSource[String,String,Object]]

    when(subjectNode.getSources()) thenReturn sourceCollectionNode
    when(subjectNode.awaitClose()) thenReturn completePromise.future
    when(sourceCollectionNode.getChild(ArgumentMatchers.any[String]())) thenReturn sourceNode
    when(sourceNode.create(ArgumentMatchers.any())) thenReturn Future.successful(null)
    when(sourceNode.getSyncState()) thenReturn sourceSyncStateNode
    when(sourceNode.getEpochs()) thenReturn sourceEpochCollectionNode
    when(sourceNode.getCommandNode()) thenReturn commandNode
    when(sourceNode.exists()) thenReturn Future.successful(true)
    when(sourceNode.sync()) thenReturn Future.successful(())
    mockLock(sourceEpochCollectionNode)
    mockLock(sourceNode)

    when(sourceNode.getConsumers()) thenReturn consumerCollection
    when(consumerCollection.getChild(ArgumentMatchers.any[String]())) thenReturn consumerNode
    when(consumerCollection.getChildren()) thenReturn Future.successful(Iterable(consumerNode,otherConsumerNode))
    when(consumerNode.create(ArgumentMatchers.any())) thenReturn Future.successful(null)
    when(consumerNode.getSyncState()) thenReturn consumerSyncState

    when(consumerSyncState.setData(ArgumentMatchers.any())) thenReturn Future.successful(())

    when(otherConsumerNode.getSyncState()) thenReturn otherConsumerSyncState
    when(otherConsumerSyncState.setData(ArgumentMatchers.any())) thenReturn Future.successful(())


    when(subjectNode.getEpochs()) thenReturn epochCollection

    when(jobNode.getJobConsumerCollection()) thenReturn jobConsumerCollectionNode
    when(jobConsumerCollectionNode.getChild(ArgumentMatchers.any())) thenReturn jobConsumerNode

    when(jobConsumerNode.create()) thenReturn Future.successful[String]("")

    when(commandNode.observe()) thenReturn Subject[SourceCommand]()
  }

  def constructManager(): KafkaSourceManager = new KafkaSourceManager(source,subjectNode,jobNode,"sourceuuid", "instanceuuid")

  "KafkaSourceManager.InitalizeRun" should "construct consumer if needed" in {
    //Arrange
    val manager = constructManager()

    //Act
    manager.initializeRun()

    //Assert
    verify(consumerNode, times(1)).create(ArgumentMatchers.any())
    assert(true)
  }


  "KafkaSourceManager" should "invoke cancel on a kafkaSource when a subject completes" in async {
    //Arrange
    val manager = constructManager()
    //Manager is the class passing events to the kafka source
    manager.initializeRun()

    //Act
    completePromise.success(())

    //Assert
    await(manager.cancel)
    assert(manager.cancel.isCompleted)
  }

  "KafkaSourceManager.startedCatchingUp" should "set the consumer to catchingUp" in {
    //Arrange
    val manager = constructManager()

    //Act
    manager.startedCatchingUp()

    //Assert
    verify(consumerSyncState, times(1)).setData(SynchronizationState(KafkaSourceState.CatchingUp))
    assert(true)
  }


  "KafkaSourceManager.isCatchedUp" should "Return true if the passed offsets are all past the second-last epoch" in async {
    //Arrange
    val manager = constructManager()
    when(epochCollection.getLatestEpochId) thenReturn Future.successful(3L)
    val epoch = mock[EpochNode]
    when(epochCollection.getChild(2)) thenReturn epoch
    when(epoch.getData()) thenReturn Future.successful(Some(Epoch(2,Map(1->2L,2 -> 2L,3 -> 4L))))

    val comparison = Map(1-> 3L, 2->3L)

    //Act
    val r = await(manager.isCatchedUp(comparison))

    //Assert
    assert(r)
  }

  it should "Return true if the pre-last epoch does not exist" in async {
    //Arrange
    val manager = constructManager()
    when(epochCollection.getLatestEpochId) thenReturn Future.successful(0L)

    val comparison = Map(1-> 2L, 2->2L)

    //Act
    val r = await(manager.isCatchedUp(comparison))

    //Assert
    assert(r)
  }

  it should "Return false if for some partition the offset is not past the pre-last epoch" in async {
    //Arrange
    val manager = constructManager()
    when(epochCollection.getLatestEpochId) thenReturn Future.successful(3L)
    val epoch = mock[EpochNode]
    when(epochCollection.getChild(2)) thenReturn epoch
    when(epoch.getData()) thenReturn Future.successful(Some(Epoch(2,Map(1->2L,2->3L,3->3L))))

    val comparison = Map(1-> 2L, 2->2L)

    //Act
    val r = await(manager.isCatchedUp(comparison))

    //Assert
    assert(!r)
  }


  "KafkaSourceManager.notifyCatchedUp" should "Set the state of the considered consumer to ready" in async {
    //Arrange
    val manager = constructManager()
    when(sourceSyncStateNode.getData()) thenReturn  Future.successful(Some(SynchronizationState(KafkaSourceState.CatchingUp)))
    when(consumerSyncState.getData()) thenReturn  Future.successful(Some(SynchronizationState(KafkaSourceState.CatchingUp)))
    when(otherConsumerSyncState.getData()) thenReturn Future.successful(Some(SynchronizationState(KafkaSourceState.CatchingUp)))

    //Act
    await(manager.notifyCatchedUp())

    //Assert
    verify(consumerSyncState,times(1)).setData(SynchronizationState(KafkaSourceState.Ready))
    assert(true)
  }

  it should "set the state of the source to ready if all consumers are ready" in async {
    //Arrange
    val manager = constructManager()
    when(sourceSyncStateNode.getData()) thenReturn  Future.successful(Some(SynchronizationState(KafkaSourceState.CatchingUp)))
    when(consumerSyncState.getData()) thenReturn  Future.successful(Some(SynchronizationState(KafkaSourceState.Ready)))
    when(otherConsumerSyncState.getData()) thenReturn Future.successful(Some(SynchronizationState(KafkaSourceState.Ready)))

    //Act
    await(manager.notifyCatchedUp())

    //Assert
    verify(sourceSyncStateNode,times(1)).setData(SynchronizationState(KafkaSourceState.Ready))
    assert(true)
  }

  it should "not set the state of the source to ready if some consumer is not yet ready" in async {
    //Arrange
    val manager = constructManager()
    when(sourceSyncStateNode.getData()) thenReturn  Future.successful(Some(SynchronizationState(KafkaSourceState.CatchingUp)))
    when(consumerSyncState.getData()) thenReturn  Future.successful(Some(SynchronizationState(KafkaSourceState.Ready)))
    when(otherConsumerSyncState.getData()) thenReturn Future.successful(Some(SynchronizationState(KafkaSourceState.CatchingUp)))

    //Act
    await(manager.notifyCatchedUp())

    //Assert
    verify(sourceSyncStateNode,times(0)).setData(SynchronizationState(KafkaSourceState.Ready))
    assert(true)
  }

  "KafkaSourceManager.notifySynchronized" should "Set the state of the considered consumer to synchronized" in async {
    //Arrange
    val manager = constructManager()
    when(sourceSyncStateNode.getData()) thenReturn  Future.successful(Some(SynchronizationState(KafkaSourceState.Ready)))
    when(consumerSyncState.getData()) thenReturn  Future.successful(Some(SynchronizationState(KafkaSourceState.Ready)))
    when(otherConsumerSyncState.getData()) thenReturn Future.successful(Some(SynchronizationState(KafkaSourceState.Ready)))

    //Act
    await(manager.notifySynchronized())

    //Assert
    verify(consumerSyncState,times(1)).setData(SynchronizationState(KafkaSourceState.Synchronized))
    assert(true)
  }

  it should "set the state of the source to synchronized if all consumers are synchronized" in async {
    //Arrange
    val manager = constructManager()
    when(sourceSyncStateNode.getData()) thenReturn  Future.successful(Some(SynchronizationState(KafkaSourceState.Ready)))
    when(consumerSyncState.getData()) thenReturn  Future.successful(Some(SynchronizationState(KafkaSourceState.Synchronized)))
    when(otherConsumerSyncState.getData()) thenReturn Future.successful(Some(SynchronizationState(KafkaSourceState.Synchronized)))

    //Act
    await(manager.notifySynchronized())

    //Assert
    verify(sourceSyncStateNode,times(1)).setData(SynchronizationState(KafkaSourceState.Synchronized))
    assert(true)
  }

  it should "not set the state of the source to ready if some consumer is not yet ready" in async {
    //Arrange
    val manager = constructManager()
    when(sourceSyncStateNode.getData()) thenReturn  Future.successful(Some(SynchronizationState(KafkaSourceState.Ready)))
    when(consumerSyncState.getData()) thenReturn  Future.successful(Some(SynchronizationState(KafkaSourceState.Synchronized)))
    when(otherConsumerSyncState.getData()) thenReturn Future.successful(Some(SynchronizationState(KafkaSourceState.Ready)))

    //Act
    await(manager.notifySynchronized())

    //Assert
    verify(sourceSyncStateNode,times(0)).setData(SynchronizationState(KafkaSourceState.Synchronized))
    assert(true)
  }



  "NotifyStartedOnEpoch" should "update the latest epoch of the source" in async {
    //Arrange
    val manager = constructManager()
    when(sourceEpochCollectionNode.getLatestEpochId()) thenReturn Future.successful(0L)

    //Act
    await(manager.notifyStartedOnEpoch(1))

    //Assert
    verify(sourceEpochCollectionNode, times(1)).setData(EpochCollection(1))
    assert(true)
  }

  it should "do nothing if the latest epoch was alreadt set" in async {
    //Arrange
    val manager = constructManager()
    when(sourceEpochCollectionNode.getLatestEpochId()) thenReturn Future.successful(1L)

    //Act
    await(manager.notifyStartedOnEpoch(1))

    //Assert
    verify(sourceEpochCollectionNode, times(0)).setData(ArgumentMatchers.any())
    assert(true)
  }
}
