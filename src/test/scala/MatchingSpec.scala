import org.scalatest.{FunSpec, MustMatchers}
import Model._
import matching._

import scala.concurrent.stm.Txn.UncaughtExceptionCause
import scala.concurrent.stm._
import scala.util.{Failure, Success, Try}

class MatchingSpec extends FunSpec with MustMatchers {
  val matcher: Matcher = new Matcher(new Db {})

  def genClients = Map(
    "C1" -> Client("C1", 1000.ref, Asset(a = 10,b = 30).ref),
    "C2" -> Client("C2", 2000.ref, Asset(a = 40,c = 50).ref),
  )

  def genOrder: Order = Order("C1", Buy, A, 10, 5)

  it("should match simple orders") {
    val clientMap = genClients
    val orders = Vector(
      Order("C1", Buy, A, 10, 5),
      Order("C2", Sell, A, 10, 5),
      Order("C1", Buy, A, 10, 5),
      Order("C2", Sell, A, 10, 5),
      Order("C1", Buy, A, 10, 5), // not gonna be matched
    )

    val triedTransactions = matcher.process(orders, clientMap)

    clientMap.toString() must === (Map(
      "C1" -> Client("C1", (1000 - 100).ref, Asset(a = 20,b = 30).ref),
      "C2" -> Client("C2", (2000 + 100).ref, Asset(a = 30,c = 50).ref),
    ).toString()) // todo implement implicit matcher instead of lame string comparing like that
  }

  it ("try atomic NotEnoughFunds"){
    val clientMap = genClients
    Try{
      atomic{ implicit trx =>
        clientMap.head._2.money() = 1
        Txn.rollback(UncaughtExceptionCause(NotEnoughFunds(genOrder)))
      }
    } must === (Failure(NotEnoughFunds(genOrder)))

    clientMap.toString() must === (genClients.toString()) // unchanged \ rollbacked
  }

  it("should throw NotEnoughFunds"){
    val orders = Vector(
      Order("C1", Buy, A, 1000, 5),
      Order("C2", Sell, A, 1000, 5),
    )

    val (triedTransactions, clientMap) = matcher.process(orders, genClients)
    clientMap.toString() must === (genClients.toString()) // unchanged \ rollbacked
    triedTransactions must === (Seq(Failure(NotEnoughFunds(orders.head))))
  }

  it("should throw NotEnoughAssets"){
    val orders = Vector(
      Order("C1", Buy, A, 1, 500),
      Order("C2", Sell, A, 1, 500),
    )

    val (triedTransactions, clientMap) = matcher.process(orders, genClients)

    triedTransactions must === (Seq(Failure(NotEnoughAssets(orders.last))))
    clientMap.toString() must === (genClients.toString()) // unchanged \ rollbacked
  }

  it("should do a transaction and then append NotEnoughFunds"){
    val orders = Vector(
      Order("C1", Buy, A, 10, 5),
      Order("C2", Sell, A, 10, 5),
      Order("C1", Buy, A, 1000, 5),
      Order("C2", Sell, A, 1000, 5),
    )

    val (triedTransactions, clientMap) = matcher.process(orders, genClients)
    triedTransactions must === (Seq(
      Success(Transaction(orders.head, orders.tail.head)),
      Failure(NotEnoughFunds(Order("C1", Buy, A, 1000, 5)))))
  }

}
