import scala.collection.concurrent.TrieMap
import scala.util.{Failure, Success, Try}
import scala.concurrent.stm._
import scala.concurrent.stm.Txn.UncaughtExceptionCause

sealed trait FailedOperation extends Exception
case class NotEnoughAssets() extends FailedOperation // todo detailed message
case class NotEnoughFunds()  extends FailedOperation // todo detailed message

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
                    money: Ref[Money],
                    assets: TrieMap[Asset, Ref[Long]],
  ) {
    def update(order: Order)(implicit txn: InTxn): Try[Client] = {
      for {
        updatedMoney <- order.adjustMoney(money, order.totalPrice)
        _ <- assets.tryCreateOrUpdate(order.asset)(qty => order.adjustAssets(qty, order.qty))(
          Ref(order.qty))
      } yield this.copy(money = updatedMoney, assets = assets)
    }

    override def toString: ClientKey = atomic { implicit txn =>
      s"$name has ${money()} and ${assets.map {
        case (asset: Asset, value: Ref[Money]) => s"$asset(${value()})"
      }}"
    }
  }

  case class Transaction(orders: Vector[Order]) {
    // todo check if all orders are matched and transaction is valid again?
    def apply(orders: Order*): Transaction = Transaction(Vector(orders: _*))
  }

  case class Order(clientName: ClientKey,
                   orderType: OrderType,
                   asset: Asset,
                   price: Money,
                   qty: Long) {
    def key: OrderKey     = (asset, price)
    def totalPrice: Money = price * qty

    sealed trait GainOrSpend
    case object Gain  extends GainOrSpend
    case object Spend extends GainOrSpend

    def tryAdjust(total: Ref[Long], adjustment: Long, gs: GainOrSpend, failure: FailedOperation)(
        implicit txn: InTxn): Try[Ref[Long]] =
      gs match {
        case Spend =>
          if ((total() - adjustment) > 0) {
            Success(Ref(total() - adjustment))
          } else { // here we both rolling back failed txn and collecting failed state to collect it later
            Txn.rollback(UncaughtExceptionCause(failure))
            Failure(failure)
          }
        case Gain => Success(Ref(total() + adjustment))
      }

    def adjustMoney(total: Ref[Money], adjustment: Money)(implicit txn: InTxn): Try[Ref[Money]] =
      this.orderType match {
        case Buy  => tryAdjust(total, adjustment, Spend, NotEnoughFunds())
        case Sell => tryAdjust(total, adjustment, Gain, NotEnoughFunds())
      }

    def adjustAssets(total: Ref[Long], adjustment: Long)(implicit txn: InTxn): Try[Ref[Long]] =
      this.orderType match {
        case Buy  => tryAdjust(total, adjustment, Gain, NotEnoughAssets())
        case Sell => tryAdjust(total, adjustment, Spend, NotEnoughAssets())
      }
  }

}
