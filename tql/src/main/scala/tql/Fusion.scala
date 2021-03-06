package tql


/**
 * Created by Eric on 21.12.2014.
 */

import scala.language.higherKinds
import MonoidEnhencer._

trait Fusion[T] { self: Traverser[T] with Combinators[T] =>

  /**
   * Abstract class to allow two strategy to fuse.
   * It is a system-f class in order to allow the fusion to different kind of strategy (topDown, bottomUp..) without
   * having to duplicate the whole thing each time.
   * */
  abstract class Fused[+A : Monoid, F[+U] <: Fused[U, F]](val m1: Matcher[A]) extends Matcher[A] {
    self : F[A] =>  //thanks http://blog.originate.com/blog/2014/02/27/types-inside-types-in-scala/
    /**
     * Exists to not have to use reflection with something like f.getClass.getConstructor[..]... inside aggregate
     * */
    def newInstance[B : Monoid](m: Matcher[B]): F[B]


    /**
     * Note for the following composition override: it is safe to have the @unchecked annotation here since we know that in F[?] ? will be B since:
     * 1) we accept a Matcher[B]
     * 2) F[B] <: Matcher[B]
     *
     * since we override aggregate we cannot have an implicit of type ClassTag[F[B]]. It would need to be inserted in the
     * argument list of aggregate.
     *
     * I would love change the return type to F[B] instead of Matcher[B] but the problem is that then what do we do
     * with input which cannot be composed in order to get a F[B] ?
     * */

    def composeFused[B >: A : Monoid](f: => F[B]) = f.newInstance(m1 aggregate f.m1)

    /**
     * strategy(a) + strategy(b) = strategy(a + b)
     * */
    override def aggregate[B >: A : Monoid](m2: => Matcher[B]): Matcher[B] = m2 match {
      case v: MappedFused[_, B, F] @unchecked => v.leftCompose(self)
      case v: FeedFused[_, B, F] @unchecked => v.leftCompose(self)
      case f: F[B] @unchecked => composeFused(f)
      case _=> super.aggregate(m2)
    }


    def composeResultsFused[B >: A : Monoid](f: => F[B]) = f.newInstance(m1 aggregateResults f.m1)

    /**
     * strategy(a) +> strategy(b) = strategy(a +> b)
     * */
    override def aggregateResults[B >: A : Monoid](m2: => Matcher[B]): Matcher[B] = m2 match {
      case v: MappedFused[_, B, F]  @unchecked => v.leftComposeResults(self)
      case v: FeedFused[_, B, F] @unchecked => v.leftComposeResults(self)
      case f: F[B] @unchecked => composeResultsFused(f)
      case _=> super.aggregateResults(m2)
    }


    def aggregateFused[B : Monoid, C >: A : Monoid](f: => F[B]): F[(C, B)] = f.newInstance(m1 tupledWith f.m1)

    /**
     *  strategy(a) ~ strategy(b) = strategy(a ~ b)
     * */
    override def tupledWith[B : Monoid, C >: A : Monoid](m2: => Matcher[B]): Matcher[(C, B)] = m2 match {
      case f: F[B] @unchecked => aggregateFused(f)
      case _=> super.tupledWith(m2)
    }

    def aggregateResultsFused[B : Monoid, C >: A : Monoid](f: => F[B]): F[(C, B)] =
      f.newInstance(m1 tupledResultsWith f.m1)
    /**
     *  strategy(a) tupledResultsWith strategy(b) = strategy(a tupledResultsWith b)
     * */
    override def tupledResultsWith[B : Monoid, C >: A : Monoid](m2: => Matcher[B]): Matcher[(C, B)] = m2 match {
      case f: F[B] @unchecked => aggregateResultsFused(f)
      case _=> super.tupledResultsWith(m2)
    }

    /**
     * strategy(x) map {a => b} + strategy(z) = strategy(x ~ z) map{case (a, z) => (b, z)}
     * */
    override def map[B](f: A => B): Matcher[B] = new MappedFused(self, f)

    override def feed[B : Monoid](m: => A => Matcher[B]): Matcher[B] = m match {
      case v : (A => F[B]) => new FeedFused(self, v)
      case v => super.feed(m)
    }

  }

  /**
   * When 'map' is applied to a strategy, this combinator ensures that the modification will only touch the
   * elements in that traversal and not mix with the other 'composed' traversal.
   * Sometimes this require us to use tupledWith or tupledResultsWith instead of aggregate or aggregateResults.
   * Some cases are handled in Fused (see 2), so all classes in this file are tightly coupled.
   *
   * There are several cases to consider (the example are presented with concrete traversal to be more easily readable):
   * 1)
   * topDown(collect{case Lit.Int(x) => x}) map(a => a.map(_ * 2)) +
   * topDown(collect{case Lit.Int(x) => x})
   * =>
   * topDown(collect{case Lit.Int(x) => x} ~ collect{case Lit.Int(x) => x}) map(case (a, b) => a.map(_ * 2) + b)
   *
   * 2)
   * topDown(collect{case Lit.Int(x) => x}) +
   * topDown(collect{case Lit.Int(x) => x}) map(a => a.map(_ * 2))
   * =>
   * topDown(collect{case Lit.Int(x) => x} ~ collect{case Lit.Int(x) => x}) map(case (a, b) => a + b.map(_ * 2))
   *
   * 3)
   * topDown(collect{case Lit.Int(x) => x}) map(a => a.map(_ * 2)) +
   * topDown(collect{case Lit.Int(x) => x}) map(a => a.map(_ * 3))
   * =>
   * topDown(collect{case Lit.Int(x) => x} ~ collect{case Lit.Int(x) => x})
   *   map{case (a, b) => a.map(_ * 2) + b.map(_ * 3)}
   *
   * 4)
   * topDown(collect{case Lit.Int(x) => x}) map(a => a.map(_ * 2)) +
   * topDown(collect{case Lit.Int(x) => x}) +
   * topDown(collect{case Lit.Int(x) => x})
   * =>
   * topDown(collect{case Lit.Int(x) => x} ~ (collect{case Lit.Int(x) => x} + collect{case Lit.Int(x) => x}))
   *  map(case (a, b) => a.map(_ * 2) + b)
   * */
  class MappedFused[A : Monoid, +B, F[+U] <: Fused[U, F]](val m1: F[A], val f: A => B) extends Matcher[B] {

