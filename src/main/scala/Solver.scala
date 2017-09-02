import Model._

import scala.concurrent.stm._

object Solver extends App {
  import Helpers._

  val matcher: Matcher = new Matcher(new Db {})

  private val orders: Vector[Model.Order] = TsvParser.orders.runLog.unsafeRun()

  private val clients: ClientsMap =
    TsvParser.clients.runLog.unsafeRun().foldLeft(Map.empty[ClientKey, Client]) {
      case (acc: Map[ClientKey, Client], tuple: Client) => acc + (tuple.name -> tuple)
    }

  println("clients before")
  printClients(clients)

  val (triedTransactions, clientMap) = matcher.process(orders, clients)

  println
  println("transactions")
  triedTransactions.foreach{println(_)}

  println
  println("orders left")
  matcher.db.ordersQueue.values.toVector.foreach(println(_))

  println
  println("clients after")
  printClients(clientMap)
}

object Helpers {
  def printClients(clients: ClientsMap): Unit = atomic { implicit trx =>
    val vector = clients.values.toVector
    vector.sortBy(_.name).foreach { c =>
      println(
        "%s\t%s\t%s\t%s\t%s\t%s\t".format(c.name,
                                          c.money(),
                                          c.assets().get(A).get,
                                          c.assets().get(B).get,
                                          c.assets().get(C).get,
                                          c.assets().get(D).get))
    }

    println(
      "%s\t%s\t%s\t%s\t%s\t%s\t".format(
        "checksum",
        vector.map(_.money()).sum,
        vector.map(_.assets().get(A).get).sum,
        vector.map(_.assets().get(B).get).sum,
        vector.map(_.assets().get(C).get).sum,
        vector.map(_.assets().get(D).get).sum
      ))
  }
}
