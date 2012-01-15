class TestUtils
  def initialize
    @j_tu = org.vertx.java.newtests.TestUtils.new
  end

  def azzert(result, message = nil)
    if message
      @j_tu.azzert(result, message)
    else
      @j_tu.azzert(result)
    end
  end

  def app_ready
    @j_tu.appReady
  end

  def app_stopped
    @j_tu.appStopped
  end

  def test_complete()
    @j_tu.testComplete
  end

  def register(test_name, &test_handler)
    @j_tu.register(test_name, test_handler)
  end

  def register_all(object)
    methods = object.private_methods
    methods.each do |meth|
      if meth.start_with? 'test_'
        register(meth) { object.method(meth).call }
      end
    end
  end

  def unregister_all
    @j_tu.unregisterAll
  end

  def TestUtils.gen_buffer(size)
    j_buff = org.vertx.java.newtests.TestUtils.generateRandomBuffer(size)
    Buffer.new(j_buff)
  end

  def TestUtils.buffers_equal(buff1, buff2)
    org.vertx.java.newtests.TestUtils.buffersEqual(buff1._to_java_buffer, buff2._to_java_buffer)
  end
end