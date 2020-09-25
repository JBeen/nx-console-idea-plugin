package com.github.iguissouma.nxconsole.buildTools.rc

import com.github.iguissouma.nxconsole.buildTools.NxJsonUtil
import com.github.iguissouma.nxconsole.buildTools.NxJsonUtil.findChildNxJsonFile
import com.github.iguissouma.nxconsole.buildTools.NxJsonUtil.getContainingAngularJsonFile
import com.github.iguissouma.nxconsole.buildTools.NxJsonUtil.getContainingNxJsonFile
import com.github.iguissouma.nxconsole.buildTools.NxRunSettings
import com.github.iguissouma.nxconsole.buildTools.NxService
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

class NxRunConfigurationProducer : LazyRunConfigurationProducer<NxRunConfiguration>() {

    companion object {

        fun setupConfigurationFromSettings(configuration: NxRunConfiguration, runSettings: NxRunSettings) {
            configuration.runSettings = runSettings
            configuration.setName(buildName(runSettings.tasks))
        }

        fun buildName(tasks: List<String>): String? {
            return if (tasks.isEmpty()) "default" else StringUtil.join(tasks, ", ")
        }

        private fun createRunSettingsFromContext(
            templateRunSettings: NxRunSettings,
            context: ConfigurationContext,
            sourceElement: Ref<PsiElement>?
        ): NxRunSettings? {

            val element = getElement(context)
            return if (element != null && element.isValid) {
                val psiNxJson = getContainingNxJsonFile(element)
                if (psiNxJson == null) {
                    val psiAngularJsonFile = getContainingAngularJsonFile(element)
                    val virtualAngularJson = psiAngularJsonFile?.virtualFile
                    if (virtualAngularJson == null) {
                        null
                    } else {
                        val findChildNxJsonFile = findChildNxJsonFile(virtualAngularJson.parent) ?: return null
                        // findContainingProjectProperty(element)
                        // TODO check when it's not JsonStringLiteral
                        val taskPropertyLiteral = element.parent as? JsonStringLiteral ?:return null
                        val architectJsonObject = PsiTreeUtil.getParentOfType(
                            taskPropertyLiteral,
                            JsonObject::class.java
                        )
                        val projectJsonObject = PsiTreeUtil.getParentOfType(
                            architectJsonObject,
                            JsonObject::class.java
                        ) ?: return null
                        val projectProperty = PsiTreeUtil.getParentOfType(
                            projectJsonObject,
                            JsonProperty::class.java,
                            false
                        ) ?: return null

                        val setting = templateRunSettings.apply {
                            nxFilePath = findChildNxJsonFile.path
                            tasks = listOf(
                                "${projectProperty.name}:${
                                taskPropertyLiteral.value
                                }"
                            )
                        }
                        sourceElement?.set(element)
                        setting
                    }
                } else {
                    val virtualNxJson = psiNxJson.virtualFile
                    if (virtualNxJson == null) {
                        null
                    } else {
                        val projectProperty = findContainingProjectProperty(element)
                        if (projectProperty == null) {
                            null
                        } else {
                            val setting = templateRunSettings.apply {
                                nxFilePath = virtualNxJson.path
                                tasks = listOf(projectProperty.name)
                            }
                            sourceElement?.set(projectProperty)
                            setting
                        }
                    }
                }
            } else {
                null
            }
        }

        private fun getElement(context: ConfigurationContext): PsiElement? {
            val location = context.location
            return location?.psiElement
        }

        private fun findContainingProjectProperty(element: PsiElement): JsonProperty? {
            val scriptProperty = NxJsonUtil.findContainingPropertyInsideNxJsonFile(element)
            val scriptsProperty = NxJsonUtil.findContainingTopLevelProperty(scriptProperty)
            return if (scriptsProperty != null && "projects" == scriptsProperty.name && scriptProperty !== scriptsProperty) scriptProperty else null
        }
    }

    override fun getConfigurationFactory(): ConfigurationFactory {
        return ConfigurationTypeUtil.findConfigurationType(NxConfigurationType::class.java)
    }

    override fun isConfigurationFromContext(configuration: NxRunConfiguration, context: ConfigurationContext): Boolean {
        val thisRunSettings = createRunSettingsFromContext(
            configuration.runSettings,
            context,
            null
        )
        return if (thisRunSettings == null) false else NxService.getInstance(configuration.project)
            .isConfigurationMatched(configuration, thisRunSettings)
    }

    override fun setupConfigurationFromContext(
        configuration: NxRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {
        val runSettings = createRunSettingsFromContext(
            configuration.runSettings,
            context,
            sourceElement
        )
        return if (runSettings == null) {
            false
        } else {
            setupConfigurationFromSettings(configuration, runSettings)
            true
        }
    }
}
