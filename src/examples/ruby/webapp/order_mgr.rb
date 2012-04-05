require('vertx')
require('json')

@eb = Vertx::EventBus
address = "demo.orderMgr"

@eb.register_handler(address) do |message|
  order = message.body
  puts "Received order in order manager #{JSON.generate(order)}"
  sessionID = order['sessionID']
  @eb.send('demo.authMgr.validate', { 'sessionID' => sessionID }) do |reply|
    if reply.body['status'] == 'ok'
      username = reply.body['username']
      @eb.send('demo.persistor',
              {'action' => 'findone', 'collection' => 'users',
              'matcher'=> {'username' => username}}) do |reply|                                         
          if reply.body['status'] == 'ok'
            message.reply({'status' => 'ok'})
            
            # Send an email            
            send_email(reply.body['result']['email'], order['items'])
          else
            puts 'Failed to persist order'
          end
        end
    else
      # Invalid session id
      puts 'invalid session id'
    end
  end
end

def send_email(email, items)

  puts "sending to email #{email}"

  body = "Thank you for your order\n\nYou bought:\n\n"
  tot_price = 0.0
  for i in 0..items.length - 1
    quant = items[i]['quantity']
    album = items[i]['album']
    line_price = quant * album['price']
    tot_price += line_price
    body << "#{quant} of #{album['title']} at $#{album['price']} Line Total: $#{line_price}\n"
  end
  body << "\nTotal: $#{tot_price}"

  msg = {
    'from' => 'vToons@localhost',
    'to' => email,
    'subject' => 'Thank you for your order',
    'body' => body
  }
  
  puts "sending email: #{body}"

  @eb.send('demo.mailer', msg)
end
