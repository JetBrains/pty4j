package com.pty4j.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal inline fun <reified T : Any> logger(): Logger = LoggerFactory.getLogger(T::class.java)
