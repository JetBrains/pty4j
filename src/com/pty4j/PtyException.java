/*
 * JPty - A small PTY interface for Java.
 * 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.pty4j;


/**
 * Exception instance for PtyHelpers specific exceptions.
 */
public class PtyException extends RuntimeException
{
  // VARIABLES

  private final int m_errno;

  // CONSTRUCTORS

  /**
   * Creates a new {@link PtyException} instance with the given error number.
   * 
   * @param errno
   *          the error number providing more details on the exact problem.
   */
  public PtyException(int errno)
  {
    m_errno = errno;
  }

  /**
   * Creates a new {@link PtyException} instance with the given message and
   * error number.
   * 
   * @param message
   *          the message for this exception, can be <code>null</code>;
   * @param errno
   *          the error number providing more details on the exact problem.
   */
  public PtyException(String message, int errno)
  {
    super( message );
    m_errno = errno;
  }

  // METHODS

  /**
   * Returns the error number.
   * 
   * @return the err number, > 0, or -1 if not defined.
   */
  public final int getErrno()
  {
    return m_errno;
  }
}
