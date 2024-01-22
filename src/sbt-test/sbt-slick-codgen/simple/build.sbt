lazy val root = (project in file("."))
  .settings(version := "0.1", scalaVersion := "2.10.6", logLevel := Level.Info)
  .enablePlugins(SlickCodeGenPlugin)
  .settings(
    defaultCodeGen(
      slickCodegenOutputContainer := "Etl",
      slickCodegenOutputPackage := "com.tubitv.mercury.datamodel.schemas"
    ),
    codeGen("Etl")(slickCodegenOutputContainer := "Schema"),
  )
