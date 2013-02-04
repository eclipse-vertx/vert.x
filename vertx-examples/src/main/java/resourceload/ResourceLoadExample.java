package resourceload;

/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.vertx.java.platform.Verticle;

import java.net.URL;

public class ResourceLoadExample extends Verticle {

  public void start() {
    URL urlBar = getClass().getClassLoader().getResource("bar.txt");
    System.out.println("bar url is: " + urlBar);
    URL urlFoo = getClass().getClassLoader().getResource("foo.xml");
    System.out.println("foo url is: " + urlFoo);

    Quux quux = new Quux();
    System.out.println("wibble is " + quux.wibble());
  }
}
