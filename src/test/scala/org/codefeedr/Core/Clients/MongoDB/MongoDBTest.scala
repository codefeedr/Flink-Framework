package org.codefeedr.core.clients.MongoDB

import org.codefeedr.core.clients.GitHub.GitHubProtocol.PushEvent
import org.scalatest.tagobjects.Slow
import org.scalatest.{AsyncFlatSpec, Matchers}

class MongoDBTest extends AsyncFlatSpec with Matchers {

  "MongoDB" should "return the correct collection based on its name and type" taggedAs (Slow) in {
    val mongo = new MongoDB()
    val collection = mongo.getCollection[PushEvent]("github_events")

    collection should not be null
  }

}
