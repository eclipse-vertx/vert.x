
import org.vertx.java.deploy.impl.VertxLocator
import org.vertx.java.framework
import org.vertx.java.core.Handler
import java.lang

from core.buffer import Buffer

class TestUtils(object):
    def __init__(self):
        self.java_obj = org.vertx.java.framework.TestUtils(org.vertx.java.deploy.impl.VertxLocator.vertx)

    def azzert(self, result, message=None):
        try:
            if message:
                self.java_obj.azzert(result, message)
            else:
                self.java_obj.azzert(result)
        except java.lang.RuntimeException:
            # Rethrow as a python exception so we see nice python backtrace
            if message:
                raise RuntimeError("Assertion Failed %s"% message)
            else:
                raise RuntimeError("Assertion Failed ")

    def app_ready(self):
        self.java_obj.appReady()

    def app_stopped(self):
        self.java_obj.appStopped()

    def test_complete(self):
        self.java_obj.testComplete()
 
    def register(self, test_name, test_method):
        self.java_obj.register(test_name, TestHandler(test_method))
    
    def register_all(self, obj):
        for meth in dir(obj):
            if meth.startswith("test_"):
                self.register(meth, getattr(obj, meth))
        
    def unregister_all(self):
        self.java_obj.unregisterAll()

    def check_context(self):
        return self.java_obj.checkContext()

    @staticmethod
    def gen_buffer(size):
        j_buff = org.vertx.java.framework.TestUtils.generateRandomBuffer(size)
        return Buffer(j_buff)

    @staticmethod
    def random_unicode_string(self, size):
        return org.vertx.java.framework.TestUtils.randomUnicodeString(size)

    @staticmethod
    def buffers_equal(buff1, buff2):
        return org.vertx.java.framework.TestUtils.buffersEqual(buff1._to_java_buffer(), buff2._to_java_buffer())

class TestHandler(org.vertx.java.core.Handler):
    """ Test handler """
    def __init__(self, handler):
        self.handler = handler

    def handle(self, nothing=None):
        """ hanlder called by test """
        self.handler()
