package shopping.analytics

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal
import akka.Done
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.kafka.CommitterSettings
import akka.kafka.ConsumerSettings
import akka.kafka.Subscriptions
import akka.kafka.scaladsl.{ Committer, Consumer, DiscoverySupport }
import akka.stream.scaladsl.RestartSource
import com.google.protobuf.any.{ Any => ScalaPBAny }
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory
import sample.shoppingcart.proto

object ShoppingCartEventConsumer {

  private val log = LoggerFactory.getLogger("shopping.analytics.ShoppingCartEventConsumer")

  def init(system: ActorSystem[_]): Unit = {
    implicit val sys: ActorSystem[_] = system
    implicit val ec: ExecutionContext = system.executionContext

    val topic = system.settings.config.getString("shopping-analytics-service.shopping-cart-kafka-topic")
    val config = system.settings.config.getConfig("shopping-analytics-service.kafka.consumer")
    val consumerSettings =
      ConsumerSettings(config, new StringDeserializer, new ByteArrayDeserializer)
        .withBootstrapServers(system.settings.config.getString("shopping-analytics-service.kafka.bootstrap-servers"))
        .withGroupId("shopping-cart-analytics")
    val committerSettings = CommitterSettings(system)

    RestartSource // <3>
      .onFailuresWithBackoff(minBackoff = 1.second, maxBackoff = 30.seconds, randomFactor = 0.1) { () =>
        Consumer
          .committableSource(consumerSettings, Subscriptions.topics(topic)) // <1>
          .mapAsync(1) { msg =>
            handleRecord(msg.record).map(_ => msg.committableOffset)
          }
          .via(Committer.flow(committerSettings)) // <2>
      }
      .run()
  }

  private def handleRecord(record: ConsumerRecord[String, Array[Byte]]): Future[Done] = {
    val bytes = record.value()
    val x = ScalaPBAny.parseFrom(bytes) // <4>
    val typeUrl = x.typeUrl
    try {
      val inputBytes = x.value.newCodedInput()
      val event =
        typeUrl match {
          case "shopping-cart-service/shoppingcart.ItemAdded" =>
            proto.ItemAdded.parseFrom(inputBytes)
          case "shopping-cart-service/shoppingcart.ItemQuantityAdjusted" =>
            proto.ItemQuantityAdjusted.parseFrom(inputBytes)
          case "shopping-cart-service/shoppingcart.ItemRemoved" =>
            proto.ItemRemoved.parseFrom(inputBytes)
          case "shopping-cart-service/shoppingcart.CheckedOut" =>
            proto.CheckedOut.parseFrom(inputBytes)
          case _ =>
            throw new IllegalArgumentException(s"unknown record type [$typeUrl]")
        }

      event match {
        case proto.ItemAdded(cartId, itemId, quantity, _) =>
          log.info("ItemAdded: {} {} to cart {}", quantity, itemId, cartId)
        case proto.ItemQuantityAdjusted(cartId, itemId, quantity, _) =>
          log.info("ItemQuantityAdjusted: {} {} to cart {}", quantity, itemId, cartId)
        case proto.ItemRemoved(cartId, itemId, _) =>
          log.info("ItemQuantityAdjusted: {} removed from cart {}", itemId, cartId)
        case proto.CheckedOut(cartId, _) =>
          log.info("CheckedOut: cart {} checked out", cartId)
      }

      Future.successful(Done)
    } catch {
      case NonFatal(e) =>
        log.error("Could not process event of type [{}]", typeUrl, e)
        // continue with next
        Future.successful(Done)
    }
  }

}
