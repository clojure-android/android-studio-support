/*
 * Copyright 2016-present Greg Shrago
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.intellij.clojure.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.PsiElement
import org.intellij.clojure.ClojureConstants
import org.intellij.clojure.lang.ClojureScriptLanguage
import org.intellij.clojure.psi.*
import org.intellij.clojure.psi.impl.CReaderCondImpl
import org.intellij.clojure.tools.Tool
import org.intellij.clojure.util.parents

/**
 * @author gregsh
 */
val RESOLVE_SKIPPED: Key<Boolean?> = Key.create("C_SKIP_RESOLVE")

class ClojureInspectionSuppressor : InspectionSuppressor {
  override fun getSuppressActions(element: PsiElement?, toolId: String): Array<out SuppressQuickFix> {
    return arrayOf()
  }

  override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
    if (!toolId.startsWith("Clojure")) return false
    // todo
    return false
  }
}

class ClojureResolveInspection : LocalInspectionTool() {
  override fun getDisplayName() = "Unresolved reference"
  override fun getShortName() = "ClojureResolveInspection"

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): ClojureVisitor {
    if (Tool.choose(holder.file.name) != null) return ClojureVisitor()

    val language = holder.file.language
    val isCljS = language == ClojureScriptLanguage
    val isCljC = FileUtilRt.getExtension(holder.file.name) == "cljc"
    return object : ClojureVisitor() {
      override fun visitSymbol(o: CSymbol) {
        val reference = o.reference
        val resolve = reference.resolve()
        if (o.getUserData(RESOLVE_SKIPPED) != null) return

        val qualifier = reference.qualifier?.apply {
          if (this.reference?.resolve() == null) return }

        if ((isCljS || isCljC && !o.parents().filter { it is CReaderCondImpl }.isEmpty)) {
          val text = o.text
          if (ClojureConstants.CLJS_SPECIAL_FORMS.contains(text)) return
        }

        if (resolve != null) return
        if (qualifier == null && !isCljS && ClojureConstants.TYPE_META_ALIASES.contains(o.name)) return
        if (qualifier == null && isCljS && ClojureConstants.CLJS_TYPES.contains(o.name)) return
        val quotesAndComments = o.parents().filter {
          it is CMetadata || (it as? CForm)?.firstChild is CReaderMacro || (it as? CList)?.first?.name == "comment"
        }
        if (!quotesAndComments.isEmpty) return
        holder.registerProblem(reference, "unable to resolve '${reference.referenceName}'", ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      }
    }
  }
}

