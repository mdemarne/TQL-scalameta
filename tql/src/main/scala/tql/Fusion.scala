package tql

import scala.language.higherKinds

/**
 * Created by Eric on 21.12.2014.
 */
trait Fusion[T] { self: Traverser[T] with Combinators[T] =>

  /**
   * Abstract class to allow two strategy to fuse.
   * It is a system-f class in order to allow the fusion to different kind of strategy (down, up..) without
   * having to duplicate the whole thing each time.
   * */
  abstract class Fused[A : Monoid, F[A] <: Fused[A, F]](val m1: Matcher[A]) extends Matcher[A] {

    /**
     * Exists to not have to use reflection with something like f.getClass.getConstructor[..]... inside compose
     * */
    def newInstance[B : Monoid](m: Matcher[B]): F[B]

    /**
     * strategy(a) + strategy(b) = strategy(a + b)
     * */
    override def compose[B >: A : Monoid](m2: => Matcher[B]): Matcher[B] = m2 match {
      case f: F[B] => newInstance(m1 compose f.m1)
      case _=> super.compose(m2)
    }
  }

  /**
   * Create a fuser for the TopDown strategy (down)
   * */
  class FusedTopDown[A : Monoid](override val m1: Matcher[A]) extends Fused[A, FusedTopDown](m1) {
    def newInstance[B : Monoid](m: Matcher[B]) = new FusedTopDown[B](m)
    def apply(t: T) = (m1 + children(this)).apply(t)
  }

  override def down[A : Monoid](m: Matcher[A]): Matcher[A] = new FusedTopDown[A](m)

}
