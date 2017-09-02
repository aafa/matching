import java.nio.file.Paths

import Model._
import fs2._
import matching._

import scala.collection.concurrent.TrieMap

object Db {

  type ClientsMap = Map[ClientKey, Client]
  type OrdersMap  = TrieMap[OrderKey, Order]

  val clients: ClientsMap    = Map.empty[ClientKey, Client]
  val ordersQueue: OrdersMap = TrieMap.empty[OrderKey, Order]

}

object TsvParser{
  import Model._

  type Effect[A] = Task[A]
  type Result[T] = Stream[Effect, T]

  def tsvLines(fileName: String): Result[Seq[String]] = {
    val url = this.getClass.getResource(fileName).toURI
    io.file.readAll[Effect](Paths.get(url), 4096)
        .through(text.utf8Decode)
        .through(text.lines)
        .map(s => s.split("\t").toList)
  }

  def clients(fileName: String): Result[(ClientKey, Client)] =
    tsvLines(fileName)
      .collect {
        case name :: money :: a :: b :: c :: d :: Nil =>
          Client(name, money.toLong.ref, Asset(a.toLong,b.toLong,c.toLong,d.toLong).ref)
      }.map { client: Client => (client.name, client) }

  def orders(fileName: String): Result[(OrderKey, Order)] =
    tsvLines(fileName)
      .collect {
        case name :: deal :: asset :: price :: qty :: Nil =>
          Order(name, OrderType(deal), Asset(asset), price.toLong, qty.toLong)
      }.map { order: Order => (order.key, order) }

  println(clients("clients.txt").runLog.unsafeRunSync())
  println(orders("orders.txt").runLog.unsafeRunSync())

}