eb = vertx.eventBus

eb.registerHandler('demo.orderMgr') { message ->
  validateUser(message)
}

def validateUser(message) {
  eb.send('demo.authMgr.validate', [ sessionID: message.body['sessionID'] ]) { reply ->
    if (reply.body['status'] == 'ok') {
      message.body['username'] = reply.body['username']
      saveOrder(message)
    } else {
      println "Failed to validate user"
    }
  }
}

def saveOrder(message) {
  eb.send('demo.persistor',
      [action: 'save', collection: 'orders', document: message.body]) { reply ->
    if (reply.body['status'] == 'ok') {
      println "order successfully processed!"
      message.reply([status: 'ok']) // Reply to the front end
    } else {
      println "Failed to save order"
    }
  }
}