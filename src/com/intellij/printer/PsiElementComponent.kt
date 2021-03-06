package com.intellij;

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElementFactory
import org.jetbrains.format.Format
import java.util.ArrayList
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiDoWhileStatement
import com.intellij.psi.PsiWhileStatement
import com.intellij.psi.PsiForStatement
import com.intellij.psi.PsiIfStatement
import com.intellij.psi.PsiBlockStatement
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiLoopStatement
import com.intellij.psi.PsiSwitchStatement
import com.intellij.psi.PsiSynchronizedStatement
import java.util.HashMap
import com.intellij.psi.PsiLambdaExpression
import com.intellij.psi.PsiClassInitializer
import java.util.Comparator

import com.intellij.psi.PsiVariable
import com.intellij.psi.PsiDeclarationStatement
import java.util.HashSet
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiParameterList
import com.intellij.psi.PsiSwitchLabelStatement
import com.intellij.psi.PsiForeachStatement
import com.intellij.psi.PsiTryStatement
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiStatement
import com.intellij.whileLang.Printer
import org.jetbrains.format.util.toLines
import org.jetbrains.format.FormatSet
import com.intellij.CommentConnectionUtils.VariantConstructionContext
import com.intellij.whileLang.WhileElementFactory

/**
 * User: anlun
 */
