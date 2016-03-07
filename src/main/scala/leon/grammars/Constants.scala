/* Copyright 2009-2016 EPFL, Lausanne */

package leon
package grammars

import purescala.Expressions._
import purescala.Types.TypeTree
import purescala.ExprOps.collect
import purescala.Extractors.IsTyped

/** Generates constants found in an [[leon.purescala.Expressions.Expr]].
  * Some constants that are generated by other grammars (like 0, 1) will be excluded
  */
case class Constants(e: Expr) extends ExpressionGrammar[TypeTree] {

  private val excluded: Set[Expr] = Set(
    InfiniteIntegerLiteral(1),
    InfiniteIntegerLiteral(0),
    IntLiteral(1),
    IntLiteral(0),
    BooleanLiteral(true),
    BooleanLiteral(false)
  )

  def computeProductions(t: TypeTree)(implicit ctx: LeonContext): Seq[Prod] = {
    val literals = collect[Expr]{
      case IsTyped(l:Literal[_], `t`) => Set(l)
      case _ => Set()
    }(e)

    (literals -- excluded map (terminal(_, Tags.Constant))).toSeq
  }
}