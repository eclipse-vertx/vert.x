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

package core.http

import org.vertx.groovy.core.buffer.Buffer
import org.vertx.groovy.testframework.TestUtils

tu = new TestUtils(vertx)
tu.checkContext()

server = vertx.createHttpServer()
client = vertx.createHttpClient().setPort(8080)

def testGET() {
  httpMethod(false, "GET", false)
}

def testGetSSL() {
  httpMethod(true, "GET", false)
}

def testPUT() {
  httpMethod(false, "PUT", false)
}

def testPUTSSL() {
  httpMethod(true, "PUT", false)
}

def testPOST() {
  httpMethod(false, "POST", false)
}

def testPOSTSSL() {
  httpMethod(true, "POST", false)
}

def testHEAD() {
  httpMethod(false, "HEAD", false)
}

def testHEADSSL() {
  httpMethod(true, "HEAD", false)
}

def testOPTIONS() {
  httpMethod(false, "OPTIONS", false)
}

def testOPTIONSSSL() {
  httpMethod(true, "OPTIONS", false)
}
def testDELETE() {
  httpMethod(false, "DELETE", false)
}

def testDELETESSL() {
  httpMethod(true, "DELETE", false)
}

def testTRACE() {
  httpMethod(false, "TRACE", false)
}

def testTRACESSL() {
  httpMethod(true, "TRACE", false)
}

def testCONNECT() {
  httpMethod(false, "CONNECT", false)
}

def testCONNECTSSL() {
  httpMethod(true, "CONNECT", false)
}

def testPATCH() {
  httpMethod(false, "PATCH", false)
}

def testPATCHSSL() {
  httpMethod(true, "PATCH", false)
}




def testGETChunked() {
  httpMethod(false, "GET", true)
}

def testGetSSLChunked() {
  httpMethod(true, "GET", true)
}

def testPUTChunked() {
  httpMethod(false, "PUT", true)
}

def testPUTSSLChunked() {
  httpMethod(true, "PUT", true)
}

def testPOSTChunked() {
  httpMethod(false, "POST", true)
}

def testPOSTSSLChunked() {
  httpMethod(true, "POST", true)
}

def testHEADChunked() {
  httpMethod(false, "HEAD", true)
}

def testHEADSSLChunked() {
  httpMethod(true, "HEAD", true)
}

def testOPTIONSChunked() {
  httpMethod(false, "OPTIONS", true)
}

def testOPTIONSSSLChunked() {
  httpMethod(true, "OPTIONS", true)
}

def testDELETEChunked() {
  httpMethod(false, "DELETE", true)
}

def testDELETESSLChunked() {
  httpMethod(true, "DELETE", true)
}

def testTRACEChunked() {
  httpMethod(false, "TRACE", true)
}

def testTRACESSLChunked() {
  httpMethod(true, "TRACE", true)
}

def testCONNECTChunked() {
  httpMethod(false, "CONNECT", true)
}

def testCONNECTSSLChunked() {
  httpMethod(true, "CONNECT", true)
}

def testPATCHChunked() {
  httpMethod(false, "PATCH", true)
}

def testPATCHSSLChunked() {
  httpMethod(true, "PATCH", true)
}


def httpMethod(ssl, method, chunked)  {

  if (ssl) {
    server.SSL = true
    server.keyStorePath = "./src/test/keystores/server-keystore.jks"
    server.keyStorePassword = "wibble"
    server.trustStorePath = "./src/test/keystores/server-truststore.jks"
    server.trustStorePassword = "wibble"
    server.clientAuthRequired = true
  }

  path = "/someurl/blah.html"
  query = "param1=vparam1&param2=vparam2"
  uri = "http://localhost:8080" + path + "?" + query

  server.requestHandler { req ->
    tu.checkContext()
    tu.azzert(req.uri.equals(uri))
    tu.azzert(req.method == method)
    tu.azzert(req.path == path)
    tu.azzert(req.query == query)
    tu.azzert(req.headers["header1"] == "vheader1")
    tu.azzert(req.headers["header2"] == "vheader2")
    tu.azzert(req.params["param1"] == "vparam1")
    tu.azzert(req.params["param2"] == "vparam2")
    req.response.headers['rheader1'] = 'vrheader1'
    req.response.headers['rheader2'] = 'vrheader2'
    body = new Buffer()
    req.dataHandler { data ->
      tu.checkContext()
      body << data
    }
    req.response.setChunked(chunked)
    req.endHandler {
      tu.checkContext()
      if (method != 'HEAD' && method != 'CONNECT') {
        if (!chunked) {
          req.response.headers['content-length'] = body.length
        }
        req.response << body
      }
      if (chunked) {
        req.response.trailers['trailer1'] = 'vtrailer1'
        req.response.trailers['trailer2'] = 'vtrailer2'
      }
      req.response.end()
    }
  }
  server.listen(8080)

  if (ssl) {
    client.SSL = true
    client.keyStorePath = "./src/test/keystores/client-keystore.jks"
    client.keyStorePassword = "wibble"
    client.trustStorePath = "./src/test/keystores/client-truststore.jks"
    client.trustStorePassword = "wibble"
  }

  sentBuff = TestUtils.generateRandomBuffer(1000)

  request = client.request(method, uri, { resp ->
    tu.checkContext()
    tu.azzert(200 == resp.statusCode)
    tu.azzert(resp.headers['rheader1'] == 'vrheader1')
    tu.azzert(resp.headers['rheader2'] == 'vrheader2')
    body = new Buffer()
    resp.dataHandler { data ->
      tu.checkContext()
      body << data
    }

    resp.endHandler {
      tu.checkContext()
      if (method != 'HEAD' && method != 'CONNECT') {
        tu.azzert(TestUtils.buffersEqual(sentBuff, body))
        if (chunked) {
          tu.azzert(resp.trailers['trailer1'] == 'vtrailer1')
          tu.azzert(resp.trailers['trailer2'] == 'vtrailer2')
        }
      }
      tu.testComplete()
    }
  })

  request.chunked = true
  request.headers['header1'] = 'vheader1'
  request.headers['header2'] = 'vheader2'
  if (!chunked) {
    request.headers['content-length'] = sentBuff.length
  }

  request << sentBuff

  request.end()
}

tu.registerTests(this)
tu.appReady()

void vertxStop() {
  client.close()
  server.close {
    tu.unregisterAll()
    tu.appStopped()
  }

}

