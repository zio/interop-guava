---
id: index
title: "Introduction to ZIO Interop Guava"
sidebar_label: "ZIO Interop Guava"
---

This library provides an interoperability layer with [Guava](https://guava.dev/)'s [`com.google.common.util.concurrent.ListenableFuture`](https://github.com/google/guava/wiki/ListenableFutureExplained).

@PROJECT_BADGES@

[//]: # (## Introduction)

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-interop-guava" % "@VERSION@"
```

## Example

Please see examples [here](https://github.com/zio/interop-guava/blob/master/src/test/scala/zio/interop/guava/GuavaSpec.scala).
