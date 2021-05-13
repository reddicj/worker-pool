package com.pirum.exercises.worker

import scala.concurrent.duration.FiniteDuration

import zio.Runtime
import zio.console._

object Main extends App {

  def program(tasks: List[Task], timeout: FiniteDuration, workers: Int): Unit = Runtime.default.unsafeRun(
    Task.runTasks(tasks, timeout, workers)
      .map(printResults)
      .flatMap(putStrLn(_))
  )

  def printResults(results: List[TaskResult]): String = {

    def print(status: TaskStatus): String = results
      .filter(_.status == status)
      .map(_.name)
      .mkString("[", ", ", "]")

    List[String](
      s"result.successful = ${print(TaskStatus.Successful)}",
      s"result.failed = ${print(TaskStatus.Failed)}",
      s"result.timedOut = ${print(TaskStatus.TimedOut)}",
    ).mkString("\n")
  }
}
