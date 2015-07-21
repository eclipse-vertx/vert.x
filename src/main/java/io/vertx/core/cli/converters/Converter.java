package io.vertx.core.cli.converters;

/**
 * The converter interface to convert {@code String}s to {@code Object}s.
 */
public interface Converter<T> {

  T fromString(String s);

}
