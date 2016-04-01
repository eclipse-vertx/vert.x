package io.vertx.core.logging;

import io.vertx.core.spi.logging.LogDelegate;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.MessageFactory;

import static org.apache.logging.log4j.Level.*;
import static org.apache.logging.log4j.spi.AbstractLogger.DEFAULT_MESSAGE_FACTORY_CLASS;


public final class Log4j2LogDelegate implements LogDelegate {

    private final org.apache.logging.log4j.Logger logger;
    private final MessageFactory messageFactory;

    Log4j2LogDelegate(final String name) {
        logger = LogManager.getLogger(name);
        messageFactory = createMessageFactory();
    }

    private static MessageFactory createMessageFactory() {
        try {
            return DEFAULT_MESSAGE_FACTORY_CLASS.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }

    @Override
    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    @Override
    public boolean isTraceEnabled() {
        return logger.isTraceEnabled();
    }

    @Override
    public void fatal(final Object message) {
        logger.fatal(message);
    }

    @Override
    public void fatal(final Object message, final Throwable t) {
        logger.fatal(message, t);
    }

    @Override
    public void error(final Object message) {
        logger.error(message);
    }

    @Override
    public void error(Object o, Object... objects) {
        log(ERROR, o, objects);
    }

    @Override
    public void error(final Object message, final Throwable t) {
        logger.error(message, t);
    }

    @Override
    public void error(Object o, Throwable throwable, Object... objects) {
        log(ERROR, o, throwable, objects);
    }

    @Override
    public void warn(final Object message) {
        logger.warn(message);
    }

    @Override
    public void warn(Object o, Object... objects) {
        log(WARN, o, objects);
    }

    @Override
    public void warn(final Object message, final Throwable t) {
        logger.warn(message, t);
    }

    @Override
    public void warn(Object o, Throwable throwable, Object... objects) {
        log(WARN, o, throwable, objects);
    }

    @Override
    public void info(final Object message) {
        logger.info(message);
    }

    @Override
    public void info(Object o, Object... objects) {
        log(INFO, o, objects);
    }

    @Override
    public void info(final Object message, final Throwable t) {
        logger.info(message, t);
    }

    @Override
    public void info(Object o, Throwable throwable, Object... objects) {
        log(INFO, o, throwable, objects);
    }

    @Override
    public void debug(final Object message) {
        logger.debug(message);
    }

    @Override
    public void debug(Object o, Object... objects) {
        log(DEBUG, o, objects);
    }

    @Override
    public void debug(final Object message, final Throwable t) {
        logger.debug(message, t);
    }

    @Override
    public void debug(Object o, Throwable throwable, Object... objects) {
        log(DEBUG, o, throwable, objects);
    }

    @Override
    public void trace(final Object message) {
        logger.trace(message);
    }

    @Override
    public void trace(Object o, Object... objects) {
        log(TRACE, o, objects);
    }

    @Override
    public void trace(final Object message, final Throwable t) {
        logger.trace(message, t);
    }

    @Override
    public void trace(Object o, Throwable throwable, Object... objects) {
        log(TRACE, o, throwable, objects);
    }

    private void log(Level level, Object message, Object[] parameters) {
        logger.log(level, String.valueOf(message), parameters);
    }

    private void log(Level level, Object message, Throwable throwable,  Object[] parameters) {
        Message logMessage = messageFactory.newMessage(String.valueOf(message), parameters);
        logger.log(level, logMessage, throwable);
    }
}