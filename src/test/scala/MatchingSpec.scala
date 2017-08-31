import org.scalatest.{FunSpec, MustMatchers}
import Matcher._
import Model._

import scala.collection.concurrent.TrieMap
import scala.util.Success

class MatchingSpec extends FunSpec with MustMatchers {
  it("should match simple orders") {
    val clients = TrieMap(
      "C1" -> Client("C1", 1000, TrieMap(A -> 10, B -> 30)),
      "C2" -> Client("C2", 2000, TrieMap(A -> 40, C -> 50)),
    )

    val orders = Vector(
      Order("C1", Buy, A, 10, 5),
      Order("C2", Sell, A, 10, 5),
      Order("C1", Buy, A, 10, 5),
      Order("C2", Sell, A, 10, 5),
      Order("C1", Buy, A, 10, 5), // not gonna be matched
    )

    Matcher.process(orders, clients) must === (Success(TrieMap(
      "C1" -> Client("C1", 1000 - 100, TrieMap(A -> 20, B -> 30)),
      "C2" -> Client("C2", 2000 + 100, TrieMap(A -> 30, C -> 50)),
    )))
  }
}
