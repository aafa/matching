import java.util.Currency

import scala.collection.concurrent.TrieMap
import scala.collection.immutable
import scala.concurrent.stm._
import scala.util.{Failure, Success, Try}

object Matcher {
  import Helpers._
  import Model._

  type ClientsMap = TrieMap[ClientKey, Client]
  type OrdersMap  = TrieMap[OrderKey, Order]

  val clients: ClientsMap    = TrieMap.empty[ClientKey, Client]
  val ordersQueue: OrdersMap = TrieMap.empty[OrderKey, Order]

  implicit class ClientsHelper(clients: ClientsMap) {
    def transactOrders(t: Transaction): Try[Transaction] = {
      atomic { implicit trx =>
        val triedClientUpdates: Try[(ClientKey, Client)] = t.orders.map { o =>
          clients.tryUpdate(o.clientName)(_.update(o))
        }.reduce(_ orElse _)

        triedClientUpdates match {
          case Failure(exception) => Failure(exception)
          case Success(_) => Success(t)
        }
      }
    }
  }

  implicit class OrderQueueHelper(queue: OrdersMap) {
    def findMatching(order: Order): Option[Order] =
      queue
        .get(order.key)
        .filter(_.asset == order.asset)
        .filter(_.orderType == order.orderType.opposite)
        .filter(_.qty == order.qty) // todo > if selling, otherwise if buying

    def modify(order: Order): Unit =
      ordersQueue.remove(order.key) // todo ??? accumulative - OR + to the order amount
  }

  def process(orders: Vector[Order], clients: ClientsMap): Try[ClientsMap] = {
    def transactions: Seq[ClientsMap => Try[ClientsMap]] = orders.map { inputOrder: Order =>
      val maybeMatchingOrder = ordersQueue.findMatching(inputOrder)
      maybeMatchingOrder match {
        case Some(matchingOrder) =>
          ordersQueue.modify(matchingOrder)
          (cm: ClientsMap) =>
            {
              cm.transactOrders(Transaction(Vector(matchingOrder, inputOrder))).fold(Failure(_), _ => Success(cm))
            }
        case None =>
          ordersQueue.put(inputOrder.key, inputOrder)
          (cm: ClientsMap) =>
            Success(cm) // effectively no-op
      }
    }

    transactions.foldLeft(Try(clients))(
      (tryClients: Try[ClientsMap], mapToTriedMap: (ClientsMap) => Try[ClientsMap]) =>
        tryClients.flatMap(mapToTriedMap))
  }
}

object Main extends App {}
