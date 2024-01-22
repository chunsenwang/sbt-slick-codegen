This is a sbt plugin that helps to generate slick code, it integrated with  postgres docker and flyway, to make life  easier.

### How to use
1. add this plugin to your project by  
   `addSbtPlugin("com.tubitv" % "sbt-slick-codegen" % "<the version>")`
2. Enable this plugin and change some necessary configs,eg
    ```sbt
    enablePlugins(SlickCodeGenPlugin)
    defaultCodeGen(
      slickCodegenOutputContainer := "Etl",
      slickCodegenOutputPackage := "com.demo.models.schemas"
    )
   ```
3. Then generate the code by the sbt task `slickGenerateAll`
4. To make sure the generated code is always uptodate, we can add the `slickVerifyAll` to CI. eg:   
   ```sbt
    validate := Def
        .sequential(
          <... your checks>,
          slickVerifyAll
        ).value,
   ```
   This will fail the CI if the db structure updated, but the generated code not. 

### Multiple  schema generate
When you need to generate multiple schemas in the same project, you can do it by 
```sbt
enablePlugins(SlickCodeGenPlugin)
defaultCodeGen(
  slickCodegenOutputContainer := "Scheam",
  slickCodegenOutputPackage := "com.tubitv.mercury.datamodel.schemas"
)

codeGen("Etl")(
  slickCodegenOutputContainer := "Schema",
  slickCodegenIncludedTables := Seq("some table"),
  slickCodeGenCustomize := new CodeGenConfig(
    byNameMapper = {
      case (_, "status") => "StatusClass"
    }
  )
)
```