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
[-> P: send(request)
create Source as S
P --> S: bind ephemeral address
participant Consumer as C
P -> C: SYN(Source)/MSG(request)
S <- C: FIN/MSG(response)
Destroy S
[<- S: reply(response)
```

```plantuml
title General case
hide footbox
participant Producer as P
[-> P: begin
create Source as S
P --> S: src = bind ephemeral address
participant Consumer as C
P -> C: SYN(Source)
create Destination as D
C --> D: bind ephemeral address
S <- C: ACK(Destination)
S -> D: MSG
D -> S: MSG
S -> D: MSG
S -> D: FIN
Destroy D
D -> S: MSG
D -> S: MSG
D -> S: FIN
Destroy S
```

## Todo

- Fragmentation
- Gauge interest
  - Pascal K.
  - Stephane Martin
- service-proxy usage
