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

import org.vertx.groovy.framework.TestUtils
import org.vertx.groovy.core.buffer.Buffer
import org.vertx.groovy.core.http.HttpClient
import org.vertx.groovy.core.http.HttpServer

tu = new TestUtils()
tu.checkContext()

server = new HttpServer()
client = new HttpClient().setPort(8080)

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
    server.setSSL(true)
    server.setKeyStorePath("./src/tests/keystores/server-keystore.jks")
    server.setKeyStorePassword("wibble")
    server.setTrustStorePath("./src/tests/keystores/server-truststore.jks")
    server.setTrustStorePassword("wibble")
    server.setClientAuthRequired(true)
  }

  path = "/someurl/blah.html"
  query = "param1=vparam1&param2=vparam2"
  uri = "http://localhost:8080" + path + "?" + query;

  server.requestHandler { req ->
    tu.checkContext()
    tu.azzert(req.uri.equals(uri))
    tu.azzert(req.method.equals(method))
    tu.azzert(req.path.equals(path))
    tu.azzert(req.query.equals(query))
    tu.azzert(req.getHeader("header1").equals("vheader1"))
    tu.azzert(req.getHeader("header2").equals("vheader2"))
    tu.azzert(req.getAllParams().get("param1").equals("vparam1"))
    tu.azzert(req.getAllParams().get("param2").equals("vparam2"))
    req.response.putHeader('rheader1', 'vrheader1')
    req.response.putHeader('rheader2', 'vrheader2')
    body = new Buffer()
    req.dataHandler { data ->
      tu.checkContext()
      body << data
    }
    req.response.setChunked(chunked)
    req.endHandler {
      tu.checkContext()
      if (!chunked) {
        req.response.putHeader('Content-Length', body.length())
      }
      req.response << body
      if (chunked) {
        req.response.putTrailer('trailer1', 'vtrailer1')
        req.response.putTrailer('trailer2', 'vtrailer2')
      }
      req.response.end()
    }
  }
  server.listen(8080)

  if (ssl) {
    client.setSSL(true)
    client.setKeyStorePath('./src/tests/keystores/client-keystore.jks')
    client.setKeyStorePassword('wibble')
    client.setTrustStorePath('./src/tests/keystores/client-truststore.jks')
    client.setTrustStorePassword('wibble')
  }

  sentBuff = TestUtils.generateRandomBuffer(1000)

  request = client.request(method, uri, { resp ->
    tu.checkContext()
    tu.azzert(200 == resp.getStatusCode())
    tu.azzert('vrheader1'.equals(resp.getHeader('rheader1')))
    tu.azzert('vrheader2'.equals(resp.getHeader('rheader2')))
    body = new Buffer()
    resp.dataHandler { data ->
      tu.checkContext()
      body << data
    }

    resp.endHandler {
      tu.checkContext()
      tu.azzert(TestUtils.buffersEqual(sentBuff, body))
      if (chunked) {
        tu.azzert('vtrailer1'.equals(resp.getTrailer('trailer1')))
        tu.azzert('vtrailer2'.equals(resp.getTrailer('trailer2')))
      }
      tu.testComplete()
    }
  })

  request.setChunked(chunked)
  request.putHeader('header1', 'vheader1')
  request.putHeader('header2', 'vheader2')
  if (!chunked) {
    request.putHeader('Content-Length', sentBuff.length())
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

