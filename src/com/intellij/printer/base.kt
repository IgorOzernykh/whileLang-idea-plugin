package com.intellij

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.whileLang.Printer
import org.jetbrains.format.*
import org.jetbrains.format.FormatSet.FormatSetType
import java.util.ArrayList

/**
 * User: anlun
 */

class IncorrectPsiElementException(str: String): Exception(str) {}

open class InsertPlace(
  val range: TextRange
, val fillConstant: Int
) {
    companion object {
        val STARTS_WITH_NEW_LINE      : Int = -1
        val DOESNT_START_WITH_NEW_LINE: Int =  0
    }

    fun shiftRight(delta: Int): InsertPlace =
            InsertPlace(range.shiftRight(delta), fillConstant)
}

fun walker(p: PsiElement, mapFunction: (PsiElement) -> Unit) {
    /*
    for (el in p.getChildren()) {
        walker(el, mapFunction)
        mapFunction(el)
    }
    */
    for (el in p.getChildren()) {
        walker(el, mapFunction)
    }
    mapFunction(p)
}

fun insertFormatsToText(text: String, fmtsWithRanges: List<Pair<InsertPlace, Format>>): Format {
    val sortedRangeList = fmtsWithRanges.sortBy { p -> p.first.range.getStartOffset() }

    var curPos = 0
    var curFmt = Format.empty
    sortedRangeList.forEach { p ->
        val range = p.first.range
        val start = range.getStartOffset()
        val end   = range.getEndOffset()
        val fmt   = p.second

        if (curPos < start) { curFmt = curFmt + text.substring(curPos, start).toFormat() }
        val fillConstant = p.first.fillConstant
        if (fillConstant < 0) {
            curFmt = curFmt / fmt
        } else {
            curFmt = curFmt.addFillStyle(fmt, fillConstant)
        }
        curPos = end
    }
    val len = text.length()
    if (curPos < len) { curFmt = curFmt + text.substring(curPos, len).toFormat() }
    return curFmt
}

fun insertToText(
        width: Int, text: String, fmtListsWithRanges: List<Pair<InsertPlace, FormatSet>>
): FormatSet {
    if (!FormatSet.usingNewInsertToText) { insertToText_old(width, text, fmtListsWithRanges) }
    return insertToText_new(width, text, fmtListsWithRanges)
}

private fun findNewLine(list: List<TextRange>, text: String): Int? {
    var firstNewLinePosition: Int? = null
    for (r in list) {
        val rText = text.substring(r.getStartOffset(), r.getEndOffset())
        val newLineSymbolPosition = rText.indexOf("\n")
        if (newLineSymbolPosition != -1) {
            firstNewLinePosition = newLineSymbolPosition + r.getStartOffset()
            break
        }
    }
    return firstNewLinePosition
}

private fun divideBy(r: TextRange, l: List<TextRange>): ArrayList<TextRange> {
    var curPos = r.getStartOffset()
    val endPos = r.getEndOffset()
    val result = ArrayList<TextRange>(l.size() + 1)

    for (p in l) {
        val pStart = p.getStartOffset()
        if (pStart > endPos) { return result }
        result.add(TextRange(curPos, pStart))
        curPos = p.getEndOffset()
    }
    if (curPos < endPos) { result.add(TextRange(curPos, endPos)) }
    return result
}


private fun insertToText_old(
        width: Int, text: String, fmtListsWithRanges: List<Pair<InsertPlace, FormatSet>>
): FormatSet = insertToTextInRange(text, fmtListsWithRanges, 0, text.length(), FormatSet.initial(width))

public fun insertToText_new(
        width: Int, text: String, fmtListsWithRanges: List<Pair<InsertPlace, FormatSet>>
): FormatSet {
    val sortedFmtListsWithRanges = fmtListsWithRanges sortBy { p -> p.first.range.getStartOffset() }
    val insertPlaceRanges =  sortedFmtListsWithRanges map    { p -> p.first.range }

    val textRanges = divideBy(TextRange(0, text.length()), insertPlaceRanges)
    val firstNLPos = findNewLine(textRanges          , text)
    val  lastNLPos = findNewLine(textRanges.reverse(), text)
    if (firstNLPos == null || lastNLPos == null) {
        return insertToTextInRange(text, sortedFmtListsWithRanges, 0, text.length(), FormatSet.initial(width))
    }

    val beginFormatSet = insertToTextInRange(text, sortedFmtListsWithRanges, 0, firstNLPos, FormatMap2D_FL(width))
    val fs: FormatSet = when (FormatSet.defaultFormatSetType) {
        is FormatSetType.D3   -> FormatMap3D   (width)
        is FormatSetType.D3AF -> FormatMap3D_AF(width)
        is FormatSetType.SteppedD3AF -> SteppedFormatMap(FormatSet.stepInMap, width)
        else -> FormatMap3D(width)
    }
    fs.addAll(beginFormatSet)

    val nextAfterFirstNL = firstNLPos + 1
    if (firstNLPos >= lastNLPos) {
        val endFormatSet = insertToTextInRange(
                text, sortedFmtListsWithRanges, nextAfterFirstNL, text.length(), FormatMap2D_LL(width))
        return fs - endFormatSet
    }

    val nextAfterLastNL = lastNLPos + 1
    val middleFormatSet = insertToTextInRange(
            text, sortedFmtListsWithRanges, nextAfterFirstNL,   lastNLPos, FormatMap1D   (width))
    val    endFormatSet = insertToTextInRange(
            text, sortedFmtListsWithRanges,  nextAfterLastNL, text.length(), FormatMap2D_LL(width))
    return fs - middleFormatSet - endFormatSet
}

