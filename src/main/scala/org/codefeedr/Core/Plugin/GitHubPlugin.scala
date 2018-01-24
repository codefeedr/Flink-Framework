package org.codefeedr.Core.Plugin

import java.util.Date

import org.codefeedr.Core.input.GitHubSource
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import org.codefeedr.Core.Library.Internal.{AbstractPlugin, SubjectTypeFactory}
import org.codefeedr.Core.Library.SubjectFactory
import org.codefeedr.Model.SubjectType
import org.eclipse.egit.github.core.event.PushPayload

import scala.async.Async.{async, await}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.reflect.runtime.{universe => ru}
import scala.collection.JavaConversions._
import org.apache.flink.api.scala._
import org.codefeedr.Core.Plugin


//simplistic view of a push event
case class PushEvent(id : String, repo_name : String, commitSHAs: Array[String], created_at : Date)

class GitHubPlugin[PushEvent: ru.TypeTag: ClassTag](maxRequests : Integer = -1) extends AbstractPlugin {

  /**
    * Creates a new SubjectType.
    * @return
    */
  override def CreateSubjectType(): SubjectType = {
    return SubjectTypeFactory.getSubjectType[PushEvent]

  }

  def GetStream(env: StreamExecutionEnvironment) : DataStream[Plugin.PushEvent] = {
    val stream = env.addSource(new GitHubSource(maxRequests)).
      filter(_.getType == "PushEvent").
      map { event =>
        val commits = event.getPayload.asInstanceOf[PushPayload].getCommits
        val commitSHAs = commits.map(x => x.getSha).toList

        PushEvent(event.getId, event.getRepo.getName, commitSHAs.toArray, event.getCreatedAt)
      }

    stream
  }

  override def Compose(env: StreamExecutionEnvironment): Future[Unit] = async {
    val sink = await(SubjectFactory.GetSink[Plugin.PushEvent])
    val stream = GetStream(env)
    stream.addSink(sink)
  }
}
