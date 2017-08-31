import java.util.Currency

import scala.collection.concurrent.TrieMap
import scala.util.{Failure, Success, Try}

sealed trait FailedOperation extends Exception
case class NotEnoughAssets() extends FailedOperation // todo detailed message
case class NotEnoughFunds() extends FailedOperation // todo detailed message

object Helpers {
  implicit class TrieHelper[Key, Value](trie: TrieMap[Key, Value]) {
    def tryUpdate(key: Key)(update: Value => Try[Value]): Try[TrieMap[Key, Value]] =
      trie.get(key) match {
        case Some(value) =>
          update(value) match {
            case Failure(exception) => Failure(exception)
            case Success(updatedValue) =>
              trie.put(key, updatedValue)
              Success(trie)
          }
        case None =>
          Failure(new IllegalArgumentException)
      }

    def tryCreateOrUpdate(key: Key)(update: Value => Try[Value])(
        create: => Value): Try[TrieMap[Key, Value]] = {
      trie.get(key) match {
        case Some(_) => tryUpdate(key)(update)
        case None =>
          trie.put(key, create)
          Success(trie)
      }
    }

  }
}

object Model {
  import Helpers._

  sealed trait OrderType {
    def opposite: OrderType = this match {
      case Buy  => Sell
      case Sell => Buy
    }
  }

  case object Buy  extends OrderType
  case object Sell extends OrderType

  type ClientKey = String
  type Money     = Long
  type OrderKey  = (Asset, Money)

  sealed trait Asset
  case object A extends Asset
  case object B extends Asset
  case object C extends Asset
  case object D extends Asset

  case class Client(name: ClientKey, // gonna be indexed field
                    money: Money,
                    assets: TrieMap[Asset, Int],
  ) {
    def update(order: Order): Try[Client] = {
      for {
        updatedMoney <- order.adjustMoney(money, order.totalPrice)
        updatedAssets <- assets.tryCreateOrUpdate(order.asset)(qty =>
          order.adjustAssets(qty, order.qty))(order.qty)
      } yield this.copy(money = updatedMoney, assets = updatedAssets)
    }
  }

  case class Order(clientName: ClientKey,
                   orderType: OrderType,
                   asset: Asset,
                   price: Money,
                   qty: Int) {
    def key: (Asset, Money) = (asset, price)
    def totalPrice: Money   = price * qty

    sealed trait GainOrSpend
    case object Gain extends GainOrSpend
    case object Spend extends GainOrSpend

    def tryAdjust[T](total: T, adjustment: T, gs: GainOrSpend, failure: FailedOperation)(implicit ev: Numeric[T]): Try[T] =
      gs match {
        case Spend =>
          if (ev.gt(ev.minus(total, adjustment), ev.zero)) { // todo import ops._
            Success(ev.minus(total, adjustment))
          } else {
            Failure(failure)
          }
        case Gain => Success(ev.plus(total, adjustment))
      }

    def adjustMoney(total: Money, adjustment: Money): Try[Money] = this.orderType match {
      case Buy => tryAdjust(total, adjustment, Spend, NotEnoughFunds())
      case Sell => tryAdjust(total, adjustment, Gain, NotEnoughFunds())
    }

    def adjustAssets(total: Int, adjustment: Int): Try[Int] = this.orderType match {
      case Buy => tryAdjust(total, adjustment, Gain, NotEnoughAssets())
      case Sell => tryAdjust(total, adjustment, Spend, NotEnoughAssets())
    }
  }


}

object Matcher {
  import Helpers._
  import Model._

  type ClientsMap = TrieMap[ClientKey, Client]
  type OrdersMap  = TrieMap[OrderKey, Order]

  val clients: ClientsMap    = TrieMap.empty[ClientKey, Client]
  val ordersQueue: OrdersMap = TrieMap.empty[OrderKey, Order]

  implicit class ClientsHelper(clients: ClientsMap) {
    def transactOrdersPair(order1: Order, order2: Order): Try[ClientsMap] = {
      clients.tryUpdate(order1.clientName)(_.update(order1))
      clients.tryUpdate(order2.clientName)(_.update(order2))
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
            cm.transactOrdersPair(matchingOrder, inputOrder)
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
