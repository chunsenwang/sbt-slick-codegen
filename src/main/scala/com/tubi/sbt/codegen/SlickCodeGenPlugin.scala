package com.tubi.sbt.codegen

import sbt.*

import scala.io.Source
import scala.util.Using

import _root_.com.github.tototoshi.sbt.slick.CodegenPlugin
import _root_.io.github.davidmweber.FlywayPlugin

object SlickCodeGenPlugin extends AutoPlugin {

  private val dbUser = "postgres"
  private val dbPass = "password"
  private def dbUrl(port: Int) = s"jdbc:postgresql://127.0.0.1:$port/postgres"

  override def trigger = noTrigger

  private val codeGenConfigs = settingKey[Seq[Configuration]]("the configurations for code gen")
  private val codegenVerifyConfig = settingKey[Configuration]("the verify configuration")

  private val DefaultCodeGenConfig = config("codeGen")
  private val DefaultCodeGenVerify = config("codeGenVerify")

  object autoImport {
    val verifyOutputDir = settingKey[File]("The output dir where genrated code put to when do verify")

    val slickVerify = taskKey[Unit]("")
    val slickVerifyAll = taskKey[Unit]("Verify the generated code is not out of date")
    val slickGenerateAll = taskKey[Unit]("Generate all slick code")

    val slickCodeGenCustomize = settingKey[CodeGenConfig]("The customize configuration for the code gen")

    val postgresContainerPort = settingKey[Int]("The port of postgres port")

    def defaultCodeGen(setting: Setting[_]*): Seq[Setting[_]] =
      codeGenSettings(DefaultCodeGenConfig, DefaultCodeGenVerify, setting)

    def codeGen(configName: String)(setting: Setting[_]*): Seq[Setting[_]] = {
      def createConfig(name: String) = Configuration.of(name.take(1).toUpperCase() + name.drop(1), name)

      val theConfig = createConfig(configName)
      val verifyConfig = createConfig(configName + "Verify") extend theConfig
      codeGenSettings(theConfig, verifyConfig, setting)
    }
  }

  // *********************
  // code gen defaults
  // *********************
  import autoImport.*

  override def projectSettings: Seq[Setting[_]] = {
    import FlywayPlugin.autoImport.*

    FlywayPlugin.projectSettings ++
      Seq(
        postgresContainerPort := 15533,
        flywayUrl := dbUrl(postgresContainerPort.value),
        flywayUser := dbUser,
        flywayPassword := dbPass,
        flywayLocations := Seq(s"filesystem:${(Compile / Keys.resourceDirectory).value.getAbsoluteFile}/db/migration"),
        codeGenConfigs := Seq.empty,
        slickGenerateAll := withDb(Def.taskDyn {
          val configs = codeGenConfigs.value
          Def.sequential(configs.map(c => c / CodegenPlugin.autoImport.slickCodegen))
        }).value,
        verifyOutputDir := new File(Keys.target.value.getAbsolutePath + "/slick-verify"),
        slickVerifyAll := withDb(Def.taskDyn {
          val configs = codeGenConfigs.value
          Def.sequential(configs.map(c => c / slickVerify))
        }).value,
      ) ++ defaultCodeGen()
  }

  private def codeGenSettings(
    config: Configuration,
    verifyConfig: Configuration,
    settings: Seq[Setting[_]]
  ): Seq[Setting[_]] = {
    import CodegenPlugin.autoImport.*

    val commonSettings =
      CodegenPlugin.projectSettings ++
        Seq(
          slickCodegenDatabaseUrl := dbUrl(postgresContainerPort.value),
          slickCodegenDatabaseUser := dbUser,
          slickCodegenDatabasePassword := dbPass,
          slickCodeGenCustomize := CodeGenConfig(),
          slickCodegenDriver := new CodeGenPostgresProfile(slickCodeGenCustomize.value),
          slickCodegenCodeGenerator := (m => new CustomizeSourceCodeGenerator(m, slickCodeGenCustomize.value)),
          slickCodegenOutputFile := s"${slickCodegenOutputContainer.value}.scala",
        ) ++ settings

    Seq(codeGenConfigs += config) ++
      inConfig(config)(
        commonSettings ++ Seq(
          codegenVerifyConfig := verifyConfig,
          slickCodegenOutputDir := (Compile / Keys.scalaSource).value,
          slickVerify := Def.taskDyn {
            val vc = codegenVerifyConfig.value
            Def.sequential(
              vc / slickCodegen,
              Def.task {
                val (gf, vf) = verifyFiles.value
                Using.resources(Source.fromFile(gf), Source.fromFile(vf)) {
                  (genreated, verify) =>
                    if (genreated.mkString != verify.mkString) {
                      throw new Exception(s"Schema file: $gf is out of date, please regenerate it")
                    }
                }
              }
            )
          }.value
        )
      ) ++
      inConfig(verifyConfig)(commonSettings ++ Seq(slickCodegenOutputDir := verifyOutputDir.value))
  }

  /** Run the task with db ready , will start the postgres docker, and run flyway migrate before the task
    * and also, it will make sure stop and remove the container after the task
    * @param task the task to be executed
    * @tparam A
    * @return
    */
  private def withDb[A](task: Def.Initialize[Task[A]]): Def.Initialize[Task[A]] = Def.taskDyn {
    val containerClosable = Def.task {
      Containers.runPostgres(postgresContainerPort.value, dbPass, Keys.streams.value.log)
    }.value

    Def
      .sequential(FlywayPlugin.autoImport.flywayMigrate, task)
      .andFinally(containerClosable.close())
  }

  private def verifyFiles = Def.taskDyn {
    import CodegenPlugin.autoImport._

    val generatedFile = s"${slickCodegenOutputDir.value.getAbsolutePath}/${slickCodegenOutputPackage.value
        .replace('.', '/')}/${slickCodegenOutputFile.value}"

    val vc = codegenVerifyConfig.value
    Def.task {
      generatedFile -> s"${(vc / slickCodegenOutputDir).value.getAbsolutePath}/${(vc / slickCodegenOutputPackage).value
          .replace('.', '/')}/${(vc / slickCodegenOutputFile).value}"
    }
  }
}
