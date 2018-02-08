package org.codefeedr.core.library.internal.zookeeper

import com.typesafe.scalalogging.LazyLogging
import rx.lang.scala.Observable

import scala.concurrent.ExecutionContext.Implicits.global
import scala.async.Async.{async, await}
import scala.concurrent.duration.{Duration, MILLISECONDS}
import scala.concurrent.{Future, Promise}

/**
  * Class managing scala collection state
  */
trait ZkCollectionStateNode[
    TChildNode <: ZkStateNode[TChild, TChildState], TChild, TChildState, TAggregateState]
    extends ZkCollectionNode[TChildNode]
    with LazyLogging {
  def getChildren(): Future[Iterable[TChildNode]]

  /**
    * Initial value of the aggreagate state before the fold
    * @return
    */
  def initial(): TAggregateState

  /**
    * Mapping from the child to the aggregate state
    * @param child
    * @return
    */
  def mapChild(child: TChildState): TAggregateState

  /**
    * Reduce operator of the aggregation
    * @param left
    * @param right
    * @return
    */
  def reduceAggregate(left: TAggregateState, right: TAggregateState): TAggregateState

  def getState(): Future[TAggregateState] = async {
    val consumerNodes = await(getChildren())
    val states = await(
      Future.sequence(
        consumerNodes.map(o => o.getStateNode().getData().map(o => mapChild(o.get))).toList))
    states.foldLeft(initial())(reduceAggregate)
  }

  /**
    * Returns a future that resolves when the given condition evaluates to true for all children
    * TODO: Find a better way to implement this
    * @param f condition to evaluate for each child
    * @return
    */
  def watchStateAggregate(f: TChildState => Boolean): Future[Boolean] = async {
    val p = Promise[Boolean]

    //Accumulator used
    def accumulator(current: List[String], element: (String, Boolean)) = {
      if (element._2) {
        current.filter(o => o != element._1)
      } else {
        if (!current.contains(element._1)) {
          current ++ List[String](element._1)
        } else {
          current
        }
      }
    }

    //First obtain the initial state
    val childNodes = await(getChildren())
    val initialState = await(
      Future.sequence(
        childNodes
          .map(child => child.getStateNode().getData().map(state => (child.name, !f(state.get))))
          .toList))
      .filter(o => o._2)
      .map(o => o._1)

    logger.debug(s"initial state: $initialState")

    //Once the initial state of all children is obtained, start watching
    val subscription = observeNewChildren()
      .flatMap(
        o =>
          o.getStateNode()
            .observeData()
            .map(state => (o.name, f(state))) ++ Observable.just((o.name, true)))
      .map(o => { logger.debug(s"got event ${o}"); o })
      .scan[List[String]](initialState)(accumulator)
      .map(o => { logger.debug(s"Current state: $o"); o })
      .map(o => o.isEmpty)
      .map(o => { logger.debug(s"Current state after throttle: $o"); o })
      .subscribe(o => if (o) p.success(true), error => p.failure(error), () => p.success(false))

    //unsubscribe on comlete if needed
    p.future.onComplete(o => if (o.get) subscription.unsubscribe())
    await(p.future)
  }
}
