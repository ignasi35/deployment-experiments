package shopping.cart

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Success

import akka.Done
import akka.actor.typed.ActorSystem
import akka.projection.eventsourced.EventEnvelope
import akka.projection.scaladsl.Handler
import org.slf4j.LoggerFactory

object ItemPopularityProjectionHandler {
  val LogInterval = 10
}

class ItemPopularityProjectionHandler(tag: String, system: ActorSystem[_], repo: ItemPopularityRepository)
    extends Handler[EventEnvelope[ShoppingCart.Event]]() { // <1>

  private var logCounter: Int = 0
  private val log = LoggerFactory.getLogger(getClass)
  private implicit val ec: ExecutionContext = system.executionContext

  override def process(envelope: EventEnvelope[ShoppingCart.Event]): Future[Done] = { // <2>
    logItemCount(envelope.event)
    val processed = envelope.event match { // <3>
      case ShoppingCart.ItemAdded(_, itemId, quantity) =>
        repo.update(itemId, quantity)

      case ShoppingCart.ItemQuantityAdjusted(_, itemId, newQuantity, oldQuantity) =>
        repo.update(itemId, newQuantity - oldQuantity)

      case ShoppingCart.ItemRemoved(_, itemId, oldQuantity) =>
        repo.update(itemId, 0 - oldQuantity)

      case _: ShoppingCart.CheckedOut => Future.successful(Done)
    }
    processed.onComplete {
      case Success(_) => logItemCount(envelope.event)
      case _          => ()
    }
    processed
  }

  private def logItemCount(event: ShoppingCart.Event): Unit =
    event match {
      case itemEvent: ShoppingCart.ItemEvent =>
        logCounter += 1
        val itemId = itemEvent.itemId
        if (logCounter == ItemPopularityProjectionHandler.LogInterval) {
          logCounter = 0
          repo.getItem(itemId).foreach {
            case Some(count) =>
              log.info("ItemPopularityProjectionHandler({}) item popularity for '{}': [{}]", tag, itemId, count)
            case None =>
              log.info("ItemPopularityProjectionHandler({}) item popularity for '{}': [0]", tag, itemId)
          }
        }
      case _ => ()
    }

}
