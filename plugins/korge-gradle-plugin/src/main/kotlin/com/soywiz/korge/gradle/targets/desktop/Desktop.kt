package com.soywiz.korge.gradle.targets.desktop

import com.soywiz.korge.gradle.*
import com.soywiz.korge.gradle.targets.*
import com.soywiz.korge.gradle.targets.apple.*
import com.soywiz.korge.gradle.util.*
import com.soywiz.korge.gradle.util.get
import org.apache.tools.ant.taskdefs.condition.*
import org.gradle.api.*
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.plugin.mpp.*
import java.io.*

private val nativeTargets = listOf("mingwX64", "linuxX64", "macosX64")

private val RELEASE = "release"
private val DEBUG = "debug"
private val RELEASE_DEBUG = listOf(RELEASE, DEBUG)

private val nativeTarget = when {
	Os.isFamily(Os.FAMILY_WINDOWS) -> "mingwX64"
	Os.isFamily(Os.FAMILY_MAC) -> "macosX64"
	Os.isFamily(Os.FAMILY_UNIX) -> "linuxX64"
	else -> "unknownX64"
}
private val cnativeTarget = nativeTarget.capitalize()

fun Project.configureNativeDesktop() {
	for (preset in nativeTargets) {
		gkotlin.targets.add((gkotlin.presets.getAt(preset) as KotlinNativeTargetPreset).createTarget(preset).apply {
			compilations["main"].outputKinds("EXECUTABLE")
		})
	}

	val prepareKotlinNativeBootstrap = tasks.create("prepareKotlinNativeBootstrap") { task ->
		task.apply {
			group = "korge"
			val output = File(buildDir, "platforms/native-desktop/bootstrap.kt")
			outputs.file(output)
			doLast {
				output.parentFile.mkdirs()

				val text = Indenter {
					line("import ${korge.entryPoint}")
					line("fun main(args: Array<String>) = RootGameMain.runMain(args)")
					line("object RootGameMain") {
						line("fun runMain() = runMain(arrayOf())")
						line("fun runMain(args: Array<String>) = com.soywiz.korio.Korio { ${korge.entryPoint}() }")
					}
				}
				if (!output.exists() || output.readText() != text) output.writeText(text)
			}
		}
	}

	afterEvaluate {
		//for (target in listOf(kotlin.macosX64(), kotlin.linuxX64(), kotlin.mingwX64(), kotlin.iosX64(), kotlin.iosArm64(), kotlin.iosArm32())) {
		for (target in listOf(kotlin.macosX64(), kotlin.linuxX64(), kotlin.mingwX64())) {
			target.apply {
				compilations["main"].apply {
					//println(this.binariesTaskName)
					for (type in listOf(NativeBuildType.DEBUG, NativeBuildType.RELEASE)) {
						getLinkTask(NativeOutputKind.EXECUTABLE, type).dependsOn(prepareKotlinNativeBootstrap)
					}
					defaultSourceSet.kotlin.srcDir(File(buildDir, "platforms/native-desktop"))
				}
				/*
            binaries {
                executable {
                    println("linkTask = $linkTask")
                    linkTask.dependsOn(prepareKotlinNativeBootstrap)
                }
            }
            */
			}
		}
	}

	project.afterEvaluate {
		for (target in nativeTargets) {
			val taskName = "copyResourcesToExecutableTest_${target.capitalize()}"
			val targetTestTask = project.tasks.getByName("${target}Test")
			val task = project.addTask<Copy>(taskName) { task ->
				for (sourceSet in project.gkotlin.sourceSets) {
					task.from(sourceSet.resources)
				}
				task.into(File(targetTestTask.inputs.properties["executable"].toString()).parentFile)
			}
			targetTestTask.dependsOn(task)
		}

		// @TODO: This doesn't work after migrating code to Kotlin.
		//for (target in listOf(project.gkotlin.targets["js"], project.gkotlin.targets["metadata"])) {
		//    for (it in (target["compilations"]["main"]["kotlinSourceSets"]["resources"] as Iterable<SourceDirectorySet>).flatMap { it.srcDirs }) {
		//        (tasks["jsJar"] as Copy).from(it)
		//    }
		//}
	}
}

private fun Project.addNativeRun() {
	afterEvaluate {
		for (target in nativeTargets) {
			val ctarget = target.capitalize()
			for (kind in RELEASE_DEBUG) {
				val ckind = kind.capitalize()
				val ctargetKind = "$ctarget$ckind"

				val compilation = gkotlin.targets[target]["compilations"]["main"] as KotlinNativeCompilation
				val executableFile = compilation.getBinary("EXECUTABLE", kind)

				val copyTask = project.addTask<Copy>("copyResourcesToExecutable$ctargetKind") { task ->
					for (sourceSet in project.gkotlin.sourceSets) {
						task.from(sourceSet.resources)
					}
					task.into(executableFile.parentFile)
				}

				addTask<Exec>("runNative$ctargetKind", dependsOn = listOf("linkMain${ckind}Executable$ctarget", copyTask), group = korgeGroup) { task ->
					task.executable = executableFile.absolutePath
					task.args = listOf<String>()
				}
			}
			addTask<Task>("runNative$ctarget", dependsOn = listOf("runNative${ctarget}Release"), group = korgeGroup) { task ->
			}
		}
	}

	addTask<Task>("runNative", dependsOn = listOf("runNative$cnativeTarget"), group = korgeGroup)
	addTask<Task>("runNativeDebug", dependsOn = listOf("runNative${cnativeTarget}Debug"), group = korgeGroup)

	afterEvaluate {
		for (buildType in RELEASE_DEBUG) {
			addTask<Task>("packageMacosX64App${buildType.capitalize()}", group = "korge", dependsOn = listOf("linkMain${buildType.capitalize()}ExecutableMacosX64")) {
				doLast {
					val compilation = gkotlin.targets["macosX64"]["compilations"]["main"] as KotlinNativeCompilation
					val executableFile = compilation.getBinary("EXECUTABLE", buildType)
					val appFolder = buildDir["${korge.name}-$buildType.app"].apply { mkdirs() }
					val appFolderContents = appFolder["Contents"].apply { mkdirs() }
					val appMacOSFolder = appFolderContents["MacOS"].apply { mkdirs() }
					val resourcesFolder = appFolderContents["Resources"].apply { mkdirs() }
					appFolderContents["Info.plist"].writeText(InfoPlistBuilder.build(korge))
					resourcesFolder["${korge.exeBaseName}.icns"].writeBytes(IcnsBuilder.build(korge.getIconBytes()))
					copy { copy ->
						for (sourceSet in project.gkotlin.sourceSets) {
							copy.from(sourceSet.resources)
						}
						copy.into(resourcesFolder)
					}
					executableFile.copyTo(appMacOSFolder[korge.exeBaseName], overwrite = true)
					appMacOSFolder[korge.exeBaseName].setExecutable(true)
				}
			}
		}
	}
}