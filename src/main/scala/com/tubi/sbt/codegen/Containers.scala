package com.tubi.sbt.codegen

import sbt.Logger

import scala.concurrent.{Await, Promise}
import scala.concurrent.duration.Duration
import scala.util.Success

import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.{Frame, PortBinding}
import com.github.dockerjava.core.DockerClientBuilder

object Containers {
  def runPostgres(exportPort: Int, password: String, logger: Logger): AutoCloseable = {
    logger.info("Starting docker container [postgres:13.7]")
    val dockerClient = DockerClientBuilder.getInstance.build
    val container = dockerClient
      .createContainerCmd("postgres:13.7")
      .withEnv(s"POSTGRES_PASSWORD=$password")

    container.getHostConfig
      .withPortBindings(PortBinding.parse(s"$exportPort:5432"))

    val containerId = container.exec().getId
    dockerClient.startContainerCmd(containerId).exec()

    val ready = Promise[Unit]()
    dockerClient
      .logContainerCmd(containerId)
      .withFollowStream(true)
      .withSince(0)
      .withStdOut(true)
      .withStdErr(true)
      .exec(new ResultCallback.Adapter[Frame] {
        override def onNext(`object`: Frame): Unit = {
          val lines = new String(`object`.getPayload).split("\n")
          if (lines.exists(_.endsWith("database system is ready to accept connections"))) {
            ready.complete(Success(()))
            this.close()
          }
        }
      })

    Await.result(ready.future, Duration.Inf)
    logger.info("Postgres start success")

    () => {
      try {
        dockerClient.stopContainerCmd(containerId).exec()
      } catch {
        case _: Throwable =>
      }
      dockerClient.removeContainerCmd(containerId).exec()
    }
  }
}
