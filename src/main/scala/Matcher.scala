import scala.concurrent.stm.atomic
import scala.util.Try

class Matcher(val db: Db) {
  import Model._

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
      db.ordersQueue.remove(order.key) // todo ??? accumulative - OR + to the order amount
  }

  def process(orders: Vector[Order], clients: ClientsMap): (Vector[Try[Transaction]], ClientsMap) = {
    def processTransactions(cm: ClientsMap): Vector[Try[Transaction]] =
      orders.foldLeft(Vector.empty[Try[Transaction]]) {
        case (triedTransactions: Vector[Try[Transaction]], inputOrder: Order) =>
          val maybeMatchingOrder = db.ordersQueue.findMatching(inputOrder)
          maybeMatchingOrder match {
            case Some(matchingOrder) =>
              db.ordersQueue.modify(matchingOrder)
              triedTransactions :+ cm.transactOrders(Transaction(matchingOrder, inputOrder))
            case None =>
              db.ordersQueue.put(inputOrder.key, inputOrder)
              triedTransactions
          }
      }

    (processTransactions(clients), clients)
  }
}