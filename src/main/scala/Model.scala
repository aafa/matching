import Model.Order

import scala.concurrent.stm._

sealed class FailedOperation(m: String) extends Exception(m)
case class NotEnoughAssets(order: Order) extends FailedOperation(s"${order.clientName} has not enough assets")
case class NotEnoughFunds(order: Order)  extends FailedOperation(s"${order.clientName} has not enough funds")
case object ParsingException  extends FailedOperation("")

object Model {

  sealed trait OrderType {
    def opposite: OrderType = this match {
      case Buy  => Sell
      case Sell => Buy
    }
  }

  object OrderType{
    def apply(s: String): OrderType = s match {
      case "b" => Buy
      case "s" => Sell
      case _ => throw ParsingException
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

  object Asset{
    def apply(s: String): Asset = s match {
      case "A" => A
      case "B" => B
      case "C" => C
      case "D" => D
      case _ => throw ParsingException
    }

    def apply(a: Long = 0, b: Long = 0, c: Long = 0, d: Long = 0): Map[Asset, Money] = Map[Asset,Long](
      A -> a, B -> b, C -> c, D -> d
    )
  }

  case class Client(name: ClientKey, // gonna be indexed field
                    money: Ref[Money],
                    assets: Ref[Map[Asset, Long]],
  ) {
    def update(order: Order)(implicit txn: InTxn): Unit = {
      {
        this.assets.transform { assetToLong: Map[Asset, Money] =>
          {
            def adjust(value: Long, qty: Long): Long = order.orderType match {
              case Buy => value + qty
              case Sell =>
                if (value - qty < 0) throw NotEnoughAssets(order)
                value - qty
            }

            assetToLong.get(order.asset) match {
              case Some(value) => assetToLong + (order.asset -> adjust(value, order.qty))
              case None        => assetToLong + (order.asset -> order.qty)
            }
          }
        }

        order.orderType match {
          case Buy =>
            this.money() -= order.totalPrice
            if (this.money() < 0) throw NotEnoughFunds(order)
          case Sell => this.money() += order.totalPrice
        }
      }
    }

    override def toString: ClientKey = atomic { implicit txn =>
      s"$name has ${money()} and ${assets().map {
        case (asset: Asset, value: Money) => s"$asset($value)"
      }}"
    }
  }

  case class Transaction(orders: Vector[Order]) {
    // todo check if all orders are matched and transaction is valid again?
  }

  object Transaction{
    def apply(orders: Order*): Transaction = Transaction(Vector(orders: _*))
  }

  case class Order(clientName: ClientKey,
                   orderType: OrderType,
                   asset: Asset,
                   price: Money,
                   qty: Long) {
    def key: OrderKey     = (asset, price)
    def totalPrice: Money = price * qty

  }

}
