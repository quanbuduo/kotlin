/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir

import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.resolve.transformers.FirTotalResolveTransformer
import org.jetbrains.kotlin.test.KotlinTestUtils
import java.io.File

abstract class AbstractFirOldFrontendDiagnosticsTest : AbstractFirDiagnosticsTest() {
    companion object {
        private val DIAGNOSTIC_PATTERN = Regex("(<!>|(<!(.(\".*\")*?)+?!>))")

        private fun loadTestDataWithoutDiagnostics(file: File): String {
            return KotlinTestUtils.doLoadFile(file).replace(DIAGNOSTIC_PATTERN, "")
        }
    }

    override fun createTestFileFromPath(filePath: String): File {
        val newPath = filePath.replace(".kt", ".fir.kt")
        return File(newPath).also {
            prepareTestDataFile(filePath, it)
        }
    }

    private fun prepareTestDataFile(originalFilePath: String, firTestDataFile: File) {
        if (!firTestDataFile.exists()) {
            firTestDataFile.writeText(loadTestDataWithoutDiagnostics(File(originalFilePath)))
        }
    }

    override fun runAnalysis(testDataFile: File, testFiles: List<TestFile>, firFiles: List<FirFile>) {
        val failure: AssertionError? = try {
            doFirResolveTestBench(firFiles, FirTotalResolveTransformer().transformers, gc = false)
            null
        } catch (e: AssertionError) {
            e
        }
        val failureFile = File(testDataFile.path.replace(".kt", ".fail"))
        if (failure == null) {
            checkResultingFirFiles(firFiles, testDataFile)
            assertFalse("Test is good but there is expected exception", failureFile.exists())
            checkDiagnostics(testDataFile, testFiles, firFiles)
            val needDump = testFiles.any { it.directives.containsKey("FIR_DUMP") }
            if (needDump) {
                checkFir(testDataFile, firFiles)
            }
        } else {
            if (!failureFile.exists()) {
                throw failure
            }
            checkFailureFile(failure, failureFile)
        }
    }

    private fun checkFailureFile(failure: AssertionError, failureFile: File) {
        val failureMessage = buildString {
            appendln(failure.message)
            failure.cause?.let {
                append("Cause: ")
                appendln(it)
            }
        }
        KotlinTestUtils.assertEqualsToFile(failureFile, failureMessage)
    }

    protected open fun checkResultingFirFiles(firFiles: List<FirFile>, testDataFile: File) {}
}

