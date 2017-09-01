import scala.collection.concurrent.TrieMap
import scala.concurrent.stm._
import scala.util.Try

object Matcher {
  import Model._

  type ClientsMap = Map[ClientKey, Client]
  type OrdersMap  = TrieMap[OrderKey, Order]

  val clients: ClientsMap    = Map.empty[ClientKey, Client]
  val ordersQueue: OrdersMap = TrieMap.empty[OrderKey, Order]

  implicit class ClientsHelper(clients: ClientsMap) {
    def transactOrders(t: Transaction): Try[Transaction] = Try {
      atomic { implicit trx =>
        t.orders.foreach { o =>
          val client = clients(o.clientName)
          client.update(o)
        }
        t
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

  def process(orders: Vector[Order], clients: ClientsMap): (Vector[Try[Transaction]], ClientsMap) = {
    def processTransactions(cm: ClientsMap): Vector[Try[Transaction]] =
      orders.foldLeft(Vector.empty[Try[Transaction]]) {
        case (triedTransactions: Vector[Try[Transaction]], inputOrder: Order) =>
          val maybeMatchingOrder = ordersQueue.findMatching(inputOrder)
          maybeMatchingOrder match {
            case Some(matchingOrder) =>
              ordersQueue.modify(matchingOrder)
              triedTransactions :+ cm.transactOrders(Transaction(matchingOrder, inputOrder))
            case None =>
              ordersQueue.put(inputOrder.key, inputOrder)
              triedTransactions
          }
      }

    (processTransactions(clients), clients)
  }
}

object Main extends App {}
