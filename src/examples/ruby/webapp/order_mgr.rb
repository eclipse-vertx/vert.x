require('vertx')
require('json')

@eb = Vertx::EventBus

@eb.register_handler('demo.orderMgr') do |message|
  validateUser(message)
end

def validateUser(message)
  @eb.send('demo.authMgr.validate', { 'sessionID' => message.body['sessionID'] }) do |reply|
    if reply.body['status'] == 'ok'
      message.body['username'] = reply.body['username']
      saveOrder(message)
    else
      puts "Failed to validate user"
    end
  end
end

def saveOrder(message)
  @eb.send('demo.persistor',
      {'action' => 'save', 'collection' => 'orders', 'document' => message.body}) do |reply|
    if reply.body['status'] === 'ok'
      puts "order successfully processed!"
      message.reply({'status' => 'ok'}) # Reply to the front end
    else
      puts "Failed to save order"
    end
  end
end
