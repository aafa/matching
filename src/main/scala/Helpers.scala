import scala.collection.concurrent.TrieMap
import scala.util.{Failure, Success, Try}

object Helpers {
  implicit class TrieHelper[Key, Value](trie: TrieMap[Key, Value]) {
    def tryUpdate(key: Key)(update: Value => Try[Value]): Try[(Key, Value)] =
      trie.get(key) match {
        case Some(value) =>
          update(value) match {
            case Failure(exception) => Failure(exception)
            case Success(updatedValue) =>
              trie.put(key, updatedValue)
              Success((key, updatedValue))
          }
        case None =>
          Failure(new IllegalArgumentException)
      }

    def tryCreateOrUpdate(key: Key)(update: Value => Try[Value])(
      create: => Value): Try[(Key, Value)] = {
      trie.get(key) match {
        case Some(_) => tryUpdate(key)(update)
        case None =>
          trie.put(key, create)
          Success((key, create))
      }
    }

  }
}
