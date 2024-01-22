package com.tubi.sbt.codegen

case class CodeGenConfig(
  byNameMapper: PartialFunction[(String, String), String] = PartialFunction.empty,
  byTypeMapper: PartialFunction[String, String] = PartialFunction.empty,
  ignoredColumns: (String, String) => Boolean = (_, _) => false,
  profile: String = "slick.jdbc.PostgresProfile",
  // some extra imports need for the generated code
  extraImports: Seq[String] = Seq.empty,
)
