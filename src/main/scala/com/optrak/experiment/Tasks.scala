package com.optrak.experiment

object Tasks {
  case class Task(id: Int, title: String, description: String = "", done: Boolean = false)
}
