/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.utils

import org.jetbrains.kotlin.ir.backend.js.JsCommonBackendContext
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.hasShape
import org.jetbrains.kotlin.types.Variance

class JsMainFunctionDetector(val context: JsCommonBackendContext) {

    private fun IrSimpleFunction.isMain(allowEmptyParameters: Boolean): Boolean {
        if (typeParameters.isNotEmpty()) return false

        val hasSingleParameter = hasShape(regularParameters = 1)
        val hasTwoParameters = hasShape(regularParameters = 2)
        val hasCorrectShape = hasSingleParameter || hasTwoParameters || allowEmptyParameters && hasShape(regularParameters = 0)
        if (!hasCorrectShape) return false

        val isLoweredSuspendFunction = isLoweredSuspendFunction(context)
        if (!returnType.isUnit() &&
            !(isLoweredSuspendFunction &&
                    (returnType == context.irBuiltIns.anyNType ||
                            returnType == context.irBuiltIns.anyType)))
            return false

        if (name.asString() != "main") return false

        if (hasSingleParameter) {
            return isLoweredSuspendFunction || parameters[0].isStringArrayParameter()
        } else if (hasTwoParameters) {
            return parameters[0].isStringArrayParameter() && isLoweredSuspendFunction
        } else {
            require(allowEmptyParameters)

            val file = parent as IrFile

            return !file.declarations.filterIsInstance<IrSimpleFunction>().any { it.isMain(allowEmptyParameters = false) }
        }
    }

    fun getMainFunctionOrNull(file: IrFile): IrSimpleFunction? {
        // TODO: singleOrNull looks suspicious
        return file.declarations.filterIsInstance<IrSimpleFunction>().singleOrNull { it.isMain(allowEmptyParameters = true) }
    }

    fun getMainFunctionOrNull(module: IrModuleFragment): IrSimpleFunction? {

        var resultPair: Pair<String, IrSimpleFunction>? = null

        module.files.forEach { f ->
            val fqn = f.packageFqName.asString()
            getMainFunctionOrNull(f)?.let {
                val result = resultPair
                if (result == null) {
                    resultPair = Pair(fqn, it)
                } else {
                    if (fqn < result.first) {
                        resultPair = Pair(fqn, it)
                    }
                }
            }
        }

        return resultPair?.second
    }

    class MainFunctionCandidate(val packageFqn: String, val mainFunctionTag: String?)
    companion object {
        inline fun <T> pickMainFunctionFromCandidates(candidates: List<T>, convertToCandidate: (T) -> MainFunctionCandidate): T? {
            return candidates
                .map { it to convertToCandidate(it) }
                .sortedBy { it.second.packageFqn }
                .firstOrNull { it.second.mainFunctionTag != null }
                ?.first
        }
    }
}


fun IrValueParameter.isStringArrayParameter(): Boolean {
    val type = this.type as? IrSimpleType ?: return false

    if (!type.isArray()) return false

    if (type.arguments.size != 1) return false

    val argument = type.arguments.single() as? IrTypeProjection ?: return false

    if (argument.variance == Variance.IN_VARIANCE) return false

    return argument.type.isString()
}

fun IrFunction.isLoweredSuspendFunction(context: JsCommonBackendContext): Boolean {
    val parameter = parameters.lastOrNull() ?: return false
    val type = parameter.type as? IrSimpleType ?: return false
    return type.classifier == context.symbols.coroutineSymbols.continuationClass
}