abstract public class PsiElementComponent<ET: PsiElement, IPT: SmartInsertPlace, T: Template<IPT>>(
        val printer: Printer
): FullConstructionUtils, FormatListFillUtils {
    open public fun getTmplt(p: ET): T? {
        val text = p.deleteSpaces(true) //deleting leading comments

        val elementFactory = WhileElementFactory(printer.getProject())
        if (elementFactory == null) { return null }
        try {
            val newElement = getNewElement(text, elementFactory)
            if (newElement == null) { return null }
            val containsComment = newElement.getChildren().any() { ch -> ch is PsiComment }
            if (containsComment) { return null }

            return getTemplateFromElement(newElement)
        } catch (e: ClassCastException) { return null }
    }

    abstract protected fun getNewElement         (text: String, elementFactory: WhileElementFactory): ET?
    abstract public    fun getTemplateFromElement(newP: ET): T?

    open public fun getAndSaveTemplate(newP: ET) {
        val template = getTmplt(newP); if (template == null) { return }
        val templateString = template.toString()
        val value = templateStringSet.get(templateString)
        if (value != null) { templateStringSet.put(templateString, value + 1); return }

        templateStringSet.put(templateString, 1)
        templates_1.add(template)
    }
    protected val templateStringSet: HashMap<String, Int> = HashMap()
    protected val templates_1      : ArrayList<T>         = ArrayList()

    open public fun getVariants(p: ET, context: VariantConstructionContext): FormatSet {
        val subtreeVariants = prepareSubtreeVariants(p, context)
        val pTagSet = getTags(p)

        val resultSet = printer.getEmptySet()
        val templates = getTemplates()
        for (tmplt in templates) {
            if (!isTemplateSuitable(p, tmplt)) { continue }
            val tmpltTagSet = tmplt.insertPlaceMap.keySet()
            if (!tmpltTagSet.equals(pTagSet)) { continue }

            val tmpltSubtreeVariants = updateSubtreeVariants(p, tmplt, subtreeVariants, context)
            val newFmtSet = getVariants(tmplt.text, tmplt.insertPlaceMap, tmpltSubtreeVariants)
            if (newFmtSet == null) { continue }
            resultSet.addAll(newFmtSet)
        }
        return resultSet
    }

    abstract protected fun getTags(p: ET): Set<String>

    abstract protected fun prepareSubtreeVariants(p: ET, context: VariantConstructionContext): Map<String, FormatSet>
    abstract protected fun updateSubtreeVariants(
              p       : ET
            , tmplt   : T
            , variants: Map<String, FormatSet>
            , context: VariantConstructionContext
    ): Map<String, FormatSet>

    abstract protected fun isTemplateSuitable(p: ET, tmplt: T): Boolean

    public fun getVariants<IPT: SmartInsertPlace>(
              text          : String
            , insertPlaceMap: Map<String, IPT>
            , variants      : Map<String, FormatSet>
    ): FormatSet? {
        val formatListsWithRanges = fillVariantsToInsertPlaceList(insertPlaceMap, variants)
        if (formatListsWithRanges == null) { return null }
        return insertToText(printer.getMaxWidth(), text, formatListsWithRanges)
    }

    public fun getVariant_SingleFormat<IPT: SmartInsertPlace>(
              text          : String
            , insertPlaceMap: Map<String, IPT>
            , variants      : Map<String, Format>
    ): Format? {
        val formatListsWithRanges = fillVariantsToInsertPlaceList_SingleFormat(insertPlaceMap, variants)
        if (formatListsWithRanges == null) { return null }
        return insertFormatsToText(text, formatListsWithRanges)
    }

    open protected fun getTemplates(): List<T> = templates_1

    /** CODE BLOCK PART **/
    public class CodeBlockSpecialTemplateInsertPlace(
              sip      : SmartInsertPlace
            , val tmplt: PsiTemplateGen<PsiCodeBlock, SmartInsertPlace>
    ): SmartInsertPlace(sip.range, sip.fillConstant, sip.boxToSuit) {
        private fun getSIP(): SmartInsertPlace = SmartInsertPlace(range, fillConstant, boxToSuit)

        override fun shiftRight(delta: Int) = CodeBlockSpecialTemplateInsertPlace(getSIP().shiftRight(delta), tmplt)

        //TODO:       "#$fillConstant ${boxToString()} $ $tmplt#"
        override public fun toString(): String = tmplt.toString()
    }
//
//    public fun getCodeBlockInsertPlace(blockSubTree: PsiElement?): SmartInsertPlace? {
//        if (blockSubTree == null) { return null }
//        val subtreeSIP = blockSubTree.toSmartInsertPlace()
//        val blockTmplt = printer.codeBlockComponent.getTemplateInBlockCase(blockSubTree)
//        if (blockTmplt == null) {
//            if (blockSubTree is PsiBlockStatement || blockSubTree is PsiCodeBlock) { return null }
//            return subtreeSIP
//        }
//        return CodeBlockSpecialTemplateInsertPlace(subtreeSIP, blockTmplt)
//    }

    //public because of trait use.
    //Actually, must be protected.
//    public fun updateCodeBlockPart(
//              codeBlockSubTree: PsiElement?
//            , blockTag: String
//            , tmplt   : T
//            , variants: MutableMap<String, FormatSet>
//            ,  context: VariantConstructionContext
//    ) {
//        val blockVariants = getCodeBlockPartVariants(codeBlockSubTree, blockTag, tmplt, context)
//        if (blockVariants.isEmpty()) { return }
//        variants.put(blockTag, blockVariants)
//    }

//    public fun getCodeBlockPartVariants(
//              block   : PsiElement?
//            , blockTag: String
//            , tmplt   : T
//            , context : VariantConstructionContext
//    ): FormatSet {
//        if (block == null) { return printer.getEmptySet() }
//        val blockIPlace = tmplt.insertPlaceMap.get(blockTag)
//
//        if (!(block is PsiCodeBlock || block is PsiBlockStatement)) {
//            if (blockIPlace is CodeBlockSpecialTemplateInsertPlace) { return printer.getEmptySet() }
//            return printer.getVariants(block, context)
//        }
//        if (blockIPlace !is CodeBlockSpecialTemplateInsertPlace) { return printer.getEmptySet() }
//
//        val codeBlock: PsiCodeBlock = when (block) {
//                                          is PsiCodeBlock      -> block
//                                          is PsiBlockStatement -> block.getCodeBlock()
//                                          else -> return printer.getEmptySet()
//                                      }
//        val blockVariants = printer.codeBlockComponent.getVariantsByTemplate(codeBlock, blockIPlace.tmplt, context)
//        return blockVariants
//    }

    override public fun getContentRelation(
            text: String, insertPlaceMap: Map<String, SmartInsertPlace>
    ): Pair< Map<TagPlaceLine, Int>
           , Map<Int, LineEquation>
           >
    {
        val lines = text.toLines()
        val tagToRangeMap = insertPlaceMap.mapValues { e -> e.getValue().range }
        val tagPlaceToLineNumber = lines.getTagPlaceToLineNumberMap(tagToRangeMap)
        val lineNumberToEquation = lines.getLineEquations(tagToRangeMap, tagPlaceToLineNumber)

        return Pair(tagPlaceToLineNumber, lineNumberToEquation)
    }


    //TODO: Unfortunately need to be public
    public fun getElementsVariants(
            elementList: List<PsiElement>
            ,   context: VariantConstructionContext
            , separator: (Int) -> Format
    ): FormatSet = getElementsVariants(elementList, context, { l: Format -> l }, separator)

    public fun getElementsVariants(
              elementList: List<PsiElement>
            ,     context: VariantConstructionContext
            , elementWrap: (Format) -> Format
            ,   separator: (Int) -> Format
    ): FormatSet {
        if (elementList.isEmpty()) { return printer.getInitialSet() }

        var elemVariantsList = elementList.map { e -> printer.getVariants(e, context) }
        if (!printer.hasToUseMultipleListElemVariants())
            elemVariantsList = elemVariantsList.map { fs -> fs.headSingleton() }

        return elemVariantsList.fillListByWidth(printer, context.widthToSuit, elementWrap, separator)
    }

    /** New CodeBlock Part */
    final val BODY_TAG : String
        get() = "body"
    protected fun toBlockTag(tag: String): String = tag + " block"

//    protected fun preparePossibleCodeBlockPart(
//              subTree: PsiElement?, subTreeTag: String
//            , variants: MutableMap<String, FormatSet>, context: VariantConstructionContext
//    ): Boolean {
//        if (subTree == null) { return false }
//        if (subTree is PsiBlockStatement || subTree is PsiCodeBlock) {
//            val codeBlock = subTree.toCodeBlock()
//            if (codeBlock == null)                   { return false }
//            if (codeBlock.getStatements().size == 0) { return true  }
//            val statementsVariants =
//                    CodeBlockComponent.getStatementsWithoutLabelsVariants(printer, codeBlock, context)
//            variants.put(toBlockTag(subTreeTag), statementsVariants)
//            return true
//        }
//        variants.put(subTreeTag, printer.getVariants(subTree, context))
//        return true
//    }

    protected fun addCBtoInsertPlaceMap(
              subTree: PsiElement?
            , subTreeTag: String
            , insertPlaceMap: MutableMap<String, SmartInsertPlace>
            , text: String
            , delta: Int = 0
    ): Boolean {
        when (subTree) {
            null -> return false
            is PsiCodeBlock, is PsiBlockStatement -> {
                val sip = subTree.getStatements()?.toSmartInsertPlace(text, delta)
                if (sip != null) { insertPlaceMap.put(toBlockTag(subTreeTag), sip) }
            }
            else -> insertPlaceMap.put(subTreeTag, subTree.toSmartInsertPlace().shiftRight(delta))
        }
        return true
    }

    protected fun addPossibleCodeBlockTag(set: MutableSet<String>, subTree: PsiElement?, subTreeTag: String) {
        when (subTree) {
            null -> return
            is PsiCodeBlock, is PsiBlockStatement
                          -> if ((subTree.getStatements()?.size ?: 0) > 0) { set.add(toBlockTag(subTreeTag)) }
            is PsiElement -> set.add(subTreeTag)
        }
    }
}