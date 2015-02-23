package tools
/**
 * Created by Eric on 25.11.2014.
 */

import scala.meta.Tree
import scala.meta._
import org.scalameta.adt._


/**
 * Macro generated traversers
 * On the way of finding the most efficient one
 * */

class TraverserTableTag{

  val myTable = TraverserTableTag.buildTraverseTable

  def traverse(tree: Tree): Unit = myTable(tree.internalTag)(tree, traverse _)
}

object TraverserTableTag {
  def buildTraverseTable: Array[(Tree, Tree => Unit) => Unit] = TraverserBuilder.buildTraverseTable[Tree]
}

/*Change the order of the pattern mathch, as in the scalac traverser*/
class Traverser extends TraverserTableTag {
  import scala.meta.internal.ast._

  def traverseType(tree: Type): Unit =  tree match {
    case  _ : Type.Name |_: Type.Apply =>
    case _ => super.traverse(tree)
  }

  def traverseTerm(tree: Term): Unit = tree match {
    case _: Term.Name =>
    case Term.ApplyInfix(lhs, op, targs, args) =>
      traverseTerm(lhs)
      traverseTerm(op)
      targs.foreach(traverseType(_))
      args.foreach(traverse(_))
    case Term.Select(qual, selector) =>
      traverseTerm(qual)
      traverseTerm(selector)
    case Term.Match(scrut, cases) =>
      traverseTerm(scrut)
      cases.foreach(traverse(_))
    case _ => super.traverse(tree)
  }

  override def traverse(tree: scala.meta.Tree) = tree match {
    case _: Term.Name | _ : Lit.Char | _ : Lit.Int | _ : Type.Name | _: Term.Param | _: Type.Apply =>
    case _ : Pat.Wildcard =>
    case Case(pat, conds, block) =>
      traverse(pat)
      conds.foreach(traverseTerm(_))
      block.stats.foreach(traverse(_))
    case Pat.Extract(ref, targs, elements) =>
      traverseTerm(ref)
      targs.foreach(traverse(_))
      elements.foreach(traverse(_))
    case _ => super.traverse(tree)
  }
}