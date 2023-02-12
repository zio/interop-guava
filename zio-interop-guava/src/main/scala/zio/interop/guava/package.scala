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

import com.google.common.util.concurrent.{FutureCallback, Futures, ListenableFuture}
import zio._

import java.util.concurrent.{CancellationException, CompletionException}
import java.util.{concurrent => juc}
import scala.concurrent.ExecutionException

package object guava {

  private def catchFromGet(isFatal: Throwable => Boolean): PartialFunction[Throwable, Task[Nothing]] = {
    case e: CompletionException   =>
      ZIO.fail(e.getCause)
    case e: ExecutionException    =>
      ZIO.fail(e.getCause)
    case _: InterruptedException  =>
      ZIO.interrupt
    case _: CancellationException =>
      ZIO.interrupt
    case e if !isFatal(e)         =>
      ZIO.fail(e)
  }

  private def unwrapDone[A](isFatal: Throwable => Boolean)(f: ListenableFuture[A]): Task[A] =
    try Exit.succeed(f.get())
    catch catchFromGet(isFatal)

  def fromListenableFuture[A](make: juc.Executor => ListenableFuture[A])(implicit trace: Trace): Task[A] =
    ZIO.uninterruptibleMask { restore =>
      ZIO.executor.flatMap { executor =>
        val ex: juc.Executor = executor.asExecutionContext.execute(_)
        ZIO.attempt(make(ex)).flatMap { lf =>
          ZIO.isFatalWith { fatal =>
            if (lf.isDone)
              unwrapDone(fatal)(lf)
            else {
              restore {
                ZIO.asyncInterrupt[Any, Throwable, A] { cb =>
                  val fcb = new FutureCallback[A] {
                    def onFailure(t: Throwable): Unit = cb(catchFromGet(fatal).lift(t).getOrElse(ZIO.die(t)))

                    def onSuccess(result: A): Unit = cb(ZIO.succeed(result))
                  }
                  Futures.addCallback(lf, fcb, ex)
                  Left(ZIO.succeed(lf.cancel(false)))
                }
              }.onInterrupt(ZIO.succeed(lf.cancel(false)))
            }
          }
        }
      }
    }

  def fromListenableFuture[A](lfUio: UIO[ListenableFuture[A]])(implicit trace: Trace): Task[A] =
    lfUio.flatMap(lf => fromListenableFuture(_ => lf))

  implicit class ListenableFutureOps[A](private val lfUio: UIO[ListenableFuture[A]]) extends AnyVal {
    def toZio: Task[A] = ZIO.fromListenableFuture(lfUio)
  }

  implicit class ZioObjListenableFutureOps(private val zioObj: ZIO.type) extends AnyVal {
    def fromListenableFuture[A](make: juc.Executor => ListenableFuture[A]): Task[A] =
      guava.fromListenableFuture(make)

    def fromListenableFuture[A](lfUio: UIO[ListenableFuture[A]]): Task[A] =
      guava.fromListenableFuture(lfUio)
  }

  implicit class FiberObjOps(private val fiberObj: Fiber.type) extends AnyVal {
    def fromListenableFuture[A](thunk: => ListenableFuture[A]): Fiber[Throwable, A] = {
      lazy val lf: ListenableFuture[A] = thunk

      new Fiber.Synthetic.Internal[Throwable, A] {

        override def await(implicit trace: Trace): UIO[Exit[Throwable, A]] =
          ZIO.fromListenableFuture(_ => lf).exit

        override def children(implicit trace: Trace): UIO[Chunk[Fiber.Runtime[_, _]]] =
          ZIO.succeed(Chunk.empty)

        override def poll(implicit trace: Trace): UIO[Option[Exit[Throwable, A]]] =
          ZIO.suspendSucceed {
            if (lf.isDone)
              ZIO
                .isFatalWith(fatal => unwrapDone(fatal)(lf))
                .fold(Exit.fail, Exit.succeed)
                .map(Some(_))
            else
              ZIO.succeed(None)
          }

        override def id: FiberId = FiberId.None

        override def interruptAsFork(fiberId: FiberId)(implicit trace: Trace): UIO[Unit] =
          ZIO.attempt(lf.cancel(false)).ignore

        override def inheritAll(implicit trace: Trace): UIO[Unit] = ZIO.unit

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
