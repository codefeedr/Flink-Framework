package org.codefeedr.plugins.github.generate

import org.codefeedr.ghtorrent._
import org.codefeedr.plugins.{BaseEventTimeGenerator, BaseSampleGenerator}
import org.joda.time.DateTime

class ProjectGenerator(seed: Long, val staticEventTime: Option[DateTime] = None)
    extends BaseEventTimeGenerator[Project](seed) {
  private val types = Array("TypeA", "TypeB")

  /**
    * Implement to generate a random value
    *
    * @return
    */
  override def generate(): Project = Project(
    id = nextInt(10000),
    url = nextString(16),
    owner_id = nextInt(1000000),
    description = nextString(200),
    language = nextString(6),
    created_at = nextDateTimeLong(),
    forked_from = nextInt(10000),
    deleted = nextBoolean(),
    updated_at = nextDateTimeLong(),
    eventTime = getEventTime.getMillis
  )
}