    def apply(t: T) = for {
      (v, t) <- m1(t)
    } yield ((v, f(t)))

    //aggregate

    def composeWithMapped[U : Monoid, C >: B : Monoid](mf: MappedFused[U, C, F]) =
      new MappedFused(mf.m1 aggregateFused m1, (x: (U, A)) => M % mf.f(x._1) + f(x._2))

    def leftCompose[C >: B : Monoid](fused: F[C]) =
      new MappedFused(fused aggregateFused m1, (x: (C, A)) => M % x._1 + f(x._2))

    override def aggregate[C >: B : Monoid](m2: => Matcher[C]): Matcher[C] = m2 match {
      case v: MappedFused[_, C, F] @unchecked => v.composeWithMapped(this) //visitor pattern here I aaaaam
      case v: F[C] @unchecked => new MappedFused(m1 aggregateFused v, (x: (A, C)) => M[C](f(x._1)) + x._2)
      case _ => super.aggregate(m2)
    }

    //aggregateResults

    def composeResultsWithMapped[U : Monoid, C >: B : Monoid](mf: MappedFused[U, C, F]) =
      new MappedFused(mf.m1 aggregateResultsFused m1, (x: (U, A)) => M % mf.f(x._1) + f(x._2))

    def leftComposeResults[C >: B : Monoid](fused: F[C]) =
      new MappedFused(fused aggregateResultsFused m1, (x: (C, A)) => M % x._1 + f(x._2))

    override def aggregateResults[C >: B : Monoid](m2: => Matcher[C]): Matcher[C] = m2 match {
      case v: MappedFused[_, C, F] @unchecked => v.composeResultsWithMapped(this)
      case v: F[C] @unchecked => new MappedFused(m1 aggregateResultsFused v, (x: (A, C)) => M[C](f(x._1)) + x._2)
      case _ => super.aggregateResults(m2)
    }
  }

  /**
   * This allows to fuse feed combinators.
   *
   * 1) strategy(x) feed (y => strategy(z)) + strategy(w) = strategy(x) feed (y => strategy(z) + strategy(w))
   * 2) strategy(x) feed (y => strategy(z)) + strategy(v) feed (u => strategy(w)) =
   *    (strategy(x) ~ strategy(v)) feed {case (y,u) => strategy(z) + strategy(w)}
   */
  class FeedFused[A : Monoid, B : Monoid, F[+U] <: Fused[U, F]](val m1: F[A], val m2: A => F[B]) extends Matcher[B] {

    def apply(tree: T) = for {
      (t, v) <- m1(tree)
       t2    <- m2(v)(t)
    } yield t2

    //aggregate

    def leftCompose[C <: B : Monoid](fused: F[C]) =
      new FeedFused(m1, (x: A) => fused composeFused m2(x))

    override def aggregate[C >: B : Monoid](m: => Matcher[C]): Matcher[C] = m match {
      case v: FeedFused[A, C, F] @unchecked =>
        new FeedFused(m1 composeFused v.m1 , (x: A) => m2(x) composeFused v.m2(x))
      case v: F[C] @unchecked =>
        new FeedFused(m1, (x: A) => m2(x) composeFused v)
      case _ =>
        super.aggregate(m)
    }

    //aggregateResults

    def leftComposeResults[C <: B : Monoid](fused: F[C]) =
      new FeedFused(m1, (x: A) => fused composeResultsFused m2(x))

    override def aggregateResults[C >: B : Monoid](m: => Matcher[C]): Matcher[C] = m match {
      case v: FeedFused[A, C, F] @unchecked =>
        new FeedFused(m1 composeResultsFused v.m1 , (x: A) => m2(x) composeResultsFused v.m2(x))
      case v: F[C] @unchecked => new FeedFused(m1, (x: A) => m2(x) composeResultsFused v)
      case _ => super.aggregateResults(m)
    }
  }

  /**
   * Create a fuser for the TopDown strategy (topDown)
   * */
  class FusedTopDown[+A : Monoid](override val m1: Matcher[A]) extends Fused[A, FusedTopDown](m1) {
    def newInstance[B : Monoid](m: Matcher[B]) = new FusedTopDown[B](m)
    def apply(t: T) = (m1 + children(this)).apply(t)
  }

  override def topDown[A : Monoid](m: Matcher[A]): Matcher[A] = new FusedTopDown[A](m)

}