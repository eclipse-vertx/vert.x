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

  def unregister_all
    @j_tu.unregisterAll
  end
end