private fun insertToTextInRange(
        text: String, fmtListsWithRanges: List<Pair<InsertPlace, FormatSet>>
        , startPos: Int, endPos: Int, startSet: FormatSet
): FormatSet {
    val sortedRangeList = fmtListsWithRanges
            .filter { p -> val rangeStart = p.first.range.getStartOffset()
                rangeStart >= startPos && rangeStart < endPos }
            .sortBy { p -> p.first.range.getStartOffset() }

    var curPos = startPos
    var curFmtList = startSet; if (curFmtList.isEmpty()) { curFmtList.add(Format.empty) }

    for (p in sortedRangeList) {
        val range   = p.first.range
        val start   = range.getStartOffset()
        if (curPos < start) { curFmtList = curFmtList + text.substring(curPos, start).toFormat() }

        val fmtList      = p.second
        val fillConstant = p.first.fillConstant
        curFmtList = if (fillConstant < 0) curFmtList.addBeside   (fmtList)
        else                  curFmtList.addFillStyle(fmtList, fillConstant)
        curPos = range.getEndOffset()
    }
    if (curPos < endPos) { curFmtList = curFmtList + text.substring(curPos, endPos).toFormat() }

    return curFmtList
}

abstract class WidthToSuit {
    abstract public fun getFirstLineSuitWidth(): Int
    abstract public fun  getLastLineSuitWidth(): Int

    public fun getMaxSuitWidth(): Int =
            Math.max(getFirstLineSuitWidth(), getLastLineSuitWidth())

    public fun isFormatSuitable(fmt: Format): Boolean {
        if (fmt.firstLineWidth >= getFirstLineSuitWidth()) { return false }
        return fmt.totalWidth < getLastLineSuitWidth()
    }
}

class SimpleWidthToSuit(
        width: Int
): WidthToSuit() {
    override public fun getFirstLineSuitWidth(): Int = width
    override public fun  getLastLineSuitWidth(): Int = width

    private val width = width
}

class BaseWidthToSuit(
        firstLineWidth: Int
        , lastLineWidth: Int
): WidthToSuit() {
    override public fun getFirstLineSuitWidth(): Int = firstLineWidth
    override public fun  getLastLineSuitWidth(): Int = lastLineWidth

    private val firstLineWidth = firstLineWidth
    private val  lastLineWidth =  lastLineWidth
}


/** For Single Format */
fun List<Format>.fillListByWidth(width: Int, f: (Format) -> Format, separator: Format): Format =
        fillListByWidth(SimpleWidthToSuit(width), f, separator)

fun List<Format>.fillListByWidth(widthToSuit: WidthToSuit, f: (Format) -> Format, separator: Format): Format {
    if (size() <= 0) { return Format.empty }
    var result = f(get(0))
    for (fmt in drop(1)) {
        val mappedFmt = f(fmt)
        val resultWithSeparator = result / separator
        val newResult = resultWithSeparator / mappedFmt
        if (widthToSuit.isFormatSuitable(newResult)) {
            result = newResult
        } else {
            result = resultWithSeparator - mappedFmt
        }
    }
    return result
}

//TODO: write tests
//TODO: rewrite in reusing style
/** For FormatSet */
//fun List<FormatSet>.fillListByWidth(maxWidth: Int, width: Int, f: (Format) -> Format, separator: Format): FormatSet =
//        fillListByWidth(maxWidth, SimpleWidthToSuit(width), f, separator)
//
fun List<FormatSet>.fillListByWidth(printer: Printer, widthToSuit: WidthToSuit, f: (Format) -> Format, separator: Format) =
        fillListByWidth(printer, widthToSuit, f, {_ -> separator})

fun List<FormatSet>.fillListByWidth(
        printer: Printer, widthToSuit: WidthToSuit, f: (Format) -> Format, separator: (Int) -> Format
): FormatSet {
    if (size() <= 0) { return printer.getInitialSet() }
    var result = get(0).map(f)
    //    var result: FormatSet = FormatMap1D(maxWidth)
    //    result.addAll(get(0).map(f))
    var fmtNumber = 1

    for (fmtList in drop(1)) {
        val mappedFmtList = fmtList.map(f)
        val curSeparator = separator(fmtNumber)
        val resultWithSeparator = result / curSeparator

        val addedBeside = resultWithSeparator / mappedFmtList
        val fAddedBeside = addedBeside filter { f -> widthToSuit.isFormatSuitable(f) }

        val addedAbove  = resultWithSeparator - mappedFmtList
        val fAddedAbove = addedAbove filter { f -> widthToSuit.isFormatSuitable(f) }

        result = fAddedBeside
        result.addAll(fAddedAbove)
        /*
        result = resultWithSeparator.crossTransform(mappedFmtList) { (x, y) ->
            val addedBeside = x / y
            if (widthToSuit.isFormatSuitable(addedBeside / curSeparator)) {
                addedBeside
            } else {
                x - y
            }
        }
        */
        fmtNumber++
    }
    return result
}

public fun Project.performUndoWrite(task: () -> Unit) {
    WriteCommandAction.runWriteCommandAction(this) {
        CommandProcessor.getInstance().runUndoTransparentAction { task() }
    }
}

