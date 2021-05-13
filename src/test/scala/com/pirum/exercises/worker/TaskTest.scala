package com.pirum.exercises.worker

import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

import zio.clock.Clock
import zio.duration.{Duration => ZDuration}
import zio.test.Assertion._
import zio.test._
import zio.test.environment.TestClock
import zio.{RIO, URIO, ZIO, Task => ZTask}

object TaskTest extends DefaultRunnableSpec {

  def createTask(name: String, duration: Duration, success: Boolean): URIO[Clock, Task] = {
    val task: ZTask[Unit] = if (success) ZIO.unit else ZIO.fail(new RuntimeException("Boom!"))
    val withDuration: RIO[Clock, Unit] = ZIO.sleep(zio.duration.Duration.fromScala(duration)) *> task
    ZIO.access[Clock](clock => Task(name, withDuration.provide(clock)))
  }

  def runTask(task: Task, timeout: FiniteDuration) = for {
    resultF <- Task.runTask(task, timeout).fork
    _ <- TestClock.adjust(ZDuration.fromScala(timeout + 1.second))
    result <- resultF.join
  } yield result

  def runWithTimer[A, R](program: URIO[R, A]): URIO[R with Clock, (A, FiniteDuration)] = for {
    start <- ZIO.accessM[Clock](_.get.instant)
    a <- program
    end <- ZIO.accessM[Clock](_.get.instant)
  } yield {
    val duration = FiniteDuration(java.time.Duration.between(start, end).toNanos, TimeUnit.NANOSECONDS)
    (a, duration)
  }

  def testProgram(
    tasks: List[Task],
    timeout: FiniteDuration,
    workers: Int,
    expectedSuccess: List[String],
    expectedFail: List[String],
    expectedTimeout: List[String],
    expectedProgramDuration: FiniteDuration
  ) = for {
      fork <- runWithTimer(Task.runTasks(tasks, timeout, workers)).fork
      _ <- TestClock.adjust(ZDuration.fromScala(timeout + 1.second))
      (results, duration) <- fork.join
      success = results.filter(_.status == TaskStatus.Successful).map(_.name)
      failed = results.filter(_.status == TaskStatus.Failed).map(_.name)
      timeout = results.filter(_.status == TaskStatus.TimedOut).map(_.name)
    } yield
      assert((success, failed, timeout))(equalTo((expectedSuccess, expectedFail, expectedTimeout))) &&
      assert(duration)(equalTo(expectedProgramDuration))

  def spec = suite("Task tests")(

    testM("Task success") {
      for {
        task <- createTask("t1", 5.seconds, true)
        result <- runTask(task, 10.seconds)
      } yield assert(result)(equalTo(TaskResult("t1", TaskStatus.Successful, 5.seconds)))
    },

    testM("Task failed") {
      for {
        task <- createTask("t2", 2.seconds, false)
        result <- runTask(task, 10.seconds)
      } yield assert(result)(equalTo(TaskResult("t2", TaskStatus.Failed, 2.seconds)))
    },

    testM("Task timeout") {
      for {
        task <- createTask("t3", 50.seconds, true)
        result <- runTask(task, 10.seconds)
      } yield assert(result)(equalTo(TaskResult("t3", TaskStatus.TimedOut, 10.seconds)))
    },

    testM("Program test 1") {
      for {
        t1 <- createTask("t1", 1.second, false)
        t2 <- createTask("t2", 2.second, true)
        t3 <- createTask("t3", 3.second, false)
        t4 <- createTask("t4", 4.second, true)

        testResult <- testProgram(
          tasks = List(t1, t2, t3, t4),
          timeout = 8.seconds,
          workers = 4,
          expectedSuccess = List("t2", "t4"),
          expectedFail = List("t1", "t3"),
          expectedTimeout = Nil,
          expectedProgramDuration = 4.seconds
        )
      } yield testResult
    },

    testM("Program test 2") {
      for {
        t1 <- createTask("t1", 1.second, true)
        t2 <- createTask("t2", 2.second, true)
        t3 <- createTask("t3", 3.second, false)
        t4 <- createTask("t4", 4.second, true)
        t5 <- createTask("t5", Duration.Inf, true)

        testResult <- testProgram(
          tasks = List(t1, t2, t3, t4, t5),
          timeout = 8.seconds,
          workers = 4,
          expectedSuccess = List("t1", "t2", "t4"),
          expectedFail = List("t3"),
          expectedTimeout = List("t5"),
          expectedProgramDuration = 8.seconds
        )
      } yield testResult
    }
  )
}