// Licensed to the Software Freedom Conservancy (SFC) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The SFC licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.openqa.selenium.remote.http;

import org.openqa.selenium.TimeoutException;

import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;

import java.net.ConnectException;
import java.util.logging.Logger;

import static com.google.common.net.HttpHeaders.CONTENT_LENGTH;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_UNAVAILABLE;
import static org.openqa.selenium.internal.Debug.getDebugLogLevel;

public class RetryRequest implements Filter {

  private static final Logger LOG = Logger.getLogger(RetryRequest.class.getName());

  // Retry on connection error.
  private static final RetryPolicy<Object> connectionFailurePolicy =
    RetryPolicy.builder()
      .handleIf(failure -> failure.getCause() instanceof ConnectException)
      .withMaxRetries(3)
      .onRetry(e -> LOG.log(
        getDebugLogLevel(),
        "Connection failure #{0}. Retrying.",
        e.getAttemptCount()))
      .build();

  // Retry on read timeout.
  private static final RetryPolicy<Object> readTimeoutPolicy =
    RetryPolicy.builder()
      .handle(TimeoutException.class)
      .withMaxRetries(3)
      .onRetry(e -> LOG.log(
        getDebugLogLevel(),
        "Read timeout #{0}. Retrying.",
        e.getAttemptCount()))
      .build();

  // Retry if server is unavailable or an internal server error occurs without response body.
  private static final RetryPolicy<Object> serverErrorPolicy =
    RetryPolicy.builder()
      .handleResultIf(response -> ((HttpResponse)response).getStatus() == HTTP_INTERNAL_ERROR &&
                                  Integer.parseInt(((HttpResponse)response).getHeader(CONTENT_LENGTH)) == 0)
      .handleResultIf(response -> ((HttpResponse)response).getStatus() == HTTP_UNAVAILABLE)
      .withMaxRetries(2)
      .onRetry(e -> LOG.log(
        getDebugLogLevel(),
        "Failure due to server error #{0}. Retrying.",
        e.getAttemptCount()))
      .build();

  @Override
  public HttpHandler apply(HttpHandler next) {
    return req -> Failsafe.with(
      connectionFailurePolicy,
      readTimeoutPolicy,
      serverErrorPolicy)
      .get(() -> next.execute(req));
  }
}
