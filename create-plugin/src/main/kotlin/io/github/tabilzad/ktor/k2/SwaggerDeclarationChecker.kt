package io.github.tabilzad.ktor.k2

import io.github.tabilzad.ktor.*
import io.github.tabilzad.ktor.output.convertInternalToOpenSpec
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirSimpleFunctionChecker
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.hasAnnotation

/**
 * check function visits all declarations in the code and searches for those annotated with @GenerateOpenApi.
 * Then the ExpressionVisitor walks through all expressions in the function body to extract Ktor dsl related data
 * and convert it to Open API specification
 */
class SwaggerDeclarationChecker(
    private val session: FirSession,
    configuration: CompilerConfiguration
) : FirSimpleFunctionChecker(MppCheckerKind.Common) {

    private val log = try {
        configuration.get(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
    } catch (ex: Throwable) {
        null
    }
    private val config = configuration.buildPluginConfiguration()

    override fun check(declaration: FirSimpleFunction, context: CheckerContext, reporter: DiagnosticReporter) {
        if (declaration.hasAnnotation(ClassIds.KTOR_GENERATE_ANNOTATION, session)) {
            val expressionsVisitor = ExpressionsVisitorK2(config, context, session, log)
            val ktorElements: List<KtorElement> = declaration.accept(expressionsVisitor, null)
            val components = expressionsVisitor.classNames
                .associateBy { it.fqName ?: "UNKNOWN" }

            convertInternalToOpenSpec(
                routes = ktorElements.wrapLooseEndpoints(),
                configuration = config,
                schemas = components
            ).serializeAndWriteTo(config)
        }
    }

    private fun List<KtorElement>.wrapLooseEndpoints(): List<DocRoute> = map {
        when (it) {
            is EndPoint -> DocRoute("/", mutableListOf(it))

            is DocRoute -> it
        }
    }
}
