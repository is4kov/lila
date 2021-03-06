package lila.timeline

import akka.actor._
import com.typesafe.config.Config

final class Env(
    config: Config,
    db: lila.db.Env,
    hub: lila.hub.Env,
    getFriendIds: String => Fu[Set[String]],
    getFollowerIds: String => Fu[Set[String]],
    lobbySocket: ActorSelection,
    renderer: ActorSelection,
    system: ActorSystem
) {

  private val CollectionEntry = config getString "collection.entry"
  private val CollectionUnsub = config getString "collection.unsub"
  private val UserDisplayMax = config getInt "user.display_max"
  private val UserActorName = config getString "user.actor.name"

  lazy val entryRepo = new EntryRepo(
    coll = entryColl,
    userMax = UserDisplayMax
  )

  system.actorOf(Props(new Push(
    lobbySocket = lobbySocket,
    renderer = renderer,
    getFriendIds = getFriendIds,
    getFollowerIds = getFollowerIds,
    unsubApi = unsubApi,
    entryRepo = entryRepo
  )), name = UserActorName)

  lazy val unsubApi = new UnsubApi(unsubColl)

  def isUnsub(channel: String)(userId: String): Fu[Boolean] =
    unsubApi.get(channel, userId)

  def status(channel: String)(userId: String): Fu[Option[Boolean]] =
    unsubApi.get(channel, userId) flatMap {
      case true => fuccess(Some(true)) // unsubed
      case false => entryRepo.channelUserIdRecentExists(channel, userId) map {
        case true => Some(false) // subed
        case false => None // not applicable
      }
    }

  private[timeline] lazy val entryColl = db(CollectionEntry)
  private[timeline] lazy val unsubColl = db(CollectionUnsub)
}

object Env {

  lazy val current = "timeline" boot new Env(
    config = lila.common.PlayApp loadConfig "timeline",
    db = lila.db.Env.current,
    hub = lila.hub.Env.current,
    getFriendIds = lila.relation.Env.current.api.fetchFriends,
    getFollowerIds = lila.relation.Env.current.api.fetchFollowersFromSecondary,
    lobbySocket = lila.hub.Env.current.socket.lobby,
    renderer = lila.hub.Env.current.actor.renderer,
    system = lila.common.PlayApp.system
  )
}
