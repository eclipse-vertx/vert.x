package io.vertx.benchmarks;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import io.netty.handler.codec.DefaultHeaders;
import io.netty.handler.codec.http.DefaultHttpHeadersFactory;
import io.vertx.core.http.impl.HttpUtils;

@State(Scope.Thread)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 400, timeUnit = TimeUnit.MILLISECONDS)
public class HeadersValidationBenchmark extends BenchmarkBase {

  @Param({ "true", "false" })
  public boolean ascii;

  private CharSequence[] headerNames;
  private CharSequence[] headerValues;
  private static final DefaultHeaders.NameValidator<CharSequence> nettyNameValidator = DefaultHttpHeadersFactory.headersFactory().getNameValidator();
  private static final DefaultHeaders.ValueValidator<CharSequence> nettyValueValidator = DefaultHttpHeadersFactory.headersFactory().getValueValidator();
  private static final Consumer<CharSequence> vertxNameValidator = HttpUtils::validateHeaderName;
  private static final Consumer<CharSequence> vertxValueValidator = HttpUtils::validateHeaderValue;

  @Setup
  public void setup() {
    headerNames = new CharSequence[4];
    headerValues = new CharSequence[4];
    headerNames[0] = io.vertx.core.http.HttpHeaders.CONTENT_TYPE;
    headerValues[0] = io.vertx.core.http.HttpHeaders.createOptimized("text/plain");
    headerNames[1] = io.vertx.core.http.HttpHeaders.CONTENT_LENGTH;
    headerValues[1] = io.vertx.core.http.HttpHeaders.createOptimized("20");
    headerNames[2] = io.vertx.core.http.HttpHeaders.SERVER;
    headerValues[2] = io.vertx.core.http.HttpHeaders.createOptimized("vert.x");
    headerNames[3] = io.vertx.core.http.HttpHeaders.DATE;
    headerValues[3] = io.vertx.core.http.HttpHeaders.createOptimized(HeadersUtils.DATE_FORMAT.format(new java.util.Date(0)));
    if (!ascii) {
      for (int i = 0; i < headerNames.length; i++) {
        headerNames[i] = headerNames[i].toString();
      }
    }
    if (!ascii) {
      for (int i = 0; i < headerValues.length; i++) {
        headerValues[i] = headerValues[i].toString();
      }
    }
  }

  @Benchmark
  public void validateNameNetty() {
    for (CharSequence headerName : headerNames) {
      nettyNameValidation(headerName);
    }
  }

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  private void nettyNameValidation(CharSequence headerName) {
    nettyNameValidator.validateName(headerName);
  }

  @Benchmark
  public void validateNameVertx() {
    for (CharSequence headerName : headerNames) {
      vertxNameValidation(headerName);
    }
  }

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  private void vertxNameValidation(CharSequence headerName) {
    vertxNameValidator.accept(headerName);
  }


  @Benchmark
  public void validateValueNetty() {
    for (CharSequence headerValue : headerValues) {
      nettyValueValidation(headerValue);
    }
  }

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  private void nettyValueValidation(CharSequence headerValue) {
    nettyValueValidator.validate(headerValue);
  }

  @Benchmark
  public void validateValueVertx() {
    for (CharSequence headerValue : headerValues) {
      vertxValueValidation(headerValue);
    }
  }

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  private void vertxValueValidation(CharSequence headerValue) {
    vertxValueValidator.accept(headerValue);
  }
}
