package com.github.iguissouma.nxconsole.plugins

import com.github.iguissouma.nxconsole.NxBundle
import com.intellij.ide.IdeBundle
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreter
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterField
import com.intellij.javascript.nodejs.util.NodePackage
import com.intellij.javascript.nodejs.util.NodePackageField
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.DocumentAdapter
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import com.intellij.util.ui.UIUtil
import com.intellij.webcore.ui.PathShortener
import java.awt.Color
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.text.JTextComponent

class NxDevToolsView(val myProject: Project) {
    var myComponent: JPanel? = null
    var myNodeInterpreterField: NodeJsInterpreterField? = null
    var myNxPackageField: NodePackageField? = null
    var myNxJsonField: TextFieldWithBrowseButton? = null
    var myNormalForeground: Color? = null
    var myAllowUpdates = false

    init {
        myAllowUpdates = true
        myNodeInterpreterField = NodeJsInterpreterField(myProject, false)
        myNxPackageField =
            NodePackageField(myNodeInterpreterField!!, NxDevToolsSettingsManager.PKG_DESCRIPTOR, null)
        this.myNxJsonField = createNxJsonField(myProject)

        myNormalForeground = (this.myNxJsonField?.childComponent as JTextField).foreground
        val panel: JPanel = FormBuilder.createFormBuilder().setAlignLabelOnRight(true)
            .addLabeledComponent(
                NxBundle.message("nx.node.interpreter.mnenonic"),
                myNodeInterpreterField!!
            ).addLabeledComponent(
                NxBundle.message("nx.package"),
                myNxPackageField!!
            ).addLabeledComponent(NxBundle.message("nx.json"), this.myNxJsonField!!)
            .panel
        myNodeInterpreterField!!.addChangeListener { newInterpreter: NodeJsInterpreter? -> this.updateLaterIfAllowed() }
        myNxPackageField!!.addSelectionListener { pkg: NodePackage? -> this.updateLaterIfAllowed() }
        this.listenForChanges(this.myNxJsonField?.childComponent as JTextComponent)
    }

    private fun listenForChanges(textComponent: JTextComponent) {

        textComponent.document.addDocumentListener(
            object : DocumentAdapter() {
                override fun textChanged(e: DocumentEvent) {
                    this@NxDevToolsView.updateLaterIfAllowed()
                }
            }
        )
    }

    private fun updateLaterIfAllowed() {
        if (myAllowUpdates) {
            this.updateLater()
        }
    }

    private fun updateLater() {
        UIUtil.invokeLaterIfNeeded { this.update() }
    }

    private fun update() {
        ApplicationManager.getApplication().assertIsDispatchThread()

        val nxDevToolsSettings = NxDevToolsSettings(myProject)
        nxDevToolsSettings.apply {
            this.myInterpreterRef = myNodeInterpreterField!!.interpreterRef
            this.myNxPackage = myNxPackageField?.selected
            this.myNxJsonPath = PathShortener.getAbsolutePath(myNxJsonField?.textField!!)
        }
    }

    private fun createNxJsonField(project: Project): TextFieldWithBrowseButton? {
        val textFieldWithBrowseButton = TextFieldWithBrowseButton()
        SwingHelper.installFileCompletionAndBrowseDialog(
            project,
            textFieldWithBrowseButton,
            IdeBundle.message("dialog.title.select.0", "nx.json"),
            FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
        )
        PathShortener.enablePathShortening(textFieldWithBrowseButton.textField, null as JTextField?)

        return textFieldWithBrowseButton
    }

    fun setSettings(settings: NxDevToolsSettings) {
        myAllowUpdates = false

        try {
            this.myNodeInterpreterField!!.interpreterRef = settings.myInterpreterRef!!
            this.myNxPackageField?.selected = settings.myNxPackage!!
            this.myNxJsonField?.text = FileUtil.toSystemDependentName(settings.myNxJsonPath!!)
            updateLater()
        } finally {
            myAllowUpdates = true
        }
    }

    fun getSettings(): NxDevToolsSettings {

        return NxDevToolsSettings(myProject).apply {
            myInterpreterRef = myNodeInterpreterField!!.interpreterRef
            myNxPackage = myNxPackageField?.selected
            myNxJsonPath = PathShortener.getAbsolutePath(myNxJsonField?.textField!!)
        }
    }
}
