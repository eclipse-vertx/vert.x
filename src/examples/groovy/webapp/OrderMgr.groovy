import groovy.json.JsonBuilder

/*
* Copyright 2011-2012 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

eb = vertx.eventBus()
log = container.logger

def handler = { orderMsg ->
  def order = orderMsg.body

  log.info "Received order in order manager ${stringify(order)}"
  def sessionID = order['sessionID']
  eb.send('demo.authMgr.validate', [ 'sessionID': sessionID ], { replyMsg ->
    def reply = replyMsg.body
    if (reply['status'] == 'ok') {
      log.info "Logged in ok"
      def username = reply.username
      eb.send('demo.persistor', ['action' :'findone', 'collection' :'users', 'matcher': ['username': username]],
        { lookupReplyMsg ->
          def lookupReply = lookupReplyMsg.body
          if (lookupReply['status'] == 'ok') {
            orderMsg.reply(['status': 'ok'])
            // Send an email
            sendEmail(lookupReply['result']['email'], order['items'])
          } else {
            log.warn 'Failed to persist order'
          }
        })
    } else {
      // Invalid session id
      log.warn 'invalid session id'
    }
  })
}

def stringify(order) {
  def builder = new JsonBuilder(order)
  builder.toPrettyString()
}

def sendEmail(email, items) {

  log.info "sending email to ${email}"

  def body = 'Thank you for your order\n\nYou bought:\n\n'
  def totPrice = 0.0
  for (i in 0 ..< items.length) {
    def quant = items[i]['quantity']
    def album = items[i]['album']
    def linePrice = quant * album.price
    totPrice += linePrice
    body += "$quant of ${album.title} at ${album.price} Line Total: £${linePrice}\n"
  }
  body += "\n Total: £${totPrice}"

  def msg = [
    'from': 'vToons@localhost',
    'to': email,
    'subject': 'Thank you for your order',
    'body': body
  ]

  eb.send('demo.mailer', msg)

  log.info "sent email: ${body}"
}

address = "demo.orderMgr"
eb.registerHandler(address, handler)