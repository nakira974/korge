package com.soywiz.korge.intellij.editor

import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.soywiz.korge.ext.particle.readParticle
import com.soywiz.korge.scene.Module
import com.soywiz.korge.scene.Scene
import com.soywiz.korge.view.Container
import com.soywiz.korge.view.text


class KorgeParticleFileEditorProvider : com.intellij.openapi.fileEditor.FileEditorProvider {
	override fun accept(project: com.intellij.openapi.project.Project, virtualFile: com.intellij.openapi.vfs.VirtualFile): Boolean {
		return (virtualFile.extension?.toLowerCase() ?: "") == "pex"
	}

	override fun createEditor(project: com.intellij.openapi.project.Project, virtualFile: com.intellij.openapi.vfs.VirtualFile): com.intellij.openapi.fileEditor.FileEditor {
		return KorgeBaseKorgeFileEditor(project, virtualFile, EditorModule, "Particle")
	}

	override fun getEditorTypeId(): String = this::class.java.name

	override fun getPolicy(): FileEditorPolicy {
		return FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR
	}

	object EditorModule : Module() {
		override val mainScene: Class<out Scene> = EditorScene::class.java

		class EditorScene(
			val fileToEdit: KorgeFileToEdit
		) : Scene() {
			suspend override fun sceneInit(sceneView: Container) {
				//sceneView += views.text("HELLO WORLD!")
				sceneView += fileToEdit.file.readParticle(views).create(views.virtualWidth / 2.0, views.virtualHeight / 2.0)
			}
		}
	}
}

