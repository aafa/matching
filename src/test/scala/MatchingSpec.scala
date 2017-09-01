import org.scalatest.{FunSpec, MustMatchers}
import Matcher._
import Model._

import scala.collection.concurrent.TrieMap
import scala.concurrent.stm._
import scala.util.{Failure, Success}

class MatchingSpec extends FunSpec with MustMatchers {

  implicit class ReachRef(i: Int){
    def ref: Ref[Long] = Ref(i)
  }

  def clients = TrieMap(
    "C1" -> Client("C1", 1000.ref, TrieMap(A -> 10.ref, B -> 30.ref)),
    "C2" -> Client("C2", 2000.ref, TrieMap(A -> 40.ref, C -> 50.ref)),
  )

  it("should match simple orders") {
    val orders = Vector(
      Order("C1", Buy, A, 10, 5),
      Order("C2", Sell, A, 10, 5),
      Order("C1", Buy, A, 10, 5),
      Order("C2", Sell, A, 10, 5),
      Order("C1", Buy, A, 10, 5), // not gonna be matched
    )

    Matcher.process(orders, clients).toString must === (Success(TrieMap(
      "C1" -> Client("C1", (1000 - 100).ref, TrieMap(A -> 20.ref, B -> 30.ref)),
      "C2" -> Client("C2", (2000 + 100).ref, TrieMap(A -> 30.ref, C -> 50.ref)),
    )).toString) // todo implement implicit matcher instead of lame string comparing like that
  }

  it("should throw NotEnoughFunds"){
    val orders = Vector(
      Order("C1", Buy, A, 1000, 5),
      Order("C2", Sell, A, 1000, 5),
    )

    Matcher.process(orders, clients) must === (Failure(NotEnoughFunds()))
  }

  it("should throw NotEnoughAssets"){
    val orders = Vector(
      Order("C1", Buy, A, 1, 500),
      Order("C2", Sell, A, 1, 500),
    )

    Matcher.process(orders, clients) must === (Failure(NotEnoughAssets()))
  }



}
