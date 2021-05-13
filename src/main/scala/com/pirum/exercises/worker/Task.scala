package com.pirum.exercises.worker

import java.time.Instant
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

import zio.clock.Clock
import zio.duration.{Duration => ZDuration}
import zio.stream.ZStream
import zio.{URIO, ZIO, Task => ZTask}

sealed trait TaskStatus
object TaskStatus {
  case object Successful extends TaskStatus
  case object Failed extends TaskStatus
  case object TimedOut extends TaskStatus
}

final case class TaskResult(name: String, status: TaskStatus, duration: FiniteDuration)

final case class Task(name: String, runnable: ZTask[Unit])

object Task {

  def runTasks(tasks: List[Task], timeout: FiniteDuration, workers: Int): URIO[Clock, List[TaskResult]] = {

    def runTaskWithRemainingTime(task: Task, programStart: Instant): URIO[Clock, TaskResult] =
      ZIO.accessM[Clock](_.get.instant).flatMap { taskStart =>
        runTask(task, remainingTime(programStart, taskStart, timeout))
      }

    def run(programStart: Instant): URIO[Clock, List[TaskResult]] = ZStream.fromIterable(tasks)
      .mapMParUnordered(workers)(runTaskWithRemainingTime(_, programStart))
      .runCollect
      .map(_.toList.sortBy(_.duration))

    ZIO.accessM[Clock](_.get.instant).flatMap(run)
  }

  def runTask(task: Task, timeout: FiniteDuration): URIO[Clock, TaskResult] = runWithTimer(task)
    .timeoutFail(TaskResult(task.name, TaskStatus.TimedOut, timeout))(ZDuration.fromScala(timeout))
    .merge

  private def runWithTimer(task: Task): URIO[Clock, TaskResult] = for {
    start <- ZIO.accessM[Clock](_.get.instant)
    result <- task.runnable.either
    end <- ZIO.accessM[Clock](_.get.instant)
  } yield {
    val status = result.fold(_ => TaskStatus.Failed, _ => TaskStatus.Successful)
    val duration = FiniteDuration(java.time.Duration.between(start, end).toNanos, TimeUnit.NANOSECONDS)
    TaskResult(task.name, status, duration)
  }

  private def remainingTime(
    programStart: Instant,
    taskStart: Instant,
    timeout: FiniteDuration
  ): FiniteDuration = {
    val elapsedTime = FiniteDuration(java.time.Duration.between(programStart, taskStart).toNanos, TimeUnit.NANOSECONDS)
    if (elapsedTime > timeout) 0.seconds
    else timeout - elapsedTime
  }
}