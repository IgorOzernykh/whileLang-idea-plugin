package com.intellij

import com.intellij.PsiElementComponent
import com.intellij.PsiTemplateGen
import com.intellij.SmartInsertPlace
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import com.intellij.whileLang.Printer
import com.intellij.whileLang.WhileFile
import org.jetbrains.format.FormatSet
import java.util.HashMap
import java.util.HashSet
import com.intellij.CommentConnectionUtils.VariantConstructionContext
import com.intellij.whileLang.WhileElementFactory
import java.util.ArrayList

/**
 * Created by Aleksei on 5/21/2015.
 */

public class WhileFileComponent(
        printer: Printer
): PsiElementComponent<WhileFile, SmartInsertPlace, PsiTemplateGen<WhileFile, SmartInsertPlace>>(printer)

{
    private fun getModuleVariants(
            p: WhileFile
            , context: VariantConstructionContext
    ): FormatSet {
        val module = p.getStmtList()
        if (module == null) { return printer.getEmptySet() }
        return printer.getVariants(module, context)
    }

    private fun getProcListVariants(
            p: WhileFile
            , context: VariantConstructionContext
    ): FormatSet {
        val proc = p.getProcList()
        if (proc == null) { return printer.getEmptySet() }
        return  printer.getVariants(proc, context)
    }

    override public fun getVariants(p: WhileFile, context: VariantConstructionContext): FormatSet {
        val list = ArrayList<FormatSet>()

        val procListVariants = getProcListVariants(p, context)
        if (procListVariants != null) {
            if (procListVariants.isEmpty()) { return printer.getEmptySet() }
            list.add(procListVariants)
        }

        val moduleVariants = getModuleVariants(p, context)
        if (moduleVariants != null) {
            if (moduleVariants.isEmpty()) { return printer.getEmptySet() }
            list.add(moduleVariants)
        }

        val firstElem = list.get(0)

        return list.drop(1).fold(firstElem) { r, e -> r % e }
    }

    override public fun getTmplt(p: WhileFile): PsiTemplateGen<WhileFile, SmartInsertPlace>? {
        return getTemplateFromElement(p)
    }

    override protected fun getNewElement(
            text: String
            , elementFactory: WhileElementFactory
    ): WhileFile? {
        return null
    }

    override protected fun updateSubtreeVariants(
            p       : WhileFile
            , tmplt   : PsiTemplateGen<WhileFile, SmartInsertPlace>
            , variants: Map<String, FormatSet>
            , context: VariantConstructionContext
    ): Map<String, FormatSet> {
        return variants
    }

    override protected fun prepareSubtreeVariants(
            p: WhileFile
            , context: VariantConstructionContext
    ): Map<String, FormatSet> = HashMap()

    override protected fun getTags(p: WhileFile): Set<String>  = HashSet()

    override protected fun isTemplateSuitable(
            p: WhileFile
            , tmplt: PsiTemplateGen<WhileFile, SmartInsertPlace>
    ): Boolean {
        return true
    }

    override public fun getTemplateFromElement(newP: WhileFile): PsiTemplateGen<WhileFile, SmartInsertPlace>? = null
}
