package org.codefeedr.plugins.github.generate

import org.codefeedr.ghtorrent._
import org.codefeedr.plugins.BaseEventTimeGenerator
import org.codefeedr.plugins.github.generate.EventTimeImpl._
import org.codefeedr.util.Constants
import org.joda.time.DateTime

class UserGenerator(seed: Long, val staticEventTime: Option[DateTime] = None)
    extends BaseEventTimeGenerator[User](seed) {
  private val types = Array("TypeA", "TypeB")

  /**
    * Implement to generate a random value
    *
    * @return
    */
  override def generate(): User = User(
    id = nextInt(1000000),
    login = nextString(6),
    name = nextString(6),
    company = nextString(6),
    email = nextEmail,
    created_at = nextString(4),
    `type` = randomOf(types),
    fake = nextBoolean(),
    deleted = nextBoolean(),
    long = None,
    lat = None,
    country_code = Some(nextString(3)),
    state = Some(randomOf(Constants.states)),
    city = Some(nextString(6)),
    updated_at = nextDateTimeLong(),
    eventTime = getEventTime.getMillis
  )
}
