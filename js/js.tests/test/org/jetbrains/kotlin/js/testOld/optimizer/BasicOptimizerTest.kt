/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.js.testOld.optimizer

import com.google.gwt.dev.js.rhino.CodePosition
import com.google.gwt.dev.js.rhino.ErrorReporter
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.kotlin.js.backend.JsToStringGenerationVisitor
import org.jetbrains.kotlin.js.backend.ast.*
import org.jetbrains.kotlin.js.backend.ast.metadata.synthetic
import org.jetbrains.kotlin.js.engine.ScriptEngine
import org.jetbrains.kotlin.js.inline.clean.FunctionPostProcessor
import org.jetbrains.kotlin.js.parser.parse
import org.jetbrains.kotlin.js.testOld.TEST_DATA_DIR_PATH
import org.jetbrains.kotlin.js.testOld.createScriptEngine
import org.jetbrains.kotlin.js.translate.utils.JsAstUtils
import org.jetbrains.kotlin.js.util.TextOutputImpl
import org.junit.AfterClass
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.rules.TestName
import java.io.File

abstract class BasicOptimizerTest(private var basePath: String) {
    companion object {
        lateinit var engine: ScriptEngine

        @BeforeClass
        @JvmStatic
        fun create() {
            engine = createScriptEngine()
        }

        @AfterClass
        @JvmStatic
        fun release() {
            engine.release()
        }
    }

    @Rule
    @JvmField
    var testName = TestName()

    protected fun box() {
        val methodName = testName.methodName
        val baseName = "${TEST_DATA_DIR_PATH}/js-optimizer/$basePath"
        val unoptimizedName = "$baseName/$methodName.original.js"
        val optimizedName = "$baseName/$methodName.optimized.js"

        val unoptimizedCode = FileUtil.loadFile(File(unoptimizedName))
        val optimizedCode = FileUtil.loadFile(File(optimizedName))

        runScript(unoptimizedName, unoptimizedCode)
        runScript(optimizedName, optimizedCode)
        checkOptimizer(unoptimizedCode, optimizedCode)
    }

    private fun checkOptimizer(unoptimizedCode: String, optimizedCode: String) {
        val parserScope = JsFunctionScope(JsRootScope(JsProgram()), "<js fun>")
        val unoptimizedAst = parse(unoptimizedCode, errorReporter, parserScope, "<unknown file>")!!

        updateMetadata(unoptimizedCode, unoptimizedAst)

        for (statement in unoptimizedAst) {
            process(statement)
        }

        val optimizedAst = parse(optimizedCode, errorReporter, parserScope, "<unknown file>")!!
        Assert.assertEquals(astToString(optimizedAst), astToString(unoptimizedAst))
    }

    protected open fun process(statement: JsStatement) {
        object : RecursiveJsVisitor() {
            override fun visitFunction(x: JsFunction) {
                FunctionPostProcessor(x).apply()
                super.visitFunction(x)
            }
        }.accept(statement)
    }

    private fun updateMetadata(code: String, ast: List<JsStatement>) {
        val comments = findSyntheticComments(code)

        for (stmt in ast) {
            object : RecursiveJsVisitor() {
                override fun visitVars(x: JsVars) {
                    x.synthetic = x.vars.any { isSyntheticId(it.name.ident) }
                    super.visitVars(x)
                }

                override fun visitExpressionStatement(x: JsExpressionStatement) {
                    val assignment = JsAstUtils.decomposeAssignmentToVariable(x.expression)
                    if (assignment != null) {
                        val name = assignment.first
                        x.synthetic = isSyntheticId(name.ident)
                    }
                    super.visitExpressionStatement(x)
                }

                override fun visitIf(x: JsIf) {
                    val line = (x.source as? JsLocation)?.startLine
                    if (line != null && line in comments.indices && comments[line]) {
                        x.synthetic = true
                    }
                    super.visitIf(x)
                }

                override fun visitLabel(x: JsLabel) {
                    x.synthetic = isSyntheticId(x.name.ident)
                    super.visitLabel(x)
                }
            }.accept(stmt)
        }
    }

    private fun findSyntheticComments(code: String): List<Boolean> {
        val parts = code.lines()
        return parts.map { it.contains("/*synthetic*/") }
    }

    private fun isSyntheticId(id: String) = id.startsWith("$")

    private fun astToString(ast: List<JsStatement>): String {
        val output = TextOutputImpl()
        val visitor = JsToStringGenerationVisitor(output)
        for (stmt in ast) {
            stmt.accept(visitor)
        }
        return output.toString()
    }

    private fun runScript(fileName: String, code: String) {
        engine.eval(code)
        val result = engine.eval("box()")

        Assert.assertEquals("$fileName: box() function must return 'OK'", "OK", result)
    }

    private val errorReporter = object : ErrorReporter {
        override fun warning(message: String, startPosition: CodePosition, endPosition: CodePosition) { }

        override fun error(message: String, startPosition: CodePosition, endPosition: CodePosition) {
            Assert.fail("Error parsing JS file: $message at $startPosition")
        }
    }
}
