/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.apache.geode.management.internal.rest;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

public class ManagementLoggingFilter extends OncePerRequestFilter {

  // Because someone is going to want to disable this.
  private static final Boolean ENABLE_REQUEST_LOGGING =
      Boolean.parseBoolean(System.getProperty("geode.management.request.logging", "true"));

  private static int MAX_PAYLOAD_LENGTH = 10000;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {

    if (!ENABLE_REQUEST_LOGGING) {
      filterChain.doFilter(request, response);
      return;
    }

    // We can not log request payload before making the actual request because then the InputStream
    // would be consumed and cannot be read again by the actual processing/server.
    ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
    ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

    // performs the actual request before logging
    filterChain.doFilter(wrappedRequest, wrappedResponse);

    // log after the request has been made and ContentCachingRequestWrapper has cached the request
    // payload.
    String requestPattern = "Management Request: %s[url=%s]; user=%s; payload=%s";
    String requestUrl = request.getRequestURI();
    if (request.getQueryString() != null) {
      requestUrl = requestUrl + "?" + request.getQueryString();
    }
    String payload = getContentAsString(wrappedRequest.getContentAsByteArray(),
        wrappedRequest.getCharacterEncoding());
    logger.info(String.format(requestPattern, request.getMethod(), requestUrl,
        request.getRemoteUser(), payload));

    // construct the response message
    String responsePattern = "Management Response: Status=%s; response=%s";
    payload = getContentAsString(wrappedResponse.getContentAsByteArray(),
        wrappedResponse.getCharacterEncoding());
    payload = payload.replaceAll(System.lineSeparator(), "");
    logger.info(String.format(responsePattern, response.getStatus(), payload));

    // IMPORTANT: copy content of response back into original response
    wrappedResponse.copyBodyToResponse();
  }

  private String getContentAsString(byte[] buf, String encoding) {
    if (buf == null || buf.length == 0)
      return "";
    int length = Math.min(buf.length, MAX_PAYLOAD_LENGTH);
    try {
      return new String(buf, 0, length, encoding);
    } catch (UnsupportedEncodingException ex) {
      return "[unknown]";
    }
  }
}
