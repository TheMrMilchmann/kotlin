/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.backend

import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.fir.backend.generators.isExternalParent
import org.jetbrains.kotlin.fir.backend.utils.ConversionTypeOrigin
import org.jetbrains.kotlin.fir.containingClassForLocalAttr
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.expressions.FirAnonymousObjectExpression
import org.jetbrains.kotlin.fir.lazy.Fir2IrLazyClass
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirReplSnippetSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeAliasSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeParameterSymbol
import org.jetbrains.kotlin.fir.symbols.lazyResolveToPhase
import org.jetbrains.kotlin.fir.types.ConeClassLikeLookupTag
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrFactoryImpl
import org.jetbrains.kotlin.ir.symbols.*
import org.jetbrains.kotlin.ir.symbols.impl.*
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.utils.addToStdlib.runIf
import java.util.concurrent.ConcurrentHashMap

class Fir2IrClassifierStorage(
    private val c: Fir2IrComponents,
    commonMemberStorage: Fir2IrCommonMemberStorage,
    private val conversionScope: Fir2IrConversionScope,
) : Fir2IrComponents by c {
    private val classCache: MutableMap<FirRegularClass, IrClassSymbol> = commonMemberStorage.classCache
    private val notFoundClassCache: ConcurrentHashMap<ConeClassLikeLookupTag, IrClass> = commonMemberStorage.notFoundClassCache

    private val typeAliasCache: MutableMap<FirTypeAlias, IrTypeAliasSymbol> = mutableMapOf()

    private val typeParameterCache: MutableMap<FirTypeParameter, IrTypeParameter> = commonMemberStorage.typeParameterCache

    private val typeParameterCacheForSetter: MutableMap<FirTypeParameter, IrTypeParameter> = mutableMapOf()

    private val enumEntryCache: MutableMap<FirEnumEntry, IrEnumEntrySymbol> = commonMemberStorage.enumEntryCache

    private val codeFragmentCache: MutableMap<FirCodeFragment, IrClass> = mutableMapOf()

    private val earlierSnippetsCache: MutableMap<FirReplSnippetSymbol, IrClass> = mutableMapOf()

    private val fieldsForContextReceivers: MutableMap<IrClass, List<IrField>> = mutableMapOf()

    private val localStorage: Fir2IrLocalClassStorage = Fir2IrLocalClassStorage(
        // Using existing cache is necessary here to be able to serialize local classes from common code in expression codegen
        commonMemberStorage.localClassCache
    )

    private val localClassesCreatedOnTheFly: MutableMap<FirClass, IrClass> = mutableMapOf()

    /**
     * This function is quite messy and doesn't have a good contract of what exactly is traversed.
     * The basic idea is to traverse the symbols which can be reasonably referenced from other modules.
     *
     * Be careful when using it, and avoid it, except really needed.
     */
    @Suppress("unused")
    @DelicateDeclarationStorageApi
    fun forEachCachedDeclarationSymbol(block: (IrSymbol) -> Unit) {
        classCache.values.forEach { block(it) }
        typeAliasCache.values.forEach { block(it) }
        enumEntryCache.values.forEach { block(it) }
        fieldsForContextReceivers.values.forEach { fields ->
            fields.forEach { block(it.symbol) }
        }
    }

    private var processMembersOfClassesOnTheFlyImmediately = false

    // ------------------------------------ type parameters ------------------------------------

    // Note: declareTypeParameters should be called before!
    internal fun preCacheTypeParameters(owner: FirTypeParameterRefsOwner) {
        for ((index, typeParameter) in owner.typeParameters.withIndex()) {
            val original = typeParameter.symbol.fir
            getCachedIrTypeParameter(original)
                ?: createAndCacheIrTypeParameter(original, index)
            if (owner is FirProperty && owner.isVar) {
                val context = ConversionTypeOrigin.SETTER
                getCachedIrTypeParameter(original, context)
                    ?: createAndCacheIrTypeParameter(original, index, context)
            }
        }
    }

    internal fun getIrTypeParameter(
        typeParameter: FirTypeParameter,
        index: Int,
        typeOrigin: ConversionTypeOrigin = ConversionTypeOrigin.DEFAULT
    ): IrTypeParameter {
        getCachedIrTypeParameter(typeParameter, typeOrigin)?.let { return it }
        val irTypeParameter = createAndCacheIrTypeParameter(typeParameter, index, typeOrigin)
        classifiersGenerator.initializeTypeParameterBounds(typeParameter, irTypeParameter)
        return irTypeParameter
    }

    private fun createAndCacheIrTypeParameter(
        typeParameter: FirTypeParameter,
        index: Int,
        typeOrigin: ConversionTypeOrigin = ConversionTypeOrigin.DEFAULT,
    ): IrTypeParameter {
        val symbol = IrTypeParameterSymbolImpl()
        val irTypeParameter = classifiersGenerator.createIrTypeParameterWithoutBounds(typeParameter, index, symbol)
        // Cache the type parameter BEFORE processing its bounds/supertypes, to properly handle recursive type bounds.
        if (typeOrigin.forSetter) {
            typeParameterCacheForSetter[typeParameter] = irTypeParameter
        } else {
            typeParameterCache[typeParameter] = irTypeParameter
        }
        return irTypeParameter
    }

    internal fun getCachedIrTypeParameter(
        typeParameter: FirTypeParameter,
        typeOrigin: ConversionTypeOrigin = ConversionTypeOrigin.DEFAULT
    ): IrTypeParameter? {
        return if (typeOrigin.forSetter)
            typeParameterCacheForSetter[typeParameter]
        else
            typeParameterCache[typeParameter]
    }

    fun getIrTypeParameterSymbol(
        firTypeParameterSymbol: FirTypeParameterSymbol,
        typeOrigin: ConversionTypeOrigin
    ): IrTypeParameterSymbol {
        val firTypeParameter = firTypeParameterSymbol.fir

        val cachedSymbol = getCachedIrTypeParameter(firTypeParameter, typeOrigin)?.symbol
            ?: typeParameterCache[firTypeParameter]?.symbol // We can try to use default cache because setter can use parent type parameters

        if (cachedSymbol != null) {
            return cachedSymbol
        }

        if (c.configuration.allowNonCachedDeclarations) {
            return createIrTypeParameterForNonCachedDeclaration(firTypeParameter)
        }

        error("Cannot find cached type parameter by FIR symbol: ${firTypeParameterSymbol.name} of the owner: ${firTypeParameter.containingDeclarationSymbol}")
    }

    private fun createIrTypeParameterForNonCachedDeclaration(firTypeParameter: FirTypeParameter): IrTypeParameterSymbol {
        val firTypeParameterOwnerSymbol = firTypeParameter.containingDeclarationSymbol
        val firTypeParameterOwner = firTypeParameterOwnerSymbol.fir as FirTypeParameterRefsOwner
        val index = firTypeParameterOwner.typeParameters.indexOf(firTypeParameter).also { check(it >= 0) }

        val isSetter = firTypeParameterOwner is FirPropertyAccessor && firTypeParameterOwner.isSetter
        val conversionTypeOrigin = if (isSetter) ConversionTypeOrigin.SETTER else ConversionTypeOrigin.DEFAULT

        return createAndCacheIrTypeParameter(firTypeParameter, index, conversionTypeOrigin).also {
            classifiersGenerator.initializeTypeParameterBounds(firTypeParameter, it)
        }.symbol
    }

    // ------------------------------------ classes ------------------------------------

    fun createAndCacheIrClass(
        regularClass: FirRegularClass,
        parent: IrDeclarationParent,
        predefinedOrigin: IrDeclarationOrigin? = null
    ): IrClass {
        val symbol = createClassSymbol()
        return classifiersGenerator.createIrClass(regularClass, parent, symbol, predefinedOrigin).also {
            @OptIn(LeakedDeclarationCaches::class)
            cacheIrClass(regularClass, it)
        }
    }

    private fun createClassSymbol(): IrClassSymbol {
        return IrClassSymbolImpl()
    }

    @LeakedDeclarationCaches
    internal fun cacheIrClass(regularClass: FirRegularClass, irClass: IrClass) {
        if (regularClass.visibility == Visibilities.Local) {
            localStorage[regularClass] = irClass
        } else {
            classCache[regularClass] = irClass.symbol
        }
    }

    /**
     * FIR2IR looks over all non-local source classes and creates IR for them using [createAndCacheIrClass]
     * This means that after this phase all classes are either created and bound to their symbols or external classes,
     *   which are created and bound at the first access anyway
     *
     * So, unlike callable declarations, it's safe to expose an API, which returns not just IrClassSymbol, but IrClass itself
     *
     * But on the first FIR2IR stage this API should not be used
     */
    fun getIrClass(firClass: FirClass): IrClass {
        getCachedIrClass(firClass)?.let { return it }
        if (firClass is FirAnonymousObject || firClass is FirRegularClass && firClass.visibility == Visibilities.Local) {
            return createAndCacheLocalIrClassOnTheFly(firClass)
        }
        return createFir2IrLazyClass(firClass)
    }

    /**
     * Function for directly generating or getting from cache the Fir2IrLazyClass.
     * Should not be generally used outside the very special cases.
     * Use [getIrClass] instead.
     */
    @DelicateDeclarationStorageApi
    fun getFir2IrLazyClass(firClass: FirClass): Fir2IrLazyClass =
        getCachedIrClass(firClass)?.let { it as Fir2IrLazyClass } ?: createFir2IrLazyClass(firClass)

    private fun createFir2IrLazyClass(firClass: FirClass): Fir2IrLazyClass {
        require(firClass is FirRegularClass)
        val symbol = createClassSymbol()
        val classId = firClass.symbol.classId
        val parentClassLookupTag = firClass.containingClassForLocalAttr
            ?: classId.outerClassId?.let { session.symbolProvider.getClassLikeSymbolByClassId(it) }?.toLookupTag()
        val irParent = declarationStorage.findIrParent(
            classId.packageFqName,
            parentClassLookupTag,
            firClass.symbol,
            firClass.origin
        )!!

        classCache[firClass] = symbol
        check(irParent.isExternalParent()) { "Source classes should be created separately before referencing" }
        return lazyDeclarationsGenerator.createIrLazyClass(firClass, irParent, symbol)
    }

    private fun getIrClass(lookupTag: ConeClassLikeLookupTag): IrClass? {
        val firClassSymbol = lookupTag.toClassSymbol(session) ?: return null
        return getIrClass(firClassSymbol.fir)
    }

    fun getCachedIrLocalClass(klass: FirClass): IrClass? {
        return runIf(klass is FirAnonymousObject || klass is FirRegularClass && klass.visibility == Visibilities.Local) {
            localStorage[klass]
        }
    }

    private fun getCachedIrClass(klass: FirClass): IrClass? {
        @OptIn(UnsafeDuringIrConstructionAPI::class)
        return getCachedIrLocalClass(klass) ?: classCache[klass]?.owner
    }

    fun getIrClassSymbol(firClassSymbol: FirClassSymbol<*>): IrClassSymbol {
        return getIrClass(firClassSymbol.fir).symbol
    }

    fun getIrClassSymbol(lookupTag: ConeClassLikeLookupTag): IrClassSymbol? {
        return getIrClass(lookupTag)?.symbol
    }

    // TODO(KT-72994) remove when context receivers are removed
    fun getFieldsWithContextReceiversForClass(irClass: IrClass, klass: FirClass): List<IrField> {
        if (klass !is FirRegularClass || klass.contextParameters.isEmpty()) return emptyList()

        return fieldsForContextReceivers.getOrPut(irClass) {
            klass.contextParameters.withIndex().map { (index, contextReceiver) ->
                IrFactoryImpl.createField(
                    startOffset = UNDEFINED_OFFSET,
                    endOffset = UNDEFINED_OFFSET,
                    origin = IrDeclarationOrigin.FIELD_FOR_CLASS_CONTEXT_RECEIVER,
                    name = Name.identifier("contextReceiverField$index"),
                    visibility = DescriptorVisibilities.PRIVATE,
                    symbol = IrFieldSymbolImpl(),
                    type = contextReceiver.returnTypeRef.toIrType(),
                    isFinal = true,
                    isStatic = false,
                    isExternal = false,
                ).also {
                    it.parent = irClass
                }
            }
        }
    }

    fun getIrClassForNotFoundClass(classLikeLookupTag: ConeClassLikeLookupTag): IrClass {
        return notFoundClassCache.getOrPut(classLikeLookupTag) {
            classifiersGenerator.createIrClassForNotFoundClass(classLikeLookupTag)
        }
    }

    // ------------------------------------ local classes ------------------------------------

    private fun createAndCacheLocalIrClassOnTheFly(klass: FirClass): IrClass {
        val (irClass, firClassOrLocalParent, irClassOrLocalParent) = classifiersGenerator.createLocalIrClassOnTheFly(
            klass,
            processMembersOfClassesOnTheFlyImmediately
        )

        if (!processMembersOfClassesOnTheFlyImmediately) {
            localClassesCreatedOnTheFly[firClassOrLocalParent] = irClassOrLocalParent
        }
        return irClass
    }

    // Note: this function is called exactly once, right after Fir2IrConverter finished f/o binding for regular classes
    fun processMembersOfClassesCreatedOnTheFly() {
        // After the call of this function, members of local classes may be processed immediately
        // Before the call it's not possible, because f/o binding for regular classes isn't done yet
        processMembersOfClassesOnTheFlyImmediately = true
        for ((klass, irClass) in localClassesCreatedOnTheFly) {
            conversionScope.withContainingFirClass(klass) {
                classifiersGenerator.processClassHeader(klass, irClass)
                converter.processClassMembers(klass, irClass)
            }
        }
        localClassesCreatedOnTheFly.clear()
    }


    // ------------------------------------ anonymous objects ------------------------------------

    fun createAndCacheAnonymousObject(
        anonymousObject: FirAnonymousObject,
        visibility: Visibility = Visibilities.Local,
        name: Name = SpecialNames.NO_NAME_PROVIDED,
        irParent: IrDeclarationParent? = null
    ): IrClass {
        return classifiersGenerator.createAnonymousObject(anonymousObject, visibility, name, irParent).also {
            localStorage[anonymousObject] = it
        }
    }

    fun getIrAnonymousObjectForEnumEntry(anonymousObject: FirAnonymousObject, name: Name, irParent: IrClass?): IrClass {
        localStorage[anonymousObject]?.let { return it }
        val irAnonymousObject = createAndCacheAnonymousObject(anonymousObject, Visibilities.Private, name, irParent)
        classifiersGenerator.processClassHeader(anonymousObject, irAnonymousObject)
        return irAnonymousObject
    }

    // ------------------------------------ enum entries ------------------------------------

    fun putEnumEntryClassInScope(enumEntry: FirEnumEntry, correspondingClass: IrClass) {
        localStorage[(enumEntry.initializer as FirAnonymousObjectExpression).anonymousObject] = correspondingClass
    }

    fun getIrEnumEntrySymbol(enumEntry: FirEnumEntry): IrEnumEntrySymbol {
        enumEntryCache[enumEntry]?.let { return it }

        val symbol = IrEnumEntrySymbolImpl()
        enumEntryCache[enumEntry] = symbol

        val irParent = declarationStorage.findIrParent(enumEntry, fakeOverrideOwnerLookupTag = null) as IrClass
        if (irParent.isExternalParent()) {
            enumEntry.lazyResolveToPhase(FirResolvePhase.ANNOTATION_ARGUMENTS)
            classifiersGenerator.createIrEnumEntry(
                enumEntry,
                irParent = irParent,
                symbol,
                predefinedOrigin = irParent.origin
            )
        }
        return symbol
    }

    fun createAndCacheIrEnumEntry(
        enumEntry: FirEnumEntry,
        irParent: IrClass,
        predefinedOrigin: IrDeclarationOrigin? = null,
    ): IrEnumEntry {
        val symbol = getIrEnumEntrySymbol(enumEntry)
        val containingFile = firProvider.getFirCallableContainerFile(enumEntry.symbol)

        @Suppress("NAME_SHADOWING")
        val predefinedOrigin = predefinedOrigin ?: if (containingFile != null) {
            IrDeclarationOrigin.DEFINED
        } else {
            irParent.origin
        }

        return classifiersGenerator.createIrEnumEntry(
            enumEntry,
            irParent = irParent,
            symbol,
            predefinedOrigin = predefinedOrigin
        )
    }

    // ------------------------------------ typealiases ------------------------------------

    fun createAndCacheIrTypeAlias(
        typeAlias: FirTypeAlias,
        parent: IrDeclarationParent
    ): IrTypeAlias {
        val symbol = IrTypeAliasSymbolImpl()
        return classifiersGenerator.createIrTypeAlias(typeAlias, parent, symbol).also {
            typeAliasCache[typeAlias] = symbol
        }
    }

    internal fun getCachedTypeAlias(firTypeAlias: FirTypeAlias): IrTypeAlias? {
        // Type alias should be created at this point
        @OptIn(UnsafeDuringIrConstructionAPI::class)
        return typeAliasCache[firTypeAlias]?.owner
    }

    fun getIrTypeAliasSymbol(firTypeAliasSymbol: FirTypeAliasSymbol): IrTypeAliasSymbol {
        val firTypeAlias = firTypeAliasSymbol.fir
        getCachedTypeAlias(firTypeAlias)?.let { return it.symbol }

        val typeAliasId = firTypeAliasSymbol.classId
        val parentId = typeAliasId.outerClassId
        val parentClass = parentId?.let { session.symbolProvider.getClassLikeSymbolByClassId(it) }
        val irParent = declarationStorage.findIrParent(
            typeAliasId.packageFqName,
            parentClass?.toLookupTag(),
            firTypeAliasSymbol,
            firTypeAlias.origin
        )!!

        val symbol = IrTypeAliasSymbolImpl()
        typeAliasCache[firTypeAlias] = symbol
        lazyDeclarationsGenerator.createIrLazyTypeAlias(firTypeAlias, irParent, symbol)
        return symbol
    }

    // ------------------------------------ code fragments ------------------------------------

    fun getCachedIrCodeFragment(codeFragment: FirCodeFragment): IrClass? {
        return codeFragmentCache[codeFragment]
    }

    fun createAndCacheCodeFragmentClass(codeFragment: FirCodeFragment, containingFile: IrFile): IrClass {
        val symbol = createClassSymbol()
        return classifiersGenerator.createCodeFragmentClass(codeFragment, containingFile, symbol).also {
            codeFragmentCache[codeFragment] = it
        }
    }

    // ------------------------------------ REPL snippets ------------------------------------

    /**
     * Get IrClass corresponding to the REPL Snippet compiled earlier.
     * (see [createAndCacheEarlierSnippetClass] for details
     */
    fun getCachedEarlierSnippetClass(snippetSymbol: FirReplSnippetSymbol): IrClass? {
        return earlierSnippetsCache[snippetSymbol]
    }

    /**
     * Create a (lazy) IrClass that corresponds to the REPL Snippet compiled previously.
     * The class is created from its FIR representation and required only for binding
     * declarations from earlier snippets that are used in the currently compiled one.
     */
    fun createAndCacheEarlierSnippetClass(snippetSymbol: FirReplSnippetSymbol, containingPackageFragment: IrPackageFragment): IrClass {
        val symbol = createClassSymbol()
        return classifiersGenerator.createEarlierSnippetClass(snippetSymbol.fir, containingPackageFragment, symbol).also {
            earlierSnippetsCache[snippetSymbol] = it
            (it as? Fir2IrLazyClass)?.let { lazyClass ->
                classCache[lazyClass.fir] = symbol
            }
        }
    }
}
