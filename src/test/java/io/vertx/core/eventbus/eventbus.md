```plantuml
title Send
hide footbox
participant Producer as P
[-> P: send(message)
participant Consumer as C
P -> C: MSG(request)
```

```plantuml
title Request and response
hide footbox
participant Producer as P
[-> P: request(request_message)
create Source as S
P --> S: bind ephemeral address
participant Consumer as C
P -> C: SYN(Source)/MSG(request_message)
S <- C: FIN/MSG(reply_message)
Destroy S
[<- S: response(reply_message)
```

```plantuml
title General case
hide footbox
participant Producer as P
[-> P: stream = connect("consumer")
create Source as S
P --> S: src = bind ephemeral address
participant Consumer as C
P -> C: SYN(src)
create Destination as D
C --> D: dst = bind ephemeral address
S <- C: ACK(dst)
[-> S: stream.write(m1)
S -> D: MSG(m1)
D -> S: MSG(m2)
[<- S: handler.handle(m2)
[-> S: stream.write(m3)
S -> D: MSG(m3)
[-> S: stream.end()
S -> D: FIN
Destroy D
[<- S: handler.handle(m4)
D -> S: MSG(m4)
[<- S: handler.handle(m5)
D -> S: MSG(m5)
D -> S: FIN
Destroy S
[<- S: endHandler.handle(null)
```

## Todo

- Fragmentation
- Gauge interest
  - Pascal K.
  - Stephane Martin
- service-proxy usage
