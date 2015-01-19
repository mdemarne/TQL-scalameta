package tools

import tools.ScalaToTree.CompilerProxy

import scala.collection.mutable
import scala.meta._

/**
 * Created by Eric on 06.12.2014.
 */
object CollectStringsTraversers {

  def scalaTraverser(compiler: CompilerProxy) = new compiler.compiler.Traverser {
    import compiler.compiler._
    var varNames = Set[String]()
    override def traverse(tree: Tree): Unit = tree match {
      case Literal(Constant(v: String)) => varNames += v
      case _ => super.traverse(tree)
    }

    def apply(tree: Tree):Set[String] = {
      varNames = Set[String]()
      traverse(tree)
      varNames
    }
  }

  def scalaTransformerTraverser(compiler: CompilerProxy) = new compiler.compiler.Transformer {
    import compiler.compiler._
    var varNames = Set[String]()
    override def transform(tree: Tree): Tree = tree match {
      case x @ Literal(Constant(v: String)) =>
        varNames += v
        x
      case _ => super.transform(tree)
    }

    def apply(tree: Tree):Set[String] = {
      varNames = Set[String]()
      transform(tree)
      varNames
    }
  }


  def basicscalametaTraverser = new Traverser {
    var varNames = Set[String]()
    import scala.meta.internal.ast._
    override def traverse(tree: scala.meta.Tree) = tree match {
      case Lit.String(v) => varNames += v
      case _ => super.traverse(tree)
    }

    def apply(tree: scala.meta.Tree): Set[String] = {
      varNames = Set[String]()
      traverse(tree)
      varNames
    }
  }

  val scalametaTraverser = new TraverserTableTag {
    var varNames = Set[String]()
    import scala.meta.internal.ast._
    override def traverse(tree: scala.meta.Tree) = tree match {
      case Lit.String(v) => varNames += v
      case _ => super.traverse(tree)
    }

    def apply(tree: scala.meta.Tree): Set[String] = {
      varNames = Set[String]()
      traverse(tree)
      varNames
    }
  }

  val scalametaOptimzedTraverser = new OptimzedOrderTraverser {
    var varNames = Set[String]()
    import scala.meta.internal.ast._

    override def traverse(tree: scala.meta.Tree) = tree match {
      case Lit.String(v) => varNames += v
      case _ => super.traverse(tree)
    }

    def apply(tree: scala.meta.Tree): Set[String] = {
      varNames = Set[String]()
      traverse(tree)
      varNames
    }
  }



  val scalametaHandwritten = new HandWrittenScalaMeta {
    var varNames = Set[String]()
    import scala.meta.internal.ast._

    override def traverse(tree: scala.meta.Tree) = tree match {
      case Lit.String(v) =>  varNames += v
      case _ => super.traverse(tree)
    }

    def apply(tree: scala.meta.Tree): Set[String] = {
      varNames = Set[String]()
      traverse(tree)
      varNames
    }
  }
}
