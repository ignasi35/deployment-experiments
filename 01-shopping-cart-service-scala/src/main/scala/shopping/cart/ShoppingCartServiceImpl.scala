package shopping.cart

import scala.concurrent.Future

import akka.actor.typed.ActorSystem
import akka.cluster.typed.Cluster
import org.slf4j.LoggerFactory

class ShoppingCartServiceImpl(system: ActorSystem[_]) extends proto.ShoppingCartService {

  private val logger = LoggerFactory.getLogger(getClass)

  private val cluster = Cluster.get(system)

  override def addItem(in: proto.AddItemRequest): Future[proto.Cart] = {
    logger.info("addItem {} to cart {}", in.itemId, in.cartId)
    val msg = s"${in.itemId} - in node with roles [${cluster.selfMember.roles.mkString(", ")}]"
    Future.successful(proto.Cart(items = List(proto.Item(msg, in.quantity))))
  }

}
