package leon
package laziness

import invariant.factories._
import invariant.util.Util._
import invariant.util._
import invariant.structure.FunctionUtils._
import purescala.ScalaPrinter
import purescala.Common._
import purescala.Definitions._
import purescala.Expressions._
import purescala.ExprOps._
import purescala.DefOps._
import purescala.Extractors._
import purescala.Types._
import leon.invariant.util.TypeUtil._
import leon.invariant.util.LetTupleSimplification._
import leon.verification.AnalysisPhase
import java.io.File
import java.io.FileWriter
import java.io.BufferedWriter
import scala.util.matching.Regex
import leon.purescala.PrettyPrinter
import leon.LeonContext
import leon.LeonOptionDef
import leon.Main
import leon.TransformationPhase
import LazinessUtil._

//case class ClosureData(tpe: TypeTree, absDef: AbstractClassDef, caseClass: Seq[CaseClassDef])

class LazyClosureFactory(p: Program) {
  val debug = false
  implicit val prog = p
  /**
   * Create a mapping from types to the lazyops that may produce a value of that type
   * TODO: relax that requirement that type parameters of return type of a function
   * lazy evaluated should include all of its type parameters
   */
  private val (tpeToADT, opToCaseClass) = {
    // collect all the operations that could be lazily evaluated.
    val lazyops = p.definedFunctions.flatMap {
      case fd if (fd.hasBody) =>
        filter(isLazyInvocation)(fd.body.get) map {
          case FunctionInvocation(_, Seq(FunctionInvocation(tfd, _))) => tfd.fd
        }
      case _ => Seq()
    }.distinct
    if (debug) {
      println("Lazy operations found: \n" + lazyops.map(_.id).mkString("\n"))
    }
    val tpeToLazyops = lazyops.groupBy(_.returnType)
    val tpeToAbsClass = tpeToLazyops.map(_._1).map { tpe =>
      val name = typeNameWOParams(tpe)
      val absTParams = getTypeParameters(tpe) map TypeParameterDef.apply
      // using tpe name below to avoid mismatches due to type parameters
      name -> AbstractClassDef(FreshIdentifier(typeNameToADTName(name), Untyped),
        absTParams, None)
    }.toMap
    var opToAdt = Map[FunDef, CaseClassDef]()
    val tpeToADT = tpeToLazyops map {
      case (tpe, ops) =>
        val name = typeNameWOParams(tpe)
        val absClass = tpeToAbsClass(name)
        val absTParams = absClass.tparams
        // create a case class for every operation
        val cdefs = ops map { opfd =>
          assert(opfd.tparams.size == absTParams.size)
          val absType = AbstractClassType(absClass, opfd.tparams.map(_.tp))
          println("Creating new case class with name: "+opNameToCCName(opfd.id.name))
          val classid = FreshIdentifier(opNameToCCName(opfd.id.name), Untyped)
          val cdef = CaseClassDef(classid, opfd.tparams, Some(absType), isCaseObject = false)
          val nfields = opfd.params.map { vd =>
            val fldType = vd.getType
            unwrapLazyType(fldType) match {
              case None =>
                ValDef(FreshIdentifier(vd.id.name, fldType))
              case Some(btype) =>
                val adtType = AbstractClassType(absClass, getTypeParameters(btype))
                ValDef(FreshIdentifier(vd.id.name, adtType))
            }
          }
          cdef.setFields(nfields)
          absClass.registerChild(cdef)
          opToAdt += (opfd -> cdef)
          cdef
        }
        println(s"$absClass --> ${ (cdefs).mkString("\n\t") }")
        (name -> (tpe, absClass, cdefs))
    }
    tpeToADT.foreach {
      case (k, v) => println(s"$k --> ${ (v._2 +: v._3).mkString("\n\t") }")
    }
    (tpeToADT, opToAdt)
  }

  // this fixes an ordering on lazy types
  lazy val lazyTypeNames = tpeToADT.keys.toSeq

  def allClosuresAndParents = tpeToADT.values.flatMap(v => v._2 +: v._3)

  def lazyType(tn: String) = tpeToADT(tn)._1

  def absClosureType(tn: String) = tpeToADT(tn)._2

  def closures(tn: String) = tpeToADT(tn)._3

  lazy val caseClassToOp = opToCaseClass map { case (k, v) => v -> k }

  def lazyopOfClosure(cl: CaseClassDef) = caseClassToOp(cl)

  def closureOfLazyOp(op: FunDef) = opToCaseClass(op)

  /**
   * Here, the lazy type name is recovered from the closure's name.
   * This avoids the use of additional maps.
   */
  def lazyTypeNameOfClosure(cl: CaseClassDef) =  adtNameToTypeName(cl.parent.get.classDef.id.name)
}
