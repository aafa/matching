import Model.Asset

import scala.concurrent.stm.Ref

package object matching {

  implicit class ReachRefLong(i: Long){
    def ref: Ref[Long] = Ref(i)
  }

  implicit class ReachRef(i: Int){
    def ref: Ref[Long] = Ref(i)
  }

  implicit class ReachMapRef(m: Map[Asset,Long]){
    def ref: Ref[Map[Asset,Long]] = Ref(m)
  }

}
