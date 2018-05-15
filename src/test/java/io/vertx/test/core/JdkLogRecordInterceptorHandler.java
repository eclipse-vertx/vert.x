package io.vertx.test.core;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

public class JdkLogRecordInterceptorHandler extends Handler{

	private Consumer<LogRecord> consumer;
	
	public JdkLogRecordInterceptorHandler(Consumer<LogRecord> consumer) {
		this.consumer = Objects.requireNonNull(consumer);
	}
	
	@Override
	public void publish(LogRecord record) {
		consumer.accept(record);
	}

	@Override
	public void flush() {
	}

	@Override
	public void close() throws SecurityException {
	}

}
