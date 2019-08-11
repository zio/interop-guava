/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.interop

import java.util.concurrent.{ CompletionException, Future }

import com.google.common.util.concurrent.{ FutureCallback, Futures, ListenableFuture }
import zio._

import scala.concurrent.{ ExecutionContext, ExecutionException }
import scala.util.control.NonFatal

object guava {

  private def catchFromGet[A]: PartialFunction[Throwable, Task[Nothing]] = {
    case e: CompletionException =>
      Task.fail(e.getCause)
    case e: ExecutionException =>
      Task.fail(e.getCause)
    case _: InterruptedException =>
      Task.interrupt
    case NonFatal(e) =>
      Task.fail(e)
  }

  private def unwrapDone[A](f: Future[A]): Task[A] =
    try {
      Task.succeed(f.get())
    } catch catchFromGet

  def fromListenableFuture[A](make: ExecutionContext => ListenableFuture[A]): Task[A] =
    Task.descriptorWith { d =>
      Task.effectSuspend {
        val ec = d.executor.asEC
        val lf = make(ec)
        if (lf.isDone) {
          unwrapDone(lf)
        } else {
          Task.effectAsync { cb =>
            val fcb = new FutureCallback[A] {
              def onFailure(t: Throwable): Unit = cb(catchFromGet.lift(t).getOrElse(Task.die(t)))
              def onSuccess(result: A): Unit    = cb(Task.succeed(result))
            }
            Futures.addCallback(lf, fcb, ec.execute(_))
          }
        }
      }
    }

  def fromListenableFuture[A](lfUio: UIO[ListenableFuture[A]]): Task[A] =
    lfUio.flatMap(lf => fromListenableFuture(_ => lf))

  implicit class ListenableFutureOps[A](private val lfUio: UIO[ListenableFuture[A]]) extends AnyVal {
    def toZio: Task[A] = Task.fromListenableFuture(lfUio)
  }

  implicit class TaskObjListenableFutureOps(private val taskObj: Task.type) extends AnyVal {

    def fromListenableFuture[A](make: ExecutionContext => ListenableFuture[A]): Task[A] =
      guava.fromListenableFuture(make)

    def fromListenableFuture[A](lfUio: UIO[ListenableFuture[A]]): Task[A] =
      guava.fromListenableFuture(lfUio)

  }

  implicit class ZioObjListenableFutureOps(private val zioObj: ZIO.type) extends AnyVal {

    def fromListenableFuture[A](make: ExecutionContext => ListenableFuture[A]): Task[A] =
      guava.fromListenableFuture(make)

    def fromListenableFuture[A](lfUio: UIO[ListenableFuture[A]]): Task[A] =
      guava.fromListenableFuture(lfUio)

  }

  implicit class FiberObjOps(private val fiberObj: Fiber.type) extends AnyVal {

    def fromListenableFuture[A](thunk: => ListenableFuture[A]): Fiber[Throwable, A] = {

      lazy val lf = thunk

      new Fiber[Throwable, A] {

        override def await: UIO[Exit[Throwable, A]] = Task.fromListenableFuture(UIO.effectTotal(lf)).run

        override def poll: UIO[Option[Exit[Throwable, A]]] =
          UIO.effectSuspendTotal {
            if (lf.isDone) {
              Task
                .effectSuspend(unwrapDone(lf))
                .fold(Exit.fail, Exit.succeed)
                .map(Some(_))
            } else {
              UIO.succeed(None)
            }
          }

        override def interrupt: UIO[Exit[Throwable, A]] = join.fold(Exit.fail, Exit.succeed)

        override def inheritFiberRefs: UIO[Unit] = UIO.unit
      }
    }

  }

  implicit class TaskListenableFutureOps[A](private val io: Task[A]) extends AnyVal {
    def toListenableFuture: UIO[ListenableFuture[A]] =
      io.fold(Futures.immediateFailedFuture[A], Futures.immediateFuture[A])
  }

  implicit class IOListenableFutureOps[E, A](private val io: IO[E, A]) extends AnyVal {
    def toListenableFutureWith(f: E => Throwable): UIO[ListenableFuture[A]] =
      io.mapError(f).toListenableFuture
  }

}
