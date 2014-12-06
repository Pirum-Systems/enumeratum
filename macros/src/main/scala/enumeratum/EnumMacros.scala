package enumeratum

import scala.reflect.macros.Context // TODO switch to blackbox.Context when dropping support for 2.10.x

object EnumMacros {

  def findValuesImpl[A: c.WeakTypeTag](c: Context): c.Expr[Set[A]] = {
    import c.universe._
    val resultType = implicitly[c.WeakTypeTag[A]].tpe
    val typeSymbol = weakTypeOf[A].typeSymbol
    validateType(c)(typeSymbol)
    val subclassSymbols = enclosedSubClasses(c)(typeSymbol)
    c.Expr[Set[A]](q"Set[${tq"$resultType"}](..${subclassSymbols.map(s => Ident(s))})")
  }

  private def validateType(c: Context)(typeSymbol: c.universe.Symbol): Unit = {
    if (!typeSymbol.asClass.isTrait || !typeSymbol.asClass.isSealed)
      c.abort(
        c.enclosingPosition,
        "You can only use findValues on sealed traits"
      )
  }

  private def enclosedSubClasses(c: Context)(typeSymbol: c.universe.Symbol): Seq[c.universe.Symbol] = {
    import c.universe._
    val enclosingBodySubclasses: List[Symbol] = try {
      /*
        When moving beyond 2.11, we should use this instead, because enclosingClass will be deprecated.

        val enclosingModuleMembers = c.internal.enclosingOwner.owner.typeSignature.decls.toList
        enclosingModuleMembers.filter { x =>
          try (x.asModule.moduleClass.asClass.baseClasses.contains(typeSymbol)) catch { case _: Throwable => false }
        }

        Unfortunately, 2.10.x does not support .enclosingOwner :P
      */
      val enclosingModule = c.enclosingClass match {
        case md @ ModuleDef(_, _, _) => md
        case _ => c.abort(c.enclosingPosition,
          "The enum (i.e. the class containing the case objects and the call to `findValues`) must be an object")
      }
      enclosingModule.impl.body.filter { x =>
        try { x.symbol.asModule.moduleClass.asClass.baseClasses.contains(typeSymbol) } catch { case _: Throwable => false }
      }.map(_.symbol)
    } catch { case e: Throwable => c.abort(c.enclosingPosition, s"Unexpected error: ${e.getMessage}") }
    if (!enclosingBodySubclasses.forall(x => x.isModule))
      c.abort(c.enclosingPosition, "All subclasses must be objects.")
    else enclosingBodySubclasses
  }

}